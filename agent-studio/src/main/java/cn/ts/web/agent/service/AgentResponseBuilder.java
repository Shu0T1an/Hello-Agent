package cn.ts.web.agent.service;

import cn.ts.agent.constant.EventConstants;
import cn.ts.agent.constant.StateKeys;
import cn.ts.graph.*;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.state.State;
import cn.ts.web.shared.constant.ApiConstants;
import cn.ts.web.agent.dto.AgentResponse;
import cn.ts.web.session.service.MessageConversionService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Agent 响应构建器
 * <p>
 * 负责将 GraphResponse 转换为 AgentResponse。
 * 封装所有响应构建逻辑，包括事件类型判断、节点类型判断、输出数据处理等。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * AgentResponseBuilder builder = new AgentResponseBuilder();
 * AgentResponse response = builder.build(response, executionId);
 * }</pre>
 * </p>
 *
 * @author tianshuo
 */
@Component
public class AgentResponseBuilder {

    private static final Set<String> REQUIRED_STATE_KEYS = Set.of(
            "execution_record",
            StateKeys.TODOS,
            StateKeys.TODOS_META
    );
    private static final String THINK = "think";
    private static final String THINK_DELTA = "think_delta";
    private static final String REASONING_CONTENT = "reasoningContent";
    private static final String REASONING_CONTENT_SNAKE = "reasoning_content";

    private final MessageConversionService messageConversionService;

    /**
     * 创建 Agent 响应构建器
     *
     * @param messageConversionService 消息转换服务（用于状态恢复时的消息反序列化）
     */
    public AgentResponseBuilder(MessageConversionService messageConversionService) {
        this.messageConversionService = messageConversionService;
    }

