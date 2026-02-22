package cn.ts.web.dto;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 消息视图对象
 * <p>
 * 用于将 Message 对象或 Map 转换为前端可用的格式
 * </p>
 *
 * @author tianshuo
 */
public class MessageVO {

    private String id;
    private String type;           // USER/ASSISTANT/SYSTEM/TOOL
    private String content;
    private Map<String, Object> metadata;
    private Instant timestamp;

    /**
     * 从 Message 对象转换
     *
     * @param message Message 对象
     * @return MessageVO
     */
    public static Optional<MessageVO> from(Message message) {
        if (message == null) {
            return Optional.empty();
        }

        MessageVO vo = new MessageVO();
        vo.id = java.util.UUID.randomUUID().toString();
        vo.timestamp = Instant.now();

        if (message instanceof UserMessage) {
            vo.type = "USER";
            vo.content = ((UserMessage) message).getText();
            vo.metadata = ((UserMessage) message).getMetadata();
        } else if (message instanceof AssistantMessage am) {
            vo.type = "ASSISTANT";
            vo.content = am.getText();
            vo.metadata = am.getMetadata();
        } else if (message instanceof SystemMessage) {
            vo.type = "SYSTEM";
            vo.content = ((SystemMessage) message).getText();
            vo.metadata = ((SystemMessage) message).getMetadata();
        } else if (message instanceof ToolResponseMessage) {
            vo.type = "TOOL";
            vo.content = message.getText();
            vo.metadata = ((ToolResponseMessage) message).getMetadata();
        } else {
            vo.type = "UNKNOWN";
            vo.content = message.getText();
            vo.metadata = Map.of();
        }

        return Optional.of(vo);
    }

    /**
     * 从 Map 转换（用于从数据库反序列化后的消息）
     *
     * @param map 消息 Map
     * @return MessageVO
     */
    @SuppressWarnings("unchecked")
    public static Optional<MessageVO> fromMap(Map<String, Object> map) {
        if (map == null) {
            return Optional.empty();
        }

        MessageVO vo = new MessageVO();
        vo.id = (String) map.get("id");
        if (vo.id == null) {
            vo.id = java.util.UUID.randomUUID().toString();
        }

        // 尝试从 messageType 字段获取类型
        String messageType = (String) map.get("messageType");
        if (messageType == null) {
            // 回退到 type 字段
            messageType = (String) map.get("type");
        }

        // 如果没有类型字段，默认为 USER
        if (messageType == null) {
            messageType = "USER";
        }

        vo.type = switch (messageType) {
            case "ASSISTANT" -> "ASSISTANT";
            case "SYSTEM" -> "SYSTEM";
            case "TOOL", "TOOL_RESPONSE" -> "TOOL";
            default -> "USER";
        };

        // 获取内容
        if ("USER".equals(vo.type) || "ASSISTANT".equals(vo.type) || "SYSTEM".equals(vo.type)) {
            vo.content = (String) map.get("text");
            if (vo.content == null) {
                vo.content = (String) map.get("content");
            }
        } else if ("TOOL".equals(vo.type)) {
            vo.content = map.get("responses") != null ? map.get("responses").toString() : "";
        } else {
            vo.content = map.get("content") != null ? map.get("content").toString() : "";
        }

        // 获取元数据
        vo.metadata = (Map<String, Object>) map.getOrDefault("metadata", Map.of());

        // 获取时间戳
        Object timestampObj = map.get("timestamp");
        if (timestampObj instanceof String) {
            try {
                vo.timestamp = Instant.parse((String) timestampObj);
            } catch (Exception e) {
                vo.timestamp = Instant.now();
            }
        } else {
            vo.timestamp = Instant.now();
        }

        return Optional.of(vo);
    }

    /**
     * 从列表转换
     *
     * @param items 可能包含 Message 或 Map 的列表
     * @return MessageVO 列表
     */
    public static List<MessageVO> fromList(List<?> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<MessageVO> result = new ArrayList<>();
        for (Object item : items) {
            if (item == null) {
                continue;
            }

            if (item instanceof Message message) {
                from(message).ifPresent(result::add);
            } else if (item instanceof Map<?, ?> map) {
                fromMap((Map<String, Object>) map).ifPresent(result::add);
            }
        }
        return result;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
