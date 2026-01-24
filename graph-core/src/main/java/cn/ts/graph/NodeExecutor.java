package cn.ts.graph;

import cn.ts.graph.flux.GraphFlux;
import cn.ts.graph.node.AsyncNodeAction;
import cn.ts.graph.node.Node;
import cn.ts.graph.node.NodeActionAsyncUtils;
import cn.ts.graph.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;

/**
 * 节点执行器
 * <p>
 * 负责执行单个节点并将结果转换为响应式流
 * 支持 CompletableFuture 到 Flux 的转换
 * 参考 Spring AI Alibaba 的 NodeExecutor 设计
 * </p>
 *
 * @author tianshuo
 */
public class NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(NodeExecutor.class);

    /**
     * 执行节点并返回响应式流
     *
     * @param node    要执行的节点
     * @param context 执行上下文
     * @return 响应式流，发射节点执行结果
     *         - 普通节点：GraphResponse<NodeOutput>（状态更新）
     *         - 流式节点：GraphResponse<NodeOutput>（单个流元素）
     */
    public Flux<GraphResponse<NodeOutput>> execute(Node node, GraphRunnerContext context) {
        CompletableFuture<Map<String, Object>> future = executeNodeAsync(node, context);

        return Mono.fromFuture(future)
                .flatMapMany(updates -> handleActionResult(node, context, updates))
                .onErrorResume(error -> Flux.just(GraphResponse.error(error)));
    }

    /**
     * 异步执行节点动作
     *
     * @param node    节点
     * @param context 上下文
     * @return CompletableFuture 包含状态更新
     */
    private CompletableFuture<Map<String, Object>> executeNodeAsync(Node node, GraphRunnerContext context) {
        if (node.action() instanceof AsyncNodeAction asyncAction) {
            return asyncAction.applyAsync(context.getOverallState());
        } else {
            return NodeActionAsyncUtils.async(node.action(), ForkJoinPool.commonPool())
                    .applyAsync(context.getOverallState());
        }
    }

    /**
     * 处理节点执行结果
     *
     * @param node    节点
     * @param context 上下文
     * @param updates 状态更新
     * @return 响应式流
     */
    private Flux<GraphResponse<NodeOutput>> handleActionResult(
            Node node, GraphRunnerContext context, Map<String, Object> updates) {

        // 检测 GraphFlux
        Optional<GraphFlux<?>> flux = extractGraphFlux(updates);
        if (flux.isPresent()) {
            return handleGraphFlux(node, context, flux.get(), updates);
        }

        // 普通结果 - 合并状态
        context.mergeIntoCurrentState(updates);

        String nodeId = context.getCurrentNodeId();

        // 记录开始时间
        java.time.Instant startTime = java.time.Instant.now();

        // 创建 STARTING 和 COMPLETED 响应
        GraphResponse<NodeOutput> startingResponse = GraphResponse.of(nodeId, NodeOutput.starting(nodeId, node));
        GraphResponse<NodeOutput> completedResponse = GraphResponse.of(nodeId, NodeOutput.completed(nodeId, node, updates, context.getOverallState(), startTime));

        // 返回 STARTING → COMPLETED 流
        return Flux.just(completedResponse).startWith(startingResponse);
    }

    /**
     * 从状态更新中提取 GraphFlux
     *
     * @param updates 状态更新
     * @return GraphFlux 的 Optional
     */
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

    /**
     * 处理流式结果
     * <p>
     * 检测流元素类型，自动包装为 StreamingOutput
     * 在流结束时自动聚合 ChatResponse 并更新到 state
     * </p>
     *
     * @param node           节点
     * @param context        上下文
     * @param graphFlux      流式结果
     * @param originalUpdates 原始状态更新
     * @return 响应式流
     */
    @SuppressWarnings("unchecked")
    private Flux<GraphResponse<NodeOutput>> handleGraphFlux(
            Node node,
            GraphRunnerContext context,
            GraphFlux<?> graphFlux,
            Map<String, Object> originalUpdates) {

        String nodeName = context.getCurrentNodeId();

        // 创建不含 GraphFlux 的状态更新
        Map<String, Object> filteredUpdates = new HashMap<>();
        if (originalUpdates != null) {
            for (Map.Entry<String, Object> entry : originalUpdates.entrySet()) {
                if (!(entry.getValue() instanceof GraphFlux<?>)) {
                    filteredUpdates.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // 先合并非流式状态
        if (!filteredUpdates.isEmpty()) {
            context.mergeIntoCurrentState(filteredUpdates);
        }

        Flux<Object> stream = (Flux<Object>) graphFlux.getStream();

        // 直接使用 handleChatResponseStream 处理所有流，方法内部会进行类型过滤
        return handleChatResponseStream(nodeName, node, context, stream);
    }

    /**
     * 处理流式输出
     * <p>
     * 同时支持实时流式输出和流完成时的消息聚合。
     * 使用 ConcurrentLinkedQueue 保证线程安全的响应收集。
     * 对于 ChatResponse 类型的元素进行聚合，其他类型直接流式输出。
     * 流状态：STARTING → RUNNING → RUNNING → ... → COMPLETED
     * </p>
     */
    private Flux<GraphResponse<NodeOutput>> handleChatResponseStream(
            String nodeName,
            Node node,
            GraphRunnerContext context,
            Flux<Object> stream) {


        // 使用 ConcurrentLinkedQueue 保存响应列表，保证线程安全
        ConcurrentLinkedQueue<ChatResponse> responsesQueue = new ConcurrentLinkedQueue<>();

        // 用于保存聚合后的完整内容（从流式 chunk 累积）
        StringBuilder fullContentBuilder = new StringBuilder();

        // 记录开始时间
        java.time.Instant startTime = java.time.Instant.now();

        // 创建 STARTING 响应
        GraphResponse<NodeOutput> startingResponse = GraphResponse.of(nodeName, StreamingOutput.ofStarting(nodeName, node));

        return stream
                // 收集 ChatResponse 响应用于聚合（副作用）
                .doOnNext(chunk -> {
                    if (chunk instanceof ChatResponse response) {
                        responsesQueue.add(response);
                    }
                })
                // 实时流式输出，包装为 RUNNING 状态，同时累积文本内容
                .map(chunk -> {
                    GraphResponse<NodeOutput> response = wrapToNodeOutputWithStatus(nodeName, node, chunk, context, startTime);
                    // 累积流式输出的文本内容
                    if (response.getData() instanceof StreamingOutput<?> streamingOutput) {
                        String chunkText = streamingOutput.getChunk();
                        if (chunkText != null && !chunkText.isEmpty()) {
                            fullContentBuilder.append(chunkText);
                        }
                    }
                    return response;
                })
                // 流完成时聚合并更新 state
                .doOnComplete(() -> {
                    if (!responsesQueue.isEmpty()) {
                        // 1. 聚合 messages
                        List<Message> messages = aggregateChatResponses(context, new ArrayList<>(responsesQueue));

                        // 2. 生成 LLM execution_record
                        Map<String, Object> executionRecord = buildLLMExecutionRecord(
                                context,
                                responsesQueue,
                                fullContentBuilder.toString(),
                                startTime
                        );
                        context.mergeIntoCurrentState(Map.of("messages", messages));
                        context.mergeIntoCurrentState(Map.of("execution_record", executionRecord));
                    }
                })
                // 添加 COMPLETED 响应（包含完整内容）
                // 使用 defer 确保在所有数据流完成后才评估 fullContentBuilder
                .concatWith(Flux.defer(() ->
                        Flux.just(GraphResponse.streamCompleteWithData(nodeName,
                                StreamingOutput.ofCompletedWithContent(nodeName, node, context.getOverallState(), startTime, fullContentBuilder.toString())))

                ))
                // 在流开始前插入 STARTING 响应
                .startWith(startingResponse)
                .onErrorResume(error -> {
                    // 流异常时清理资源
                    responsesQueue.clear();
                    return Flux.just(GraphResponse.error(error));
                });
    }

    /**
     * 聚合多个 ChatResponse 为消息列表
     * <p>
     * 从 context 获取原始消息列表，追加聚合后的完整消息
     * 修复: 保留 toolCalls 信息以支持 ReAct Agent 流式输出
     * </p>
     */
    private List<Message> aggregateChatResponses(GraphRunnerContext context, List<ChatResponse> responses) {
        // 获取原始消息列表
//        List<Message> messages = context.getOverallState()
//                .<List<Message>>value("messages")
//                .orElse(new ArrayList<>());

        // 创建新的消息列表副本
        List<Message> result = new ArrayList<>();

        // 累积所有文本内容和 toolCalls
        StringBuilder fullContent = new StringBuilder();
        ChatResponse lastResponse = null;
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        Map<String, Object> messageMetadata = new HashMap<>();

//        如果是tool 的话会生成两条信息，一个是toolcall 一个是token
        for (ChatResponse response : responses) {

            if(response.getResult()!=null){
                lastResponse = response;
            }
            var output = response.getResult() != null ? response.getResult().getOutput() : null;

            if (output != null) {
                // 累加文本内容
                if (output.getText() != null) {
                    fullContent.append(output.getText());
                }
                // 收集 toolCalls（通常在最后一个响应中）
                if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                    toolCalls = output.getToolCalls();
                }
            }
        }



        // 创建完整的 AssistantMessage
        if (lastResponse != null && lastResponse.getResult() != null) {
            var output = lastResponse.getResult().getOutput();
            // 使用构造函数创建 AssistantMessage（Spring AI 1.0.0 不支持 builder）
            // 构造函数：AssistantMessage(String content, Map<String, Object> metadata)
            AssistantMessage assistantMessage;
            output.getMetadata();
            if (!output.getMetadata().isEmpty()) {
                // 带 metadata 的构造函数
                messageMetadata = new HashMap<>(output.getMetadata());
                // 将 toolCalls 信息也加入 metadata
                assistantMessage = new AssistantMessage(fullContent.toString(), messageMetadata,toolCalls);
            } else {
                // 只有 content 的构造函数
                assistantMessage = new AssistantMessage(fullContent.toString(), messageMetadata, toolCalls);
            }

            result.add(assistantMessage);
        }

        return result;
    }

    /**
     * 将流元素包装为 NodeOutput
     * <p>
     * 检测元素类型：
     * - ChatResponse → StreamingOutput&lt;ChatResponse&gt;
     * - String → StreamingOutput&lt;String&gt;
     * - 其他 → NodeOutput
     * </p>
     *
     * @param nodeName 节点名称
     * @param node     节点对象
     * @param chunk    流元素
     * @param context  上下文
     * @return GraphResponse
     */
    private GraphResponse<NodeOutput> wrapToNodeOutput(
            String nodeName,
            Node node,
            Object chunk,
            GraphRunnerContext context) {

        NodeOutput output;

        // 检测 ChatResponse 类型
        if (chunk instanceof ChatResponse chatResponse) {
            output = StreamingOutput.ofChatResponse(nodeName, node, chatResponse, context.getOverallState());
        }
        // 检测 String 类型
        else if (chunk instanceof String string) {
            output = StreamingOutput.ofChunk(nodeName, node, string, context.getOverallState());
        }
        // 其他类型使用普通 NodeOutput
        else {
            output = NodeOutput.of(nodeName, node, chunk, context.getOverallState());
        }

        return GraphResponse.stream(nodeName, output);
    }

    /**
     * 将流元素包装为带 RUNNING 状态的 NodeOutput
     * <p>
     * 用于流式输出的中间帧，标记状态为 RUNNING
     * </p>
     *
     * @param nodeName  节点名称
     * @param node      节点对象
     * @param chunk     流元素
     * @param context   上下文
     * @param startTime 开始时间
     * @return GraphResponse
     */
    private GraphResponse<NodeOutput> wrapToNodeOutputWithStatus(
            String nodeName,
            Node node,
            Object chunk,
            GraphRunnerContext context,
            java.time.Instant startTime) {

        NodeOutput output;

        // 检测 ChatResponse 类型
        if (chunk instanceof ChatResponse chatResponse) {
            output = StreamingOutput.ofRunningChatResponse(nodeName, node, chatResponse, context.getOverallState(), startTime);
        }
        // 检测 String 类型
        else if (chunk instanceof String string) {
            output = StreamingOutput.ofRunningChunk(nodeName, node, string, context.getOverallState(), startTime);
        }
        // 其他类型使用普通 NodeOutput（无状态）
        else {
            output = NodeOutput.of(nodeName, node, chunk, context.getOverallState());
        }

        return GraphResponse.stream(nodeName, output);
    }

    /**
     * 构建 LLM 节点的执行记录
     * <p>
     * 在流完成时调用，聚合完整的执行信息
     * </p>
     *
     * @param context        执行上下文
     * @param responsesQueue ChatResponse 队列
     * @param fullContent    聚合后的完整输出
     * @param startTime      开始时间
     * @return 执行记录 Map
     */
    private Map<String, Object> buildLLMExecutionRecord(
            GraphRunnerContext context,
            ConcurrentLinkedQueue<ChatResponse> responsesQueue,
            String fullContent,
            java.time.Instant startTime) {

        Map<String, Object> record = new HashMap<>();
        record.put("nodeType", "llm");
        record.put("startTime", startTime.toString());
        record.put("endTime", java.time.Instant.now().toString());

        // 提取输入 messages（执行前的 messages）
        List<Map<String, Object>> input = extractInputMessages(context);
        record.put("input", input);

        // 完整输出
        record.put("output", fullContent);

        // 提取 toolCalls
        List<Map<String, Object>> toolCalls = extractToolCallsFromResponses(responsesQueue);
        record.put("toolCalls", toolCalls);

        // 提取 usage
        Map<String, Object> usage = aggregateUsage(responsesQueue);
        record.put("usage", usage);

        return record;
    }

    /**
     * 从上下文中提取输入 messages
     * <p>
     * 将 Message 对象简化为可序列化的 Map 格式
     * </p>
     *
     * @param context 执行上下文
     * @return 简化的 messages 列表
     */
    private List<Map<String, Object>> extractInputMessages(GraphRunnerContext context) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Message> messages = context.getOverallState()
                .<List<Message>>value("messages")
                .orElse(new ArrayList<>());

        for (Message message : messages) {
            Map<String, Object> msgMap = new HashMap<>();
            msgMap.put("role", message.getMessageType().getValue());

            // 根据消息类型获取内容
            String content = null;
            if (message instanceof AssistantMessage am) {
                content = am.getText();
            } else if (message instanceof org.springframework.ai.chat.messages.UserMessage um) {
                content = um.getText();
            } else if (message instanceof org.springframework.ai.chat.messages.SystemMessage sm) {
                content = sm.getText();
            } else if(message instanceof  org.springframework.ai.chat.messages.ToolResponseMessage tm){
                content = tm.getResponses().toString();
            }

            if (content != null) {
                msgMap.put("content", content);
            }

            result.add(msgMap);
        }

        return result;
    }

    /**
     * 从响应列表中提取 toolCalls
     *
     * @param responses ChatResponse 队列
     * @return toolCalls 列表
     */
    private List<Map<String, Object>> extractToolCallsFromResponses(ConcurrentLinkedQueue<ChatResponse> responses) {
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        for (ChatResponse response : responses) {
            var output = response.getResult() != null ? response.getResult().getOutput() : null;
            if (output != null && output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                for (AssistantMessage.ToolCall tc : output.getToolCalls()) {
                    Map<String, Object> tcMap = new HashMap<>();
                    tcMap.put("id", tc.id());
                    tcMap.put("name", tc.name());
                    tcMap.put("arguments", tc.arguments());
                    toolCalls.add(tcMap);
                }
            }
        }
        return toolCalls;
    }

    /**
     * 聚合所有响应的 token 使用统计
     *
     * @param responses ChatResponse 队列
     * @return usage 统计
     */
    private Map<String, Object> aggregateUsage(ConcurrentLinkedQueue<ChatResponse> responses) {
        long promptTokens = 0;
        long completionTokens = 0;
        long totalTokens = 0;

        for (ChatResponse response : responses) {
            if(response.getMetadata() != null && response.getMetadata().getUsage() != null ){
                var usage = response.getMetadata().getUsage();
                if(usage != null && usage.getTotalTokens() > 0){
                    promptTokens = usage.getPromptTokens();
                    completionTokens = usage.getCompletionTokens();
                    totalTokens = usage.getTotalTokens();
                }
            }
        }

        return Map.of(
                "promptTokens", promptTokens,
                "completionTokens", completionTokens,
                "totalTokens", totalTokens
        );
    }

    /**
     * 创建默认的节点执行器
     *
     * @return NodeExecutor 实例
     */
    public static NodeExecutor create() {
        return new NodeExecutor();
    }
}