    /**
     * 构建 Agent 响应
     *
     * @param response    图响应
     * @param executionId 执行ID
     * @return Agent 响应
     */
    public AgentResponse build(GraphResponse<NodeOutput> response, String executionId) {
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
     * 构建错误响应
     *
     * @param errorMessage 错误消息
     * @param executionId 执行ID
     * @return Agent 响应
     */
    public AgentResponse buildErrorResponse(String errorMessage, String executionId) {
        // 解析错误消息，判断是否为特定类型的错误
        String eventType = EventConstants.ERROR;
        String userMessage = ApiConstants.ErrorMessages.EXECUTION_ERROR;

        if (errorMessage != null) {
            // 检查 429 错误
            if (errorMessage.contains("429") || errorMessage.contains("Too Many Requests")) {
                eventType = EventConstants.RATE_LIMIT;
                userMessage = ApiConstants.ErrorMessages.RATE_LIMIT_EXCEEDED;
            }
            // 检查 503 错误
            else if (errorMessage.contains("503") || errorMessage.contains("Service Unavailable")) {
                eventType = EventConstants.SERVICE_UNAVAILABLE;
                userMessage = ApiConstants.ErrorMessages.SERVICE_UNAVAILABLE_MSG;
            }
            // 检查认证失败
            else if (errorMessage.contains("401") || errorMessage.contains("Unauthorized")) {
                eventType = EventConstants.AUTH_FAILED;
                userMessage = ApiConstants.ErrorMessages.AUTH_FAILED_MSG;
            }
            // 检查 WebClientResponseException
            else if (errorMessage.contains("WebClientResponseException")) {
                // 尝试解析状态码
                if (errorMessage.contains("status 429")) {
                    eventType = EventConstants.RATE_LIMIT;
                    userMessage = ApiConstants.ErrorMessages.RATE_LIMIT_EXCEEDED;
                } else if (errorMessage.contains("status 503") || errorMessage.contains("status 502")) {
                    eventType = EventConstants.SERVICE_UNAVAILABLE;
                    userMessage = ApiConstants.ErrorMessages.SERVICE_UNAVAILABLE_MSG;
                } else if (errorMessage.contains("status 401")) {
                    eventType = EventConstants.AUTH_FAILED;
                    userMessage = ApiConstants.ErrorMessages.AUTH_FAILED_MSG;
                } else {
                    eventType = EventConstants.API_ERROR;
                    userMessage = ApiConstants.ErrorMessages.API_ERROR_MSG;
                }
            }
            // 默认错误消息
            else {
                userMessage = ApiConstants.ErrorMessages.EXECUTION_ERROR + errorMessage;
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
     * 确定事件类型
     */
    private String determineEventType(GraphResponse<NodeOutput> response) {
        if (response.hasError()) {
            return EventConstants.NODE_FAILED;
        }

        // 判断是否为中断响应
        if (response.type() == GraphResponse.ResponseType.INTERRUPTION) {
            return EventConstants.INTERRUPTION;
        }

        // 判断是否为图完成（整个流程结束）
        boolean isGraphComplete = response.isComplete() && response.getNodeId() == null;
        if (isGraphComplete) {
            return EventConstants.GRAPH_COMPLETED;
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
            case GraphConstants.AGENT_MODEL -> ApiConstants.NodeTypes.LLM;
            case GraphConstants.AGENT_TOOL -> ApiConstants.NodeTypes.TOOL;
            default -> ApiConstants.NodeTypes.CUSTOM;
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
            mergeRequiredStateData(output.getState().data(), finalStateData);
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

        metadata.put("outputType", streamingOutput.getOutputType().toString());
        String chunk = streamingOutput.getChunk();
        if (chunk != null && !chunk.isEmpty()) {
            builder.message(chunk);
            metadata.put("chunk", chunk);
        }

        // 如果有完整的原始数据且可序列化，也保存
        Object originData = streamingOutput.getOriginData();
        if (originData != null && isSerializable(originData)) {
            metadata.put("originData", originData);
        }

        Optional<String> thinkingDelta = extractThinkingFromOriginData(originData);
        thinkingDelta.ifPresent(value -> {
            metadata.put(THINK_DELTA, value);
            putThinkingAliases(metadata, value);
        });

        if (streamingOutput.getOutputType() == OutputType.COMPLETE) {
            extractThinkingFromState(streamingOutput.getState())
                    .ifPresent(value -> {
                        metadata.put(THINK, value);
                        putThinkingAliases(metadata, value);
                    });
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
            if (resultValue instanceof Map<?, ?> resultValueMap) {
                Object interruption = resultValueMap.get("interruption");
                if (interruption instanceof InterruptionOutput) {
                    applyInterruptionOutput(builder, (InterruptionOutput) interruption, extraStateData);
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
     */
    private void applyInterruptionOutput(
            AgentResponse.Builder builder,
            InterruptionOutput interruption,
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
    private Map<String, Object> buildUsageMap(Usage usage) {
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
     * Ensure required state keys are forwarded whenever they exist in node state.
     */
    private void mergeRequiredStateData(Map<String, Object> source, Map<String, Object> target) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (String key : REQUIRED_STATE_KEYS) {
            if (!source.containsKey(key)) {
                continue;
            }
            Object value = source.get(key);
            if (value == null || isSerializable(value)) {
                target.put(key, value);
            }
        }
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

    private Optional<String> extractThinkingFromOriginData(Object originData) {
        if (!(originData instanceof ChatResponse chatResponse)) {
            return Optional.empty();
        }
        if (chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return Optional.empty();
        }
        Map<String, Object> metadata = chatResponse.getResult().getOutput().getMetadata();
        return extractThinkingFromMetadata(metadata);
    }

    private Optional<String> extractThinkingFromState(State state) {
        if (state == null) {
            return Optional.empty();
        }
        List<?> messages = state.<List<?>>value(StateKeys.MESSAGES).orElse(List.of());
        if (messages.isEmpty()) {
            return Optional.empty();
        }
        Object last = messages.get(messages.size() - 1);
        if (!(last instanceof AssistantMessage assistantMessage)) {
            return Optional.empty();
        }
        return extractThinkingFromMetadata(assistantMessage.getMetadata());
    }

    private Optional<String> extractThinkingFromMetadata(Map<String, Object> metadata) {
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

    private void putThinkingAliases(Map<String, Object> metadata, String value) {
        metadata.put(THINK, value);
        metadata.put(REASONING_CONTENT, value);
        metadata.put(REASONING_CONTENT_SNAKE, value);
    }
}
