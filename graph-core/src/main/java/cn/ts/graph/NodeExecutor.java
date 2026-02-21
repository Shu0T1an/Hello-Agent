package cn.ts.graph;

import cn.ts.graph.checkpoint.InterruptionMetadata;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.flux.GraphFlux;
import cn.ts.graph.flux.GraphFlux;
import cn.ts.graph.node.AsyncNodeAction;
import cn.ts.graph.node.AsyncNodeActionWithConfig;
import cn.ts.graph.node.InterruptableAction;
import cn.ts.graph.node.Node;
import cn.ts.graph.node.NodeAction;
import cn.ts.graph.node.NodeActionAsyncUtils;
import cn.ts.graph.node.NodeActionWithConfig;
import cn.ts.graph.record.DefaultExecutionRecordManager;
import cn.ts.graph.record.ExecutionRecord;
import cn.ts.graph.record.ExecutionRecordManager;
import cn.ts.graph.record.ExecutionRecords;
import cn.ts.graph.record.InputMessage;
import cn.ts.graph.record.TokenUsage;
import cn.ts.graph.record.ToolCallInfo;
import cn.ts.graph.record.ToolExecutionRecord;
import cn.ts.graph.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
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
 * 支持中断检测（InterruptableAction）
 * 参考 Spring AI Alibaba 的 NodeExecutor 设计
 * </p>
 *
 * @author tianshuo
 */
