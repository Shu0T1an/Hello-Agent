package cn.ts.web.service;

import cn.ts.graph.CompiledGraph;
import cn.ts.graph.GraphResponse;
import cn.ts.graph.GraphResult;
import cn.ts.graph.NodeOutput;
import cn.ts.web.dto.ExecutionEventDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CompiledGraph> graphRegistry = new ConcurrentHashMap<>();

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
    public Flux<String> executeAgentStream(String agentName, Map<String, Object> initialState) {
        CompiledGraph graph = graphRegistry.get(agentName);
        if (graph == null) {
            return Flux.error(new IllegalArgumentException("Agent not found: " + agentName));
        }

        String executionId = UUID.randomUUID().toString();

        return graph.stream(initialState)
                .map(response -> toEventDTO(response, executionId))
                .map(dto -> {
                    try {
                        return "data: " + objectMapper.writeValueAsString(dto) + "\n\n";
                    } catch (Exception e) {
                        return "data: {\"error\": \"Failed to serialize event\"}\n\n";
                    }
                })
                .doOnSubscribe(subscription -> {
                    // 连接建立时发送开始事件
                })
                .doOnComplete(() -> {
                    // 流完成时清理资源
                })
                .doOnError(error -> {
                    // 流错误时清理资源
                });
    }

    /**
     * 使用自定义配置流式执行 Agent
     *
     * @param agentName    Agent 名称
     * @param initialState 初始状态
     * @param timeout      超时时间
     * @return SSE 事件流
     */
    public Flux<String> executeAgentStream(String agentName, Map<String, Object> initialState, Duration timeout) {
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
                .map(response -> toEventDTO(response, executionId))
                .map(dto -> {
                    try {
                        return "data: " + objectMapper.writeValueAsString(dto) + "\n\n";
                    } catch (Exception e) {
                        return "data: {\"error\": \"Failed to serialize event\"}\n\n";
                    }
                });
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
     * 转换 GraphResponse 为 ExecutionEventDTO
     */
    private ExecutionEventDTO toEventDTO(GraphResponse<NodeOutput> response, String executionId) {
        String eventType;
        String nodeId = response.getNodeId();
        NodeOutput output = response.getData();

        if (response.hasError()) {
            eventType = "ERROR";
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
            if (output.getState() != null) {
                stateData = new HashMap<>(output.getState().data());
            }
            if (output.getResultValue() != null) {
                metadata.put("resultValue", output.getResultValue());
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

        message = switch (eventType) {
            case "NODE_COMPLETE" -> "Node execution completed: " + nodeId;
            case "STREAM_DATA" -> "Stream data from: " + nodeId;
            case "STREAM_COMPLETE" -> "Stream completed for: " + nodeId;
            case "ERROR" -> "Error in node: " + nodeId;
            default -> "Event: " + eventType;
        };

        return ExecutionEventDTO.builder()
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
