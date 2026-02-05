package cn.ts.web.service.strategy;

import cn.ts.web.constant.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * UserMessage 反序列化策略
 * <p>
 * 负责将 Map 数据反序列化为 UserMessage 对象
 * </p>
 *
 * @author tianshuo
 */
public class UserMessageStrategy implements MessageDeserializationStrategy {

    private static final Logger log = LoggerFactory.getLogger(UserMessageStrategy.class);

    @Override
    public boolean supports(String messageType) {
        return ApiConstants.MessageTypes.USER.equals(messageType);
    }

    @Override
    public UserMessage deserialize(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException("Message data map cannot be null");
        }

        String text = extractText(map);
        Map<String, Object> metadata = extractMetadata(map);

        UserMessage.Builder builder = UserMessage.builder();
        builder.text(text);
        builder.metadata(metadata);

        log.debug("Deserialized UserMessage with text length: {}", text.length());
        return builder.build();
    }

    @Override
    public String getSupportedMessageType() {
        return ApiConstants.MessageTypes.USER;
    }

    /**
     * 提取文本内容
     */
    private String extractText(Map<String, Object> map) {
        Object textObj = map.get("text");
        if (textObj == null) {
            return "";
        }
        return textObj.toString();
    }

    /**
     * 提取元数据
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMetadata(Map<String, Object> map) {
        Object metadataObj = map.get("metadata");
        if (metadataObj instanceof Map) {
            return new HashMap<>((Map<String, Object>) metadataObj);
        }
        return new HashMap<>();
    }
}