public class NodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(NodeExecutor.class);
    private static final String THINK = "think";
    private static final String REASONING_CONTENT = "reasoningContent";
    private static final String REASONING_CONTENT_SNAKE = "reasoning_content";

    private final ExecutionRecordManager recordManager;

    /**
     * 创建默认的节点执行器
     */
    public NodeExecutor() {
        this.recordManager = new DefaultExecutionRecordManager();
    }

    /**
     * 创建带有自定义记录管理器的节点执行器
     */
    public NodeExecutor(ExecutionRecordManager recordManager) {
        this.recordManager = recordManager != null ? recordManager : new DefaultExecutionRecordManager();
    }

    /**
     * 获取执行记录管理器
     */
    public ExecutionRecordManager getRecordManager() {
        return recordManager;
    }

    /**
     * 执行节点并返回响应式流
     *
     * @param node    要执行的节点
     * @param context 执行上下文
     * @return 响应式流，发射节点执行结果
     *         - 普通节点：GraphResponse<NodeOutput>（状态更新）
     *         - 流式节点：GraphResponse<NodeOutput>（单个流元素）
     *         - 中断节点：GraphResponse<NodeOutput>（执行被中断）
     */
    public Flux<GraphResponse<NodeOutput>> execute(Node node, GraphRunnerContext context) {
        // 1. 检查是否是可中断节点
        if (node.isInterruptable() && node.interruptableAction() != null) {
            InterruptableAction interruptable = node.interruptableAction();
            Optional<InterruptionMetadata> interruption = interruptable.interrupt(
                    node.id(),
                    context.getOverallState(),
                    context.getConfig()
            );

            if (interruption.isPresent()) {
                logger.info("节点 {} 执行被中断", node.id());

                // 获取 threadId（用于恢复时查找检查点）
                String threadId = context.getConfig().threadId();

                // 自动创建检查点（如果配置了 CheckpointManager）
                String checkpointId = createCheckpointOnInterruption(context, node.id());

                // 构建中断输出
                InterruptionOutput interruptionOutput = InterruptionOutput.of(
                        interruption.get(),
                        checkpointId != null ? checkpointId : "",
                        threadId
                );
                Map<String, Object> interruptData = Map.of(
                        "interruption", interruptionOutput,
                        "interrupted", true
                );
                NodeOutput interruptOutput = NodeOutput.of(
                        node.id(),
                        interruptData,
                        context.getOverallState()
                );
                return Flux.just(GraphResponse.interruption(interruptOutput));
            }
        }

        // 2. 异步执行节点动作，返回 CompletableFuture
        CompletableFuture<Map<String, Object>> future = executeNodeAsync(node, context);

        // 3. 将 CompletableFuture 转换为 Mono，然后扁平化为 Flux
        return Mono.fromFuture(future)
                .flatMapMany(updates -> handleActionResult(node, context, updates))
                .onErrorResume(error -> Flux.just(GraphResponse.error(error)));
    }

    /**
     * 异步执行节点动作
     * <p>
     * 支持多种节点动作接口，优先使用带 config 的新接口：
     * 1. AsyncNodeActionWithConfig（新接口，带 config）
     * 2. AsyncNodeAction（旧接口，包装）
     * 3. NodeAction（旧接口，包装）
     * </p>
     *
     * @param node    节点
     * @param context 上下文
     * @return CompletableFuture 包含状态更新
     */
    private CompletableFuture<Map<String, Object>> executeNodeAsync(Node node, GraphRunnerContext context) {
        NodeAction action = node.action();
        RunnableConfig config = context.getConfig();

        // 优先检查：是否实现了 AsyncNodeActionWithConfig（新接口，带 config）
        if (action instanceof AsyncNodeActionWithConfig actionWithConfig) {
            return actionWithConfig.applyAsync(context.getOverallState(), config);
        }

        // 检查是否是 AsyncNodeAction（旧接口，包装）
        if (action instanceof AsyncNodeAction asyncAction) {
            AsyncNodeActionWithConfig wrapped = AsyncNodeActionWithConfig.from(asyncAction);
            return wrapped.applyAsync(context.getOverallState(), config);
        }

        // 普通 NodeAction，包装成 NodeActionWithConfig
        NodeActionWithConfig wrapped = NodeActionWithConfig.from(action);
        return wrapped.applyAsync(context.getOverallState(), config);
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

        // 检查是否有执行信息（由节点提供的 __execution_info__）
        Optional<Map<String, Object>> execInfo = extractExecutionInfo(updates);

        // 移除 __execution_info__ 不合并到状态中
        if (execInfo.isPresent()) {
            updates.remove("__execution_info__");
        }

        // 普通结果 - 合并状态
        context.mergeIntoCurrentState(updates);

        String nodeId = context.getCurrentNodeId();

        // 记录开始时间
        Instant startTime = Instant.now();

        // 创建 STARTING 和 COMPLETED 响应
        GraphResponse<NodeOutput> startingResponse = GraphResponse.of(nodeId, NodeOutput.starting(nodeId, node));
        GraphResponse<NodeOutput> completedResponse = GraphResponse.of(nodeId, NodeOutput.completed(nodeId, node, updates, context.getOverallState(), startTime));

        // 如果有执行信息，创建并保存执行记录
        if (execInfo.isPresent()) {
            ExecutionRecord record = createRecordFromInfo(nodeId, execInfo.get(), context);
            recordManager.saveRecord(record, context.getOverallState());
        }

        // 返回 STARTING → COMPLETED 流，并在完成后创建检查点
        return Flux.just(completedResponse)
                .startWith(startingResponse)
                .doOnComplete(() -> createCheckpointAfterNode(context, nodeId));
    }

    /**
     * 从状态更新中提取执行信息
     *
     * @param updates 状态更新
     * @return 执行信息的 Optional
     */
    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> extractExecutionInfo(Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return Optional.empty();
        }
        Object execInfo = updates.get("__execution_info__");
        if (execInfo instanceof Map<?, ?> map) {
            return Optional.of((Map<String, Object>) map);
        }
        return Optional.empty();
    }

    /**
     * 从执行信息创建执行记录
     *
     * @param nodeId    节点ID
     * @param execInfo  执行信息
     * @param context   执行上下文
     * @return 执行记录
     */
    @SuppressWarnings("unchecked")
    private ExecutionRecord createRecordFromInfo(String nodeId, Map<String, Object> execInfo, GraphRunnerContext context) {
        String nodeType = (String) execInfo.get("nodeType");
        Instant startTime = Instant.parse((String) execInfo.get("startTime"));

        if ("tool".equals(nodeType)) {
            // Tool 节点执行记录
            List<Map<String, Object>> executions = (List<Map<String, Object>>) execInfo.getOrDefault("executions", List.of());
            List<ToolExecutionRecord.ToolExecution> toolExecutions = new ArrayList<>();
            for (Map<String, Object> execMap : executions) {
                toolExecutions.add(ToolExecutionRecord.ToolExecution.fromMap(execMap));
            }
            return ExecutionRecords.toolSuccess(nodeId, startTime, Instant.now(), toolExecutions);
        }

        // 默认返回通用成功记录
        return ExecutionRecords.success(
            ExecutionRecord.NodeType.fromValue(nodeType),
            nodeId,
            startTime,
            Instant.now()
        );
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
                // 流完成时聚合并更新 state，然后创建检查点
                .doOnComplete(() -> {
                    if (!responsesQueue.isEmpty()) {
                        // 1. 聚合 messages
                        List<Message> messages = aggregateChatResponses(context, new ArrayList<>(responsesQueue));
                        context.mergeIntoCurrentState(Map.of("messages", messages));

                        // 2. 生成并保存 LLM 执行记录（使用新的记录管理器）
                        ExecutionRecord record = buildLLMExecutionRecord(
                                nodeName,
                                context,
                                responsesQueue,
                                fullContentBuilder.toString(),
                                startTime
                        );
                        recordManager.saveRecord(record, context.getOverallState());
                    }
                    // 节点完成后创建检查点
                    createCheckpointAfterNode(context, nodeName);
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
        StringBuilder fullThinking = new StringBuilder();
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
                extractReasoningFromMetadata(output.getMetadata()).ifPresent(fullThinking::append);
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
                if (!fullThinking.isEmpty()) {
                    messageMetadata.put(THINK, fullThinking.toString());
                    messageMetadata.put(REASONING_CONTENT, fullThinking.toString());
                    messageMetadata.put(REASONING_CONTENT_SNAKE, fullThinking.toString());
                }
                // 将 toolCalls 信息也加入 metadata
                assistantMessage = AssistantMessage.builder()
                        .content(fullContent.toString())
                        .properties(messageMetadata)
                        .toolCalls(toolCalls)
                        .build();
            } else {
                // 只有 content 的构造函数
                if (!fullThinking.isEmpty()) {
                    messageMetadata.put(THINK, fullThinking.toString());
                    messageMetadata.put(REASONING_CONTENT, fullThinking.toString());
                    messageMetadata.put(REASONING_CONTENT_SNAKE, fullThinking.toString());
                }
                assistantMessage = AssistantMessage.builder()
                        .content(fullContent.toString())
                        .properties(messageMetadata)
                        .toolCalls(toolCalls)
                        .build();            }

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
     * 在流完成时调用，聚合完整的执行信息，返回 ExecutionRecord 对象
     * </p>
     *
     * @param nodeName       节点名称
     * @param context        执行上下文
     * @param responsesQueue ChatResponse 队列
     * @param fullContent    聚合后的完整输出
     * @param startTime      开始时间
     * @return 执行记录
     */
    private ExecutionRecord buildLLMExecutionRecord(
            String nodeName,
            GraphRunnerContext context,
            ConcurrentLinkedQueue<ChatResponse> responsesQueue,
            String fullContent,
            Instant startTime) {

        // 提取输入 messages（执行前的 messages）
        List<InputMessage> inputMessages = extractInputMessages(context);

        // 提取 toolCalls
        List<ToolCallInfo> toolCalls = extractToolCallsFromResponses(responsesQueue);

        // 提取 usage
        TokenUsage usage = aggregateUsage(responsesQueue);

        return ExecutionRecords.llmSuccess(
                nodeName,
                startTime,
                Instant.now(),
                inputMessages,
                fullContent,
                toolCalls,
                usage
        );
    }

    /**
     * 从上下文中提取输入 messages
     * <p>
     * 将 Message 对象简化为 InputMessage 格式
     * </p>
     *
     * @param context 执行上下文
     * @return 简化的 messages 列表
     */
    private List<InputMessage> extractInputMessages(GraphRunnerContext context) {
        List<InputMessage> result = new ArrayList<>();
        List<Message> messages = context.getOverallState()
                .<List<Message>>value("messages")
                .orElse(new ArrayList<>());

        for (Message message : messages) {
            String role = message.getMessageType().getValue();
            String content = null;

            // 根据消息类型获取内容
            if (message instanceof AssistantMessage am) {
                content = am.getText();
            } else if (message instanceof org.springframework.ai.chat.messages.UserMessage um) {
                content = um.getText();
            } else if (message instanceof org.springframework.ai.chat.messages.SystemMessage sm) {
                content = sm.getText();
            } else if (message instanceof org.springframework.ai.chat.messages.ToolResponseMessage tm) {
                content = tm.getResponses().toString();
            }

            if (content != null) {
                result.add(new InputMessage(role, content));
            }
        }

        return result;
    }

    /**
     * 从响应列表中提取 toolCalls
     *
     * @param responses ChatResponse 队列
     * @return toolCalls 列表
     */
    private List<ToolCallInfo> extractToolCallsFromResponses(ConcurrentLinkedQueue<ChatResponse> responses) {
        List<ToolCallInfo> toolCalls = new ArrayList<>();
        for (ChatResponse response : responses) {
            var output = response.getResult() != null ? response.getResult().getOutput() : null;
            if (output != null && output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                for (AssistantMessage.ToolCall tc : output.getToolCalls()) {
                    toolCalls.add(new ToolCallInfo(tc.id(), tc.name(), tc.arguments()));
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
    private TokenUsage aggregateUsage(ConcurrentLinkedQueue<ChatResponse> responses) {
        long promptTokens = 0;
        long completionTokens = 0;
        long totalTokens = 0;

        for (ChatResponse response : responses) {
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                if (usage != null && usage.getTotalTokens() > 0) {
                    promptTokens = usage.getPromptTokens();
                    completionTokens = usage.getCompletionTokens();
                    totalTokens = usage.getTotalTokens();
                }
            }
        }

        return new TokenUsage(promptTokens, completionTokens, totalTokens);
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

    /**
     * 在中断时自动创建检查点
     *
     * @param context 执行上下文
     * @param nodeId  节点ID
     * @return 检查点ID，如果没有配置 CheckpointManager 则返回 null
     */
    private String createCheckpointOnInterruption(GraphRunnerContext context, String nodeId) {
        return context.getCheckpointManager()
                .map(manager -> {
                    try {
                        String checkpointId = manager.createCheckpoint(context, "interruption");
                        logger.info("中断时自动创建检查点: nodeId={}, checkpointId={}", nodeId, checkpointId);
                        return checkpointId;
                    } catch (Exception e) {
                        logger.warn("创建中断检查点失败: nodeId={}, error={}", nodeId, e.getMessage());
                        return null;
                    }
                })
                .orElse(null);
    }

    /**
     * 节点完成后自动创建检查点
     * <p>
     * 根据 CheckpointConfig 的策略决定是否创建检查点
     * </p>
     *
     * @param context 执行上下文
     * @param nodeId  节点ID
     */
    private void createCheckpointAfterNode(GraphRunnerContext context, String nodeId) {
        context.getCheckpointManager().ifPresent(manager -> {
            if (manager.shouldCheckpoint(nodeId)) {
                try {
                    String checkpointId = manager.createCheckpoint(context, "auto");
                    logger.debug("节点 {} 完成后自动创建检查点: {}", nodeId, checkpointId);
                } catch (Exception e) {
                    logger.warn("节点完成后创建检查点失败: nodeId={}, error={}", nodeId, e.getMessage());
                }
            }
        });
    }

    /**
     * 创建默认的节点执行器
     *
     * @return NodeExecutor 实例
     */
    public static NodeExecutor create() {
        return new NodeExecutor();
    }

    /**
     * 创建带有自定义记录管理器的节点执行器
     *
     * @param recordManager 执行记录管理器
     * @return NodeExecutor 实例
     */
    public static NodeExecutor create(ExecutionRecordManager recordManager) {
        return new NodeExecutor(recordManager);
    }
}
