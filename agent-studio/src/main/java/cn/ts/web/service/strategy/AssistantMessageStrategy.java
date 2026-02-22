package cn.ts.web.service.strategy;

import cn.ts.web.shared.constant.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AssistantMessage 反序列化策略
 * <p>
 * 负责将 Map 数据反序列化为 AssistantMessage 对象
 * 处理 content、metadata 和 toolCalls 字段
 * </p>
 *
 * @author tianshuo
 */
public class AssistantMessageStrategy implements MessageDeserializationStrategy {

    private static final Logger log = LoggerFactory.getLogger(AssistantMessageStrategy.class);

    @Override
    public boolean supports(String messageType) {
        return ApiConstants.MessageTypes.ASSISTANT.equals(messageType);
    }

    @Override
    public AssistantMessage deserialize(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException("Message data map cannot be null");
        }

        String text = extractContent(map);
        Map<String, Object> metadata = extractMetadata(map);
        List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(map);

        log.debug("Deserialized AssistantMessage with {} tool calls", toolCalls.size());

        return AssistantMessage.builder()
                    .content(text)
                    .properties(metadata)
                    .toolCalls(toolCalls)
                    .build();
    }

    @Override
    public String getSupportedMessageType() {
        return ApiConstants.MessageTypes.ASSISTANT;
    }

    /**
     * 提取内容
     */
    private String extractContent(Map<String, Object> map) {
        Object content = map.get("content");
        return content != null ? content.toString() : "";
    }

    /**
     * 提取元数据
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMetadata(Map<String, Object> map) {
        Object metadataObj = map.get("metadata");
        if (metadataObj instanceof Map) {
            return (Map<String, Object>) metadataObj;
        }
        return new HashMap<>();
    }

    /**
     * 提取工具调用列表
     */
    @SuppressWarnings("unchecked")
    private List<AssistantMessage.ToolCall> extractToolCalls(Map<String, Object> map) {
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

        Object toolCallsObj = map.get("toolCalls");
        if (!(toolCallsObj instanceof List<?>)) {
            return toolCalls;
        }

        for (Object item : (List<?>) toolCallsObj) {
            if (item instanceof Map<?, ?>) {
                AssistantMessage.ToolCall toolCall = deserializeToolCall((Map<String, Object>) item);
                if (toolCall != null) {
                    toolCalls.add(toolCall);
                }
            }
        }

        return toolCalls;
    }

    /**
     * 反序列化 ToolCall
     * <p>
     * ToolCall 构造函数: (id, type, name, arguments)
     * </p>
     */
    private AssistantMessage.ToolCall deserializeToolCall(Map<String, Object> map) {
        String id = (String) map.get("id");
        if (id == null) {
            return null;
        }

        String type = (String) map.get("type");
        String name = (String) map.get("name");
        String arguments = (String) map.get("arguments");

        return new AssistantMessage.ToolCall(
                id,
                type != null ? type : "",
                name != null ? name : "",
                arguments != null ? arguments : "{}"
        );
    }
}
