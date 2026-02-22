package cn.ts.web.service.strategy;

import cn.ts.web.shared.constant.ApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.Map;

/**
 * SystemMessage 反序列化策略
 * <p>
 * 负责将 Map 数据反序列化为 SystemMessage 对象
 * </p>
 *
 * @author tianshuo
 */
public class SystemMessageStrategy implements MessageDeserializationStrategy {

    private static final Logger log = LoggerFactory.getLogger(SystemMessageStrategy.class);

    @Override
    public boolean supports(String messageType) {
        return ApiConstants.MessageTypes.SYSTEM.equals(messageType);
    }

    @Override
    public SystemMessage deserialize(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException("Message data map cannot be null");
        }

        Object content = map.get("content");
        String text = content != null ? content.toString() : "";

        log.debug("Deserialized SystemMessage with text length: {}", text.length());
        return new SystemMessage(text);
    }

    @Override
    public String getSupportedMessageType() {
        return ApiConstants.MessageTypes.SYSTEM;
    }
}
