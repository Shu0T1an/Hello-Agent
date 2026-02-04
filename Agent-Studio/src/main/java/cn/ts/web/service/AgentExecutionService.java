package cn.ts.web.service;

import cn.ts.graph.*;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.checkpoint.StateSnapshot;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.web.config.AgentExecutionConfig;
import cn.ts.web.dto.AgentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.scheduler.Schedulers;

/**
 * Agent 执行服务（重构版）
 * <p>
 * 提供流式执行 Agent 的能力，通过 SSE 向前端推送执行事件
 * </p>
 *
 * @author tianshuo
 */
@Service
public class AgentExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(AgentExecutionService.class);

    private final Map<String, CompiledGraph> graphRegistry;
    private final SessionService sessionService;
    private final CheckpointManager checkpointManager;
    private final AgentExecutionConfig config;
    private final ObjectMapper objectMapper;

    public AgentExecutionService(SessionService sessionService,
                                 CheckpointManager checkpointManager,
                                 AgentExecutionConfig config) {
        this.checkpointManager = checkpointManager;
        this.graphRegistry = new ConcurrentHashMap<>();
        this.sessionService = sessionService;
        this.config = config;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 注册一个图
     *
     * @param agentName Agent 名称
     * @param graph    编译后的图
     */
    public void registerGraph(String agentName, CompiledGraph graph) {
        graphRegistry.put(agentName, graph);
    }

    /**
     * 流式执行 Agent
     *
     * @param agentName    Agent 名称
     * @param initialState 初始状态
     * @return SSE 事件流
     */
    public Flux<AgentResponse> executeAgentStream(String agentName, Map<String, Object> initialState) {
        return executeAgentStreamWithSession(agentName, initialState, "", config.getTimeout());
    }

    /**
     * 流式执行 Agent（支持会话）
     *
     * @param agentName    Agent 名称
     * @param initialState 初始状态
     * @param sessionId    会话ID（用于保持连续对话）
     * @param timeout      超时时间
     * @return SSE 事件流
     */
    public Flux<AgentResponse> executeAgentStreamWithSession(
            String agentName,
            Map<String, Object> initialState,
            String sessionId,
            Duration timeout) {

        CompiledGraph graph = graphRegistry.get(agentName);
        if (graph == null) {
            return Flux.error(new IllegalArgumentException("Agent not found: " + agentName));
        }

        String executionId = UUID.randomUUID().toString();

        // 构建 RunnableConfig，支持 threadId
        cn.ts.graph.config.RunnableConfig.Builder configBuilder = cn.ts.graph.config.RunnableConfig.builder()
                .executionId(executionId);

        // 如果提供了 sessionId，设置为 threadId 以支持会话管理
        if (sessionId != null && !sessionId.isEmpty()) {
            configBuilder.threadId(sessionId);

            // 如果会话不存在，先创建
            if (!sessionService.sessionExists(sessionId)) {
                sessionService.createSession(agentName, "新对话");
            }
        }

        return graph.stream(initialState, configBuilder.timeout(timeout).build())
                .map(response -> toAgentResponse(response, executionId))
                .onErrorResume(throwable -> Flux.just(buildErrorResponse(throwable.getMessage(), executionId)));
    }

    /**
     * 保存到会话（已废弃）
     * <p>
     * <b>已废弃：</b>由于状态保存已移至每个节点执行完成后，此方法不再需要。
     * 消息会通过 NodeExecutor 中的检查点机制自动保存。
     * </p>
     *
     * @param sessionId   会话ID
     * @param userInput   用户输入
     * @param fullResponse 完整响应
     * @deprecated 不再需要手动保存到会话，消息通过节点检查点自动保存
     */
    @Deprecated(forRemoval = true)
    private void saveToSession(String sessionId, String userInput, String fullResponse) {
        // 保留空实现以保持二进制兼容性
    }

    /**
     * 异步生成标题（已废弃）
     * <p>
     * <b>已废弃：</b>标题生成逻辑已移除，会使用默认标题"新对话"
     * </p>
     *
     * @param sessionId 会话ID
     * @param userInput 用户输入
     * @deprecated 标题生成逻辑已移除
     */
    @Deprecated(forRemoval = true)
    private void generateTitleAsync(String sessionId, String userInput) {
        // 保留空实现以保持二进制兼容性
    }

    /**
     * 使用自定义配置流式执行 Agent
     *
     * @param agentName    Agent 名称
     * @param initialState 初始状态
     * @param timeout      超时时间
     * @return SSE 事件流
     */
    public Flux<AgentResponse> executeAgentStream(
            String agentName,
            Map<String, Object> initialState,
            Duration timeout) {

        CompiledGraph graph = graphRegistry.get(agentName);
        if (graph == null) {
            return Flux.error(new IllegalArgumentException("Agent not found: " + agentName));
        }

        String executionId = UUID.randomUUID().toString();

        return graph.stream(initialState,
                        cn.ts.graph.config.RunnableConfig.builder()
                                .executionId(executionId)
                                .timeout(timeout)
                                .build())
                .map(response -> toAgentResponse(response, executionId))
                .onErrorResume(throwable -> Flux.just(buildErrorResponse(throwable.getMessage(), executionId)));
    }

    /**
     * 异步执行 Agent（非流式）
     *
     * @param agentName    Agent 名称
     * @param initialState 初始状态
     * @return CompletableFuture 包含执行结果
     */
    public Mono<GraphResult> executeAgentAsync(String agentName, Map<String, Object> initialState) {
        CompiledGraph graph = graphRegistry.get(agentName);
        if (graph == null) {
            return Mono.error(new IllegalArgumentException("Agent not found: " + agentName));
        }

        return Mono.fromCallable(() -> graph.invoke(initialState));
    }

    /**
     * 将从检查点恢复的 messages 列表中的 Map 转换为 Message 对象
     * <p>
     * 检查点恢复时，Message 对象被反序列化为 LinkedHashMap，
     * 需要根据 messageType 字段重新转换为正确的 Message 类型
     * </p>
     *
     * @param messagesData 可能包含 Map 或 Message 的列表
     * @return 包含正确 Message 类型对象的列表
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Message> convertStateToMessages(List<?> messagesData) {
        if (messagesData == null || messagesData.isEmpty()) {
            return new ArrayList<>();
        }

        List<Message> result = new ArrayList<>();

        for (Object item : messagesData) {
            if (item == null) {
                continue;
            }

            // 如果已经是 Message 对象，直接添加
            if (item instanceof Message message) {
                result.add(message);
                continue;
            }

            // 如果是 Map，需要转换
            if (item instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) item;
                String type = (String) map.get("messageType");

                if (type == null) {
                    // 默认作为 UserMessage 处理
                    type = "USER";
                }

                try {
                    Message message = switch (type) {
                        case "USER" -> deserializeUserMessage(map);
                        case "ASSISTANT" -> deserializeAssistantMessage(map);
                        case "SYSTEM" -> deserializeSystemMessage(map);
                        case "TOOL", "TOOL_RESPONSE" -> deserializeToolResponseMessage(map);
                        default -> deserializeUserMessage(map);
                    };
                    result.add(message);
                } catch (Exception e) {
                    // 转换失败，记录日志并跳过
                    System.err.println("Failed to convert message: " + map + ", error: " + e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * 反序列化 UserMessage
     *
     * @param map 包含 UserMessage 数据的 Map
     * @return UserMessage 对象
     */
    private UserMessage deserializeUserMessage(Map<String, Object> map) {
        String text = (String) map.get("text");


        Map<String,Object> metadata = (Map<String, Object>) map.getOrDefault("metadata", new HashMap<>());
        text = text != null ? text : "";

        UserMessage.Builder builder = UserMessage.builder();
        builder.text(text);
        builder.metadata(metadata);

        return builder.build();
    }

    /**
     * 反序列化 SystemMessage
     *
     * @param map 包含 SystemMessage 数据的 Map
     * @return SystemMessage 对象
     */
    private SystemMessage deserializeSystemMessage(Map<String, Object> map) {
        Object content = map.get("content");
        String text = content != null ? content.toString() : "";
        return new SystemMessage(text);
    }

    /**
     * 反序列化 AssistantMessage
     * <p>
     * 处理 content、metadata 和 toolCalls 字段
     * </p>
     *
     * @param map 包含 AssistantMessage 数据的 Map
     * @return AssistantMessage 对象
     */
    @SuppressWarnings("unchecked")
    private AssistantMessage deserializeAssistantMessage(Map<String, Object> map) {
        Object content = map.get("content");
        String text = content != null ? content.toString() : "";

        // 处理 metadata
        Map<String, Object> metadata = (Map<String, Object>) map.get("metadata");

        // 处理 toolCalls
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        Object toolCallsObj = map.get("toolCalls");
        if (toolCallsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> toolCallMap) {
                    AssistantMessage.ToolCall toolCall = deserializeToolCall(
                        (Map<String, Object>) toolCallMap
                    );
                    if (toolCall != null) {
                        toolCalls.add(toolCall);
                    }
                }
            }
        }

        return new AssistantMessage(text, metadata, toolCalls);
    }

    /**
     * 反序列化 ToolCall
     * <p>
     * ToolCall 构造函数: (id, name, arguments, result)
     * </p>
     *
     * @param map 包含 ToolCall 数据的 Map
     * @return ToolCall 对象，如果 id 为 null 则返回 null
     */
    private AssistantMessage.ToolCall deserializeToolCall(Map<String, Object> map) {
        String id = (String) map.get("id");
        String type = (String ) map.get("type");
        String name = (String) map.get("name");
        String arguments = (String) map.get("arguments");
        if (id == null) {
            return null;
        }

        // ToolCall 构造函数: (id, name, arguments, result)
        return new AssistantMessage.ToolCall(
            id,
            type,
            name != null ? name : "",
            arguments != null ? arguments : "{}"
            // result 默认为空
        );
    }

    /**
     * 反序列化 ToolResponseMessage
     * <p>
     * 处理 responses 列表
     * </p>
     *
     * @param map 包含 ToolResponseMessage 数据的 Map
     * @return ToolResponseMessage 对象
     */
    @SuppressWarnings("unchecked")
    private ToolResponseMessage deserializeToolResponseMessage(Map<String, Object> map) {
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();

        Object responsesObj = map.get("responses");
        if (responsesObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> responseMap) {
                    String id = (String) responseMap.get("id");
                    String name = (String) responseMap.get("name");
                    String responseData = (String) responseMap.get("responseData");

                    if (id != null) {
                        responses.add(new ToolResponseMessage.ToolResponse(
                            id,
                            name != null ? name : "",
                            responseData != null ? responseData : ""
                        ));
                    }
                }
            }
        }

        return new ToolResponseMessage(responses);
    }

    /**
     * 恢复执行（从中断处继续）
     * <p>
     * 当用户提交反馈后，从检查点恢复执行
     * </p>
     *
     * @param agentName    Agent 名称
     * @param checkpointId 检查点ID
     * @param feedbackData 反馈数据
     * @param sessionId    会话ID（必需，用于获取检查点）
     * @param timeout      超时时间
     * @return SSE 事件流
     */
    public Flux<AgentResponse> resumeAgentStream(
            String agentName,
            String checkpointId,
            Map<String, Object> feedbackData,
            String sessionId,
            Duration timeout) {

        // 验证 sessionId（必需，因为需要用它来获取检查点）
        if (sessionId == null || sessionId.isEmpty()) {
            return Flux.error(new IllegalArgumentException("SessionId is required for resume"));
        }

        CompiledGraph graph = graphRegistry.get(agentName);
        if (graph == null) {
            return Flux.error(new IllegalArgumentException("Agent not found: " + agentName));
        }

        String executionId = UUID.randomUUID().toString();

        // 构建 RunnableConfig
        cn.ts.graph.config.RunnableConfig.Builder configBuilder = cn.ts.graph.config.RunnableConfig.builder()
                .executionId(executionId)
                .checkpointId(checkpointId)
                .feedbackData(feedbackData)
                .threadId(sessionId)
                .timeout(timeout);

        // 使用 CheckpointStorage 通过 threadId 和 checkpointId 获取检查点
        Optional<StateSnapshot> stateSnapshot = checkpointManager.getStorage()
                .getCheckpoint(sessionId, checkpointId);
        if (stateSnapshot.isEmpty()){
            return Flux.error(new IllegalArgumentException("Checkpoint not found: " + checkpointId + " for session: " + sessionId));
        }

        // 从 StateSnapshot 获取 nodeId 作为起始节点
        String resumeNodeId = stateSnapshot.get().getNodeId();

        logger.info("resumeAgentStream: 从检查点 {} 恢复，起始节点: {}", checkpointId, resumeNodeId);

        // 构建 RunnableConfig，设置 startNode
        configBuilder.startNode(resumeNodeId);

        // 获取状态并处理 messages 字段的反序列化
        Map<String, Object> initState = new HashMap<>(stateSnapshot.get().getState());

        // 处理 messages 字段：将 LinkedHashMap 转换回 Message 对象
        Object messagesObj = initState.get("messages");
        if (messagesObj instanceof List<?> messagesList) {
            List<Message> deserializedMessages = convertStateToMessages(messagesList);
            initState.put("messages", deserializedMessages);
        }

        return graph.stream(initState, configBuilder.build())
                .map(response -> toAgentResponse(response, executionId))
                .onErrorResume(throwable -> Flux.just(buildErrorResponse(throwable.getMessage(), executionId)));
    }

    /**
     * 转换 GraphResponse 为 AgentResponse（重构版）
     * <p>
     * 方法拆分为多个小方法，提高可读性和可维护性
     * </p>
     */
    private AgentResponse toAgentResponse(GraphResponse<NodeOutput> response, String executionId) {

        String eventType = determineEventType(response);
        String nodeType = determineNodeType(response.getNodeId());
        NodeOutput output = response.getData();

        // 确定 nodeId：优先使用 response.getNodeId()，如果为 null 则使用 output.getNodeId()
        String nodeId = response.getNodeId();
        if (nodeId == null && output != null) {
            nodeId = output.getNodeId();
        }
        if (nodeId == null) {
            nodeId = "unknown";
        }

        AgentResponse.Builder builder = AgentResponse.builder()
                .eventType(eventType)
                .nodeId(nodeId)
                .nodeType(nodeType)
                .timestamp(Instant.now())
                .executionId(executionId)
                .error(response.hasError() ? response.getError().getMessage() : null);

        if (output != null) {
            applyOutputData(builder, output);
            applyNodeStatusInfo(builder, output);
        }

        return builder.build();
    }

    /**
     * 确定事件类型
     */
    private String determineEventType(GraphResponse<NodeOutput> response) {
        if (response.hasError()) {
            return NodeStatus.FAILED.getCode();
        }

        // 判断是否为中断响应
        if (response.type() == GraphResponse.ResponseType.INTERRUPTION) {
            return "INTERRUPTION";
        }

        // 判断是否为图完成（整个流程结束）
        boolean isGraphComplete = response.isComplete() && response.getNodeId() == null;
        if (isGraphComplete) {
            System.out.println("检测到 GRAPH_COMPLETED: isComplete=" + response.isComplete() + ", nodeId=" + response.getNodeId());
            return "GRAPH_COMPLETED";
        }

        NodeOutput output = response.getData();
        if (output != null && output.getStatus() != null) {
            return output.getStatus().getCode();
        }

        return NodeStatus.PENDING.getCode();
    }

    /**
     * 确定节点类型
     */
    private String determineNodeType(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        return switch (nodeId) {
            case GraphConstants.AGENT_MODEL -> "llm";
            case GraphConstants.AGENT_TOOL -> "tool";
            default -> "custom";
        };
    }

    /**
     * 应用输出数据
     */
    private void applyOutputData(AgentResponse.Builder builder, NodeOutput output) {
        Map<String, Object> metadata = new HashMap<>();
        Map<String, Object> extraStateData = new HashMap<>();

        // 处理流式输出
        if (output instanceof StreamingOutput<?> streamingOutput) {
            applyStreamingOutput(builder, streamingOutput, metadata);
        } else {
            // 非流式输出
            applyNormalOutput(builder, output, metadata, extraStateData);
        }

        // 处理状态数据（合并）
        Map<String, Object> finalStateData = new HashMap<>();

        // 先添加来自输出的额外状态数据（如 interruption）
        if (!extraStateData.isEmpty()) {
            finalStateData.putAll(extraStateData);
        }

        // 再添加节点的状态数据
        if (output.getState() != null && !output.getState().data().isEmpty()) {
            finalStateData.putAll(extractSerializableData(output.getState().data()));
        }

        if (!finalStateData.isEmpty()) {
            builder.stateData(finalStateData);
        }

        // 处理使用信息
        if (output.getUsage() != null) {
            metadata.put("usage", buildUsageMap(output.getUsage()));
        }

        // 处理节点描述
        if (output.getNode() != null) {
            metadata.put("nodeDescription", output.getNode().description());
        }

        if (!metadata.isEmpty()) {
            builder.metadata(metadata);
        }
    }

    /**
     * 应用流式输出数据
     */
    private void applyStreamingOutput(
            AgentResponse.Builder builder,
            StreamingOutput<?> streamingOutput,
            Map<String, Object> metadata) {

        String chunk = streamingOutput.getChunk();
        if (chunk != null && !chunk.isEmpty()) {
            builder.message(chunk);
            metadata.put("chunk", chunk);
            metadata.put("outputType", streamingOutput.getOutputType().toString());

            // 如果有完整的原始数据且可序列化，也保存
            Object originData = streamingOutput.getOriginData();
            if (originData != null && isSerializable(originData)) {
                metadata.put("originData", originData);
            }
        }
    }

    /**
     * 应用普通输出数据
     */
    private void applyNormalOutput(
            AgentResponse.Builder builder,
            NodeOutput output,
            Map<String, Object> metadata,
            Map<String, Object> extraStateData) {

        if (output.getResultValue() != null) {
            Object resultValue = output.getResultValue();

            // 特殊处理中断输出
            // NodeExecutor 将 InterruptionOutput 包装在 Map 中：
            // Map.of("interruption", interruptionOutput, "interrupted", true)
            if (resultValue instanceof Map<?, ?> resultValueMap) {
                Object interruption = resultValueMap.get("interruption");
                if (interruption instanceof cn.ts.graph.InterruptionOutput) {
                    applyInterruptionOutput(builder, (cn.ts.graph.InterruptionOutput) interruption, extraStateData);
                    return;
                }
            }

            if (isSerializable(resultValue)) {
                metadata.put("resultValue", resultValue);
            } else {
                metadata.put("resultValue", resultValue.toString());
            }
        }
    }

    /**
     * 应用中断输出数据
     * <p>
     * 将 InterruptionOutput 转换为前端期望的格式
     * </p>
     */
    private void applyInterruptionOutput(
            AgentResponse.Builder builder,
            cn.ts.graph.InterruptionOutput interruption,
            Map<String, Object> extraStateData) {

        Map<String, Object> interruptionData = new HashMap<>();
        Map<String, Object> metadata = new HashMap<>();

        // 构建中断元数据
        metadata.put("nodeId", interruption.metadata().getNodeId());
        metadata.put("message", interruption.metadata().getMessage());
        metadata.put("timestamp", interruption.metadata().getTimestamp().toString());
        metadata.put("customData", interruption.metadata().getCustomData());

        // 构建中断数据
        interruptionData.put("metadata", metadata);
        interruptionData.put("checkpointId", interruption.checkpointId());
        // 添加 threadId（用于恢复时查找检查点）
        if (interruption.threadId() != null && !interruption.threadId().isEmpty()) {
            interruptionData.put("threadId", interruption.threadId());
        }

        // 添加到 extraStateData，而不是直接设置到 builder
        extraStateData.put("interruption", interruptionData);
    }

    /**
     * 应用节点状态信息
     */
    private void applyNodeStatusInfo(AgentResponse.Builder builder, NodeOutput output) {
        builder.nodeStatus(output.getStatus() != null ? output.getStatus().getCode() : null)
                .title(output.getTitle())
                .startTime(output.getStartTime())
                .endTime(output.getEndTime())
                .logs(output.getLogs())
                .nodeErrorMessage(output.getErrorMessage());
    }

    /**
     * 构建使用信息映射
     */
    private Map<String, Object> buildUsageMap(org.springframework.ai.chat.metadata.Usage usage) {
        if (usage == null) {
            return Map.of();
        }
        return Map.of(
                "promptTokens", usage.getPromptTokens(),
                "completionTokens", usage.getCompletionTokens(),
                "totalTokens", usage.getTotalTokens()
        );
    }

    /**
     * 构建错误响应
     * <p>
     * 解析错误消息，判断错误类型并返回友好的用户提示
     * </p>
     */
    private AgentResponse buildErrorResponse(String errorMessage, String executionId) {
        // 解析错误消息，判断是否为特定类型的错误
        String eventType = "ERROR";
        String userMessage = "执行错误";

        if (errorMessage != null) {
            // 检查 429 错误
            if (errorMessage.contains("429") || errorMessage.contains("Too Many Requests")) {
                eventType = "RATE_LIMIT";
                userMessage = "请求过于频繁，请稍后再试";
            }
            // 检查 503 错误
            else if (errorMessage.contains("503") || errorMessage.contains("Service Unavailable")) {
                eventType = "SERVICE_UNAVAILABLE";
                userMessage = "外部服务暂时不可用";
            }
            // 检查认证失败
            else if (errorMessage.contains("401") || errorMessage.contains("Unauthorized")) {
                eventType = "AUTH_FAILED";
                userMessage = "API 认证失败，请检查密钥配置";
            }
            // 检查 WebClientResponseException
            else if (errorMessage.contains("WebClientResponseException")) {
                // 尝试解析状态码
                if (errorMessage.contains("status 429")) {
                    eventType = "RATE_LIMIT";
                    userMessage = "请求过于频繁，请稍后再试";
                } else if (errorMessage.contains("status 503") || errorMessage.contains("status 502")) {
                    eventType = "SERVICE_UNAVAILABLE";
                    userMessage = "外部服务暂时不可用";
                } else if (errorMessage.contains("status 401")) {
                    eventType = "AUTH_FAILED";
                    userMessage = "API 认证失败，请检查密钥配置";
                } else {
                    eventType = "API_ERROR";
                    userMessage = "外部 API 调用失败";
                }
            }
            // 默认错误消息
            else {
                userMessage = "执行错误: " + errorMessage;
            }
        }

        return AgentResponse.builder()
                .eventType(eventType)
                .timestamp(Instant.now())
                .executionId(executionId)
                .error(errorMessage)
                .message(userMessage)
                .build();
    }

    /**
     * 从状态数据中提取可序列化的简单类型数据
     */
    private Map<String, Object> extractSerializableData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (isSerializable(entry.getValue())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * 检查对象是否可被 Jackson 序列化
     */
    private boolean isSerializable(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
            return true;
        }
        if (obj instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String) || !isSerializable(entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if (obj instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (!isSerializable(item)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 检查 Agent 是否已注册
     */
    public boolean isAgentRegistered(String agentName) {
        return graphRegistry.containsKey(agentName);
    }

    /**
     * 获取所有已注册的 Agent 名称
     */
    public java.util.Set<String> getRegisteredAgents() {
        return graphRegistry.keySet();
    }

    /**
     * 注销 Agent
     */
    public void unregisterAgent(String agentName) {
        graphRegistry.remove(agentName);
    }
}
