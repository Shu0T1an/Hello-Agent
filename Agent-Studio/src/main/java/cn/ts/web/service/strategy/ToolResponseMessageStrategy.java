package cn.ts.web.service.strategy;

import cn.ts.web.constant.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ToolResponseMessage 反序列化策略
 * <p>
 * 负责将 Map 数据反序列化为 ToolResponseMessage 对象
 * 处理 responses 列表
 * </p>
 *
 * @author tianshuo
 */
public class ToolResponseMessageStrategy implements MessageDeserializationStrategy {

    private static final Logger log = LoggerFactory.getLogger(ToolResponseMessageStrategy.class);

    @Override
    public boolean supports(String messageType) {
        return ApiConstants.MessageTypes.TOOL_RESPONSE.equals(messageType) ||
               ApiConstants.MessageTypes.TOOL.equals(messageType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResponseMessage deserialize(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException("Message data map cannot be null");
        }

        List<ToolResponseMessage.ToolResponse> responses = extractResponses(map);

        log.debug("Deserialized ToolResponseMessage with {} responses", responses.size());
        return new ToolResponseMessage(responses);
    }

    @Override
    public String getSupportedMessageType() {
        return ApiConstants.MessageTypes.TOOL_RESPONSE;
    }

    /**
     * 提取工具响应列表
     */
    @SuppressWarnings("unchecked")
    private List<ToolResponseMessage.ToolResponse> extractResponses(Map<String, Object> map) {
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();

        Object responsesObj = map.get("responses");
        if (!(responsesObj instanceof List<?>)) {
            return responses;
        }

        for (Object item : (List<?>) responsesObj) {
            if (item instanceof Map<?, ?>) {
                ToolResponseMessage.ToolResponse response = deserializeToolResponse((Map<String, Object>) item);
                if (response != null) {
                    responses.add(response);
                }
            }
        }

        return responses;
    }

    /**
     * 反序列化单个 ToolResponse
     * <p>
     * ToolResponse 构造函数: (id, name, responseData)
     * </p>
     */
    private ToolResponseMessage.ToolResponse deserializeToolResponse(Map<String, Object> map) {
        String id = (String) map.get("id");
        if (id == null) {
            return null;
        }

        String name = (String) map.get("name");
        String responseData = (String) map.get("responseData");

        return new ToolResponseMessage.ToolResponse(
                id,
                name != null ? name : "",
                responseData != null ? responseData : ""
        );
    }
}
