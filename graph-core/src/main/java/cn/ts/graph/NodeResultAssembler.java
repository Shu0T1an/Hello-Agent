package cn.ts.graph;

import cn.ts.graph.flux.GraphFlux;
import cn.ts.graph.node.Node;
import cn.ts.graph.record.ExecutionRecord;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

class NodeResultAssembler {

    private static final String THINK = "think";
    private static final String REASONING_CONTENT = "reasoningContent";
    private static final String REASONING_CONTENT_SNAKE = "reasoning_content";

    private final ExecutionRecordService executionRecordService;

    NodeResultAssembler(ExecutionRecordService executionRecordService) {
        this.executionRecordService = executionRecordService;
    }

    Flux<GraphResponse<NodeOutput>> assemble(Node node, GraphRunnerContext context, Map<String, Object> updates) {
        Optional<GraphFlux<?>> flux = extractGraphFlux(updates);
        if (flux.isPresent()) {
            return handleGraphFlux(node, context, flux.get(), updates);
        }

        Optional<Map<String, Object>> execInfo = executionRecordService.extractExecutionInfo(updates);
        if (execInfo.isPresent()) {
            updates.remove("__execution_info__");
        }

        context.mergeIntoCurrentState(updates);
        String nodeId = context.getCurrentNodeId();
        Instant startTime = Instant.now();

        GraphResponse<NodeOutput> startingResponse = GraphResponse.of(nodeId, NodeOutput.starting(nodeId, node));
        GraphResponse<NodeOutput> completedResponse = GraphResponse.of(
                nodeId,
                NodeOutput.completed(nodeId, node, updates, context.getOverallState(), startTime)
        );

        executionRecordService.saveIfPresent(nodeId, execInfo, context);

        return Flux.just(completedResponse)
                .startWith(startingResponse)
                .doOnComplete(() -> createCheckpointAfterNode(context, nodeId));
    }

