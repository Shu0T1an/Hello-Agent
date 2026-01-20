package cn.ts.web.service;

import cn.ts.graph.CompiledGraph;
import cn.ts.graph.GraphResponse;
import cn.ts.graph.GraphResult;
import cn.ts.graph.NodeOutput;
import cn.ts.graph.StreamingOutput;
import cn.ts.web.dto.AgentResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 执行服务
 * <p>
 * 提供流式执行 Agent 的能力，通过 SSE 向前端推送执行事件
 * </p>
 *
 * @author tianshuo
 */
@Service
public class AgentExecutionService {

    private final Map<String, CompiledGraph> graphRegistry;

    public AgentExecutionService() {
        this.graphRegistry = new ConcurrentHashMap<>();
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
        CompiledGraph graph = graphRegistry.get(agentName);
        if (graph == null) {
            return Flux.error(new IllegalArgumentException("Agent not found: " + agentName));
        }

        String executionId = UUID.randomUUID().toString();

        return graph.stream(initialState)
                .map(response -> toAgentResponse(response, executionId))
                .onErrorResume(throwable -> Flux.just(AgentResponse.builder()
                        .eventType("ERROR")
                        .timestamp(Instant.now())
                        .executionId(executionId)
                        .error(throwable.getMessage())
                        .message("执行错误: " + throwable.getMessage())
                        .build()));
    }

    /**
     * 使用自定义配置流式执行 Agent
     *
     * @param agentName    Agent 名称
     * @param initialState 初始状态
     * @param timeout      超时时间
     * @return SSE 事件流
     */
    public Flux<AgentResponse> executeAgentStream(String agentName, Map<String, Object> initialState, Duration timeout) {
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
                .onErrorResume(throwable -> Flux.just(AgentResponse.builder()
                        .eventType("ERROR")
                        .timestamp(Instant.now())
                        .executionId(executionId)
                        .error(throwable.getMessage())
                        .message("执行错误: " + throwable.getMessage())
                        .build()));
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
     * 转换 GraphResponse 为 AgentResponse
     * <p>
     * 注意：只提取可序列化的关键数据，避免序列化包含复杂对象的完整状态
     * </p>
     */
    private AgentResponse toAgentResponse(GraphResponse<NodeOutput> response, String executionId) {
        String eventType;
        String nodeId = response.getNodeId();
        NodeOutput output = response.getData();

        if (response.hasError()) {
            eventType = "ERROR";
        } else if (nodeId == null && response.isNormalComplete()) {
            // 图完成响应（nodeId 为 null，isNormalComplete 为 true）
            eventType = "COMPLETE";
        } else if (response.isStream()) {
            if (response.isComplete()) {
                eventType = "STREAM_COMPLETE";
            } else {
                eventType = "STREAM_DATA";
            }
        } else {
            eventType = "NODE_COMPLETE";
        }

        Map<String, Object> stateData = null;
        String message = null;
        Map<String, Object> metadata = new HashMap<>();

        if (output != null) {
            // === 检查是否是 StreamingOutput 并提取 chunk ===
            // StreamingOutput 继承 NodeOutput，用于流式输出
            // output.getResultValue() 此时就是 chunk（文本片段）
            if (output instanceof StreamingOutput) {
                StreamingOutput<?> streamingOutput = (StreamingOutput<?>) output;

                // 获取文本片段
                String chunk = streamingOutput.getChunk();

                if (chunk != null && !chunk.isEmpty()) {
                    // 将实际的文本片段设置为 message
                    message = chunk;
                    metadata.put("chunk", chunk);

                    // 保存输出类型
                    metadata.put("outputType", streamingOutput.getOutputType().toString());

                    // 如果有完整的原始数据且可序列化，也保存
                    Object originData = streamingOutput.getOriginData();
                    if (originData != null && isSerializable(originData)) {
                        metadata.put("originData", originData);
                    }
                }
            } else {
                // === 非流式输出，使用原有逻辑 ===
                if (output.getResultValue() != null) {
                    // 尝试提取简单的结果值
                    Object resultValue = output.getResultValue();
                    if (isSerializable(resultValue)) {
                        metadata.put("resultValue", resultValue);
                    } else {
                        metadata.put("resultValue", resultValue.toString());
                    }
                }
            }

            // 只提取可序列化的简单类型数据，避免包含复杂对象
            if (output.getState() != null && !output.getState().data().isEmpty()) {
                stateData = extractSerializableData(output.getState().data());
            }
            if (output.getUsage() != null) {
                metadata.put("usage", Map.of(
                        "promptTokens", output.getUsage().getPromptTokens(),
                        "completionTokens", output.getUsage().getCompletionTokens(),
                        "totalTokens", output.getUsage().getTotalTokens()
                ));
            }
            if (output.getNode() != null) {
                metadata.put("nodeDescription", output.getNode().description());
            }
        }

        // 如果 message 还没被设置（非流式或 chunk 为空），使用原有的 switch 逻辑
        // 注意：STREAM_DATA 且 chunk 为空时，不设置默认消息（避免显示无关内容）
        if (message == null && !"STREAM_DATA".equals(eventType)) {
            message = switch (eventType) {
                case "COMPLETE" -> "Graph execution completed";
                case "NODE_COMPLETE" -> "Node execution completed: " + nodeId;
                case "STREAM_DATA" -> "";  // 不设置默认消息
                case "STREAM_COMPLETE" -> "Stream completed for: " + nodeId;
                case "ERROR" -> "Error in node: " + nodeId;
                default -> "Event: " + eventType;
            };
        }

        return AgentResponse.builder()
                .eventType(eventType)
                .nodeId(nodeId)
                .stateData(stateData)
                .message(message)
                .timestamp(Instant.now())
                .executionId(executionId)
                .error(response.hasError() ? response.getError().getMessage() : null)
                .metadata(metadata)
                .build();
    }

    /**
     * 从状态数据中提取可序列化的简单类型数据
     * <p>
     * 只提取 String, Number, Boolean, null 等基本类型，以及仅包含这些类型的 Map 和 List
     * </p>
     *
     * @param data 原始状态数据
     * @return 可序列化的数据子集
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
     * <p>
     * 可序列化的类型包括：null, String, Number, Boolean, 以及只包含这些类型的 Map 和 List
     * </p>
     *
     * @param obj 要检查的对象
     * @return true 如果可序列化
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
     *
     * @param agentName Agent 名称
     * @return true 如果已注册
     */
    public boolean isAgentRegistered(String agentName) {
        return graphRegistry.containsKey(agentName);
    }

    /**
     * 获取所有已注册的 Agent 名称
     *
     * @return Agent 名称列表
     */
    public java.util.Set<String> getRegisteredAgents() {
        return graphRegistry.keySet();
    }

    /**
     * 注销 Agent
     *
     * @param agentName Agent 名称
     */
    public void unregisterAgent(String agentName) {
        graphRegistry.remove(agentName);
    }
}
