package cn.ts.web.service;

import cn.ts.graph.*;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.web.config.AgentExecutionConfig;
import cn.ts.web.dto.AgentResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private final Map<String, CompiledGraph> graphRegistry;
    private final SessionService sessionService;
    private final TitleGeneratorService titleGeneratorService;
    private final AgentExecutionConfig config;

    public AgentExecutionService(SessionService sessionService,
                                TitleGeneratorService titleGeneratorService,
                                AgentExecutionConfig config) {
        this.graphRegistry = new ConcurrentHashMap<>();
        this.sessionService = sessionService;
        this.titleGeneratorService = titleGeneratorService;
        this.config = config;
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
        String userInput = extractUserInput(initialState);

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

        // 判断是否需要生成标题（仅当会话消息数为0时）
        boolean shouldGenerateTitle = sessionId != null
                && !sessionId.isEmpty()
                && sessionService.getSession(sessionId)
                        .map(s -> s.getMessageCount() == 0)
                        .orElse(false);

        // 收集完整的AI回复
        StringBuilder fullResponse = new StringBuilder();

        return graph.stream(initialState, configBuilder.timeout(timeout).build())
                .doOnNext(response -> {
                    // 收集流式输出
                    if (response.getData() instanceof StreamingOutput) {
                        StreamingOutput<?> streamingOutput = (StreamingOutput<?>) response.getData();
                        String chunk = streamingOutput.getChunk();
                        if (chunk != null && streamingOutput.getStatus()!=NodeStatus.COMPLETED) {
                            fullResponse.append(chunk);
                        }
                    }
                })
                .doOnComplete(() -> {
                    // 执行完成后保存消息到会话（使用 addMessageIfNotExists 防止重复）
                    saveToSession(sessionId, userInput, fullResponse.toString());

                    // 异步生成标题（不阻塞主流程）
                    if (shouldGenerateTitle && userInput != null && !userInput.isEmpty()) {
                        generateTitleAsync(sessionId, userInput);
                    }
                })
                .map(response -> toAgentResponse(response, executionId))
                .onErrorResume(throwable -> Flux.just(buildErrorResponse(throwable.getMessage(), executionId)));
    }

    /**
     * 保存到会话（使用 addMessageIfNotExists 防止重复）
     * <p>
     * 由于 NodeExecutor 已经更新了 context.state["messages"]，
     * 我们使用 addMessageIfNotExists 来避免重复保存相同的消息。
     * </p>
     */
    private void saveToSession(String sessionId, String userInput, String fullResponse) {
        if (sessionId != null && !sessionId.isEmpty()) {
            // 保存用户消息（如果还没有保存的话）
            if (userInput != null && !userInput.isEmpty()) {
                sessionService.addMessageIfNotExists(sessionId, "user", userInput);
            }
            // 保存AI回复（如果还没有保存的话）
            if (fullResponse.length() > 0) {
                sessionService.addMessageIfNotExists(sessionId, "assistant", fullResponse);
            }
        }
    }

    /**
     * 异步生成标题
     */
    private void generateTitleAsync(String sessionId, String userInput) {
        Schedulers.boundedElastic().schedule(() -> {
            try {
                String title = titleGeneratorService.generateTitle(userInput);

                // 使用配置的标题长度限制
                if (title.length() > config.getMaxTitleLength()) {
                    title = title.substring(0, config.getMaxTitleLength());
                }

                sessionService.updateSession(sessionId, title);
            } catch (Exception e) {
                // 标题生成失败不影响主流程
            }
        });
    }

    /**
     * 从初始状态中提取用户输入
     */
    private String extractUserInput(Map<String, Object> initialState) {
        if (initialState == null) {
            return null;
        }
        // 尝试从 "input" 键获取
        Object input = initialState.get("input");
        if (input != null) {
            return input.toString();
        }
        // 尝试从 messages 列表获取最后一条用户消息
        Object messages = initialState.get("messages");
        if (messages instanceof List<?> && !((List<?>) messages).isEmpty()) {
            Object lastMessage = ((List<?>) messages).get(((List<?>) messages).size() - 1);
            if (lastMessage instanceof Map<?, ?>) {
                Object content = ((Map<?, ?>) lastMessage).get("content");
                if (content != null) {
                    return content.toString();
                }
            }
        }
        return null;
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
     * 转换 GraphResponse 为 AgentResponse（重构版）
     * <p>
     * 方法拆分为多个小方法，提高可读性和可维护性
     * </p>
     */
    private AgentResponse toAgentResponse(GraphResponse<NodeOutput> response, String executionId) {
        String eventType = determineEventType(response);
        String nodeType = determineNodeType(response.getNodeId());
        NodeOutput output = response.getData();

        AgentResponse.Builder builder = AgentResponse.builder()
                .eventType(eventType)
                .nodeId(response.getNodeId())
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

        // 处理流式输出
        if (output instanceof StreamingOutput<?> streamingOutput) {
            applyStreamingOutput(builder, streamingOutput, metadata);
        } else {
            // 非流式输出
            applyNormalOutput(builder, output, metadata);
        }

        // 处理状态数据
        if (output.getState() != null && !output.getState().data().isEmpty()) {
            builder.stateData(extractSerializableData(output.getState().data()));
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
            Map<String, Object> metadata) {

        if (output.getResultValue() != null) {
            Object resultValue = output.getResultValue();
            if (isSerializable(resultValue)) {
                metadata.put("resultValue", resultValue);
            } else {
                metadata.put("resultValue", resultValue.toString());
            }
        }
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
     */
    private AgentResponse buildErrorResponse(String errorMessage, String executionId) {
        return AgentResponse.builder()
                .eventType("ERROR")
                .timestamp(Instant.now())
                .executionId(executionId)
                .error(errorMessage)
                .message("执行错误: " + errorMessage)
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