    private Optional<GraphFlux<?>> extractGraphFlux(Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return Optional.empty();
        }
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            if (entry.getValue() instanceof GraphFlux<?> flux) {
                return Optional.of(flux);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Flux<GraphResponse<NodeOutput>> handleGraphFlux(
            Node node,
            GraphRunnerContext context,
            GraphFlux<?> graphFlux,
            Map<String, Object> originalUpdates) {
        String nodeName = context.getCurrentNodeId();
        Map<String, Object> filteredUpdates = new HashMap<>();
        if (originalUpdates != null) {
            for (Map.Entry<String, Object> entry : originalUpdates.entrySet()) {
                if (!(entry.getValue() instanceof GraphFlux<?>)) {
                    filteredUpdates.put(entry.getKey(), entry.getValue());
                }
            }
        }

        if (!filteredUpdates.isEmpty()) {
            context.mergeIntoCurrentState(filteredUpdates);
        }

        Flux<Object> stream = (Flux<Object>) graphFlux.getStream();
        return handleChatResponseStream(nodeName, node, context, stream);
    }

    private Flux<GraphResponse<NodeOutput>> handleChatResponseStream(
            String nodeName,
            Node node,
            GraphRunnerContext context,
            Flux<Object> stream) {

        ConcurrentLinkedQueue<ChatResponse> responsesQueue = new ConcurrentLinkedQueue<>();
        StringBuilder fullContentBuilder = new StringBuilder();
        Instant startTime = Instant.now();
        GraphResponse<NodeOutput> startingResponse = GraphResponse.of(nodeName, StreamingOutput.ofStarting(nodeName, node));

        return stream
                .doOnNext(chunk -> {
                    if (chunk instanceof ChatResponse response) {
                        responsesQueue.add(response);
                    }
                })
                .map(chunk -> {
                    GraphResponse<NodeOutput> response = wrapToNodeOutputWithStatus(nodeName, node, chunk, context, startTime);
                    if (response.getData() instanceof StreamingOutput<?> streamingOutput) {
                        String chunkText = streamingOutput.getChunk();
                        if (chunkText != null && !chunkText.isEmpty()) {
                            fullContentBuilder.append(chunkText);
                        }
                    }
                    return response;
                })
                .doOnComplete(() -> {
                    if (!responsesQueue.isEmpty()) {
                        List<Message> messages = aggregateChatResponses(new ArrayList<>(responsesQueue));
                        context.mergeIntoCurrentState(Map.of("messages", messages));
                        ExecutionRecord record = executionRecordService.buildLlmRecord(
                                nodeName,
                                context,
                                responsesQueue,
                                fullContentBuilder.toString(),
                                startTime
                        );
                        executionRecordService.saveRecord(record, context);
                    }
                    createCheckpointAfterNode(context, nodeName);
                })
                .concatWith(Flux.defer(() ->
                        Flux.just(GraphResponse.streamCompleteWithData(
                                nodeName,
                                StreamingOutput.ofCompletedWithContent(
                                        nodeName,
                                        node,
                                        context.getOverallState(),
                                        startTime,
                                        fullContentBuilder.toString()
                                )
                        ))
                ))
                .startWith(startingResponse)
                .onErrorResume(error -> {
                    responsesQueue.clear();
                    return Flux.just(GraphResponse.error(error));
                });
    }

    private List<Message> aggregateChatResponses(List<ChatResponse> responses) {
        List<Message> result = new ArrayList<>();
        StringBuilder fullContent = new StringBuilder();
        StringBuilder fullThinking = new StringBuilder();
        ChatResponse lastResponse = null;
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        Map<String, Object> messageMetadata = new HashMap<>();

        for (ChatResponse response : responses) {
            if (response.getResult() != null) {
                lastResponse = response;
            }
            var output = response.getResult() != null ? response.getResult().getOutput() : null;
            if (output == null) {
                continue;
            }
            if (output.getText() != null) {
                fullContent.append(output.getText());
            }
            extractReasoningFromMetadata(output.getMetadata()).ifPresent(fullThinking::append);
            if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                toolCalls = output.getToolCalls();
            }
        }

        if (lastResponse != null && lastResponse.getResult() != null) {
            var output = lastResponse.getResult().getOutput();
            if (!output.getMetadata().isEmpty()) {
                messageMetadata = new HashMap<>(output.getMetadata());
            }
            if (!fullThinking.isEmpty()) {
                messageMetadata.put(THINK, fullThinking.toString());
                messageMetadata.put(REASONING_CONTENT, fullThinking.toString());
                messageMetadata.put(REASONING_CONTENT_SNAKE, fullThinking.toString());
            }
            AssistantMessage assistantMessage = AssistantMessage.builder()
                    .content(fullContent.toString())
                    .properties(messageMetadata)
                    .toolCalls(toolCalls)
                    .build();
            result.add(assistantMessage);
        }

        return result;
    }

    private GraphResponse<NodeOutput> wrapToNodeOutputWithStatus(
            String nodeName,
            Node node,
            Object chunk,
            GraphRunnerContext context,
            Instant startTime) {

        NodeOutput output;
        if (chunk instanceof ChatResponse chatResponse) {
            output = StreamingOutput.ofRunningChatResponse(nodeName, node, chatResponse, context.getOverallState(), startTime);
        } else if (chunk instanceof String string) {
            output = StreamingOutput.ofRunningChunk(nodeName, node, string, context.getOverallState(), startTime);
        } else {
            output = NodeOutput.of(nodeName, node, chunk, context.getOverallState());
        }

        return GraphResponse.stream(nodeName, output);
    }

    private Optional<String> extractReasoningFromMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Optional.empty();
        }
        return normalizeText(metadata.get(THINK))
                .or(() -> normalizeText(metadata.get(REASONING_CONTENT)))
                .or(() -> normalizeText(metadata.get(REASONING_CONTENT_SNAKE)));
    }

    private Optional<String> normalizeText(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String text = value.toString();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private void createCheckpointAfterNode(GraphRunnerContext context, String nodeId) {
        context.getCheckpointManager().ifPresent(manager -> {
            if (manager.shouldCheckpoint(nodeId)) {
                try {
                    manager.createCheckpoint(context, "auto");
                } catch (Exception ignored) {
                    // Keep behavior non-fatal for checkpoint errors in stream assembly path.
                }
            }
        });
    }
}
