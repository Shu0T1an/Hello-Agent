package cn.ts.graph.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jackson 模块，用于正确反序列化 Spring AI Message 类
 * <p>
 * 根据 JSON 中的 messageType 字段创建正确的 Message 子类实例。
 * 支持的 Message 类型：
 * <ul>
 *   <li>USER → UserMessage</li>
 *   <li>ASSISTANT → AssistantMessage</li>
 *   <li>SYSTEM → SystemMessage</li>
 *   <li>TOOL → ToolResponseMessage</li>
 * </ul>
 * </p>
 *
 * @author tianshuo
 */
public class MessageJsonModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    /**
     * 创建 MessageJsonModule
     */
    public MessageJsonModule() {
        super("MessageJsonModule");
        addDeserializer(Message.class, new MessageJsonDeserializer());
    }

    /**
     * Message 反序列化器
     * <p>
     * 根据 metadata 中的 messageType 字段确定具体的 Message 类型并创建相应实例。
     * </p>
     */
    public static class MessageJsonDeserializer extends JsonDeserializer<Message> {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public Message deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);

            // 提取 messageType
            MessageType messageType = extractMessageType(node);

            // 根据类型创建相应的 Message 实例
            return switch (messageType) {
                case USER -> deserializeUserMessage(node);
                case ASSISTANT -> deserializeAssistantMessage(node);
                case SYSTEM -> deserializeSystemMessage(node);
                case TOOL -> deserializeToolResponseMessage(node);
            };
        }

        /**
         * 从 JsonNode 中提取 MessageType
         * <p>
         * 处理大小写问题：Spring AI 序列化时使用大写枚举名（如 "ASSISTANT"），
         * 但 MessageType.fromValue() 期望小写值（如 "assistant"）。
         * </p>
         *
         * @param node JSON 节点
         * @return MessageType
         */
        private MessageType extractMessageType(JsonNode node) {
            // 首先尝试从顶层字段获取 messageType（Spring AI Message 序列化时会同时写入顶层和 metadata）
            JsonNode messageTypeNode = node.get("messageType");
            if (messageTypeNode != null && messageTypeNode.isTextual()) {
                String typeValue = messageTypeNode.asText();
                MessageType type = parseMessageType(typeValue);
                if (type != null) {
                    return type;
                }
            }

            // 尝试从 metadata 中获取 messageType
            JsonNode metadataNode = node.get("metadata");
            if (metadataNode != null && metadataNode.isObject()) {
                JsonNode metadataMessageTypeNode = metadataNode.get("messageType");
                if (metadataMessageTypeNode != null && metadataMessageTypeNode.isTextual()) {
                    String typeValue = metadataMessageTypeNode.asText();
                    MessageType type = parseMessageType(typeValue);
                    if (type != null) {
                        return type;
                    }
                }
            }

            // 默认返回 USER
            return MessageType.USER;
        }

        /**
         * 解析 MessageType 字符串
         * <p>
         * 尝试多种格式：小写（"assistant"）、大写（"ASSISTANT"）、枚举名
         * </p>
         *
         * @param typeValue 类型字符串
         * @return MessageType，如果无法解析则返回 null
         */
        private MessageType parseMessageType(String typeValue) {
            if (typeValue == null || typeValue.isEmpty()) {
                return null;
            }

            try {
                // 首先尝试直接解析（可能是小写格式如 "assistant"）
                return MessageType.fromValue(typeValue);
            } catch (IllegalArgumentException e1) {
                try {
                    // 尝试转为小写再解析（处理大写格式如 "ASSISTANT"）
                    return MessageType.fromValue(typeValue.toLowerCase());
                } catch (IllegalArgumentException e2) {
                    // 尝试作为枚举名解析
                    try {
                        return MessageType.valueOf(typeValue);
                    } catch (IllegalArgumentException e3) {
                        // 尝试大写枚举名
                        try {
                            return MessageType.valueOf(typeValue.toUpperCase());
                        } catch (IllegalArgumentException e4) {
                            // 所有尝试都失败
                            return null;
                        }
                    }
                }
            }
        }

        /**
         * 反序列化 UserMessage
         */
        private UserMessage deserializeUserMessage(JsonNode node) {
            // 尝试从 textContent 或 text 字段获取内容
            String content = "";
            if (node.has("textContent")) {
                content = node.get("textContent").asText();
            } else if (node.has("text")) {
                content = node.get("text").asText();
            }

            Map<String, Object> metadata = extractMetadata(node);

            return UserMessage.builder()
                    .text(content)
                    .metadata(metadata)
                    .build();
        }

        /**
         * 反序列化 AssistantMessage
         */
        private AssistantMessage deserializeAssistantMessage(JsonNode node) {
            // 尝试从 textContent 或 text 字段获取内容
            String content = "";
            if (node.has("textContent")) {
                content = node.get("textContent").asText();
            } else if (node.has("text")) {
                content = node.get("text").asText();
            }

            Map<String, Object> metadata = extractMetadata(node);

            // 提取 toolCalls
            List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
            JsonNode toolCallsNode = node.get("toolCalls");
            if (toolCallsNode != null && toolCallsNode.isArray()) {
                for (JsonNode toolCallNode : toolCallsNode) {
                    String id = toolCallNode.has("id") ? toolCallNode.get("id").asText() : "";
                    String type = toolCallNode.has("type") ? toolCallNode.get("type").asText() : "";
                    String name = toolCallNode.has("name") ? toolCallNode.get("name").asText() : "";
                    String arguments = toolCallNode.has("arguments") ? toolCallNode.get("arguments").asText() : "";
                    toolCalls.add(new AssistantMessage.ToolCall(id, type, name, arguments));
                }
            }

            return  AssistantMessage.builder()
                        .content(content)
                        .properties(metadata)
                        .toolCalls(toolCalls)
                        .build();
        }

        /**
         * 反序列化 SystemMessage
         * <p>
         * 注意：SystemMessage 构造函数是 private，需要使用 Builder。
         * 但是 Builder 也是 private，需要使用静态工厂方法或反射。
         * 这里使用简单的 text 构造函数，metadata 会丢失。
         * </p>
         */
        private SystemMessage deserializeSystemMessage(JsonNode node) {
            // 尝试从 textContent 或 text 字段获取内容
            String content = "";
            if (node.has("textContent")) {
                content = node.get("textContent").asText();
            } else if (node.has("text")) {
                content = node.get("text").asText();
            }

            // SystemMessage 的 Builder 是 private，暂时只使用 content
            // 如果需要 metadata，可能需要使用反射或等待 Spring AI API 变更
            return new SystemMessage(content);
        }

        /**
         * 反序列化 ToolResponseMessage
         */
        private ToolResponseMessage deserializeToolResponseMessage(JsonNode node) {
            Map<String, Object> metadata = extractMetadata(node);

            // 提取 responses
            List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
            JsonNode responsesNode = node.get("responses");
            if (responsesNode != null && responsesNode.isArray()) {
                for (JsonNode responseNode : responsesNode) {
                    String id = responseNode.has("id") ? responseNode.get("id").asText() : "";
                    String name = responseNode.has("name") ? responseNode.get("name").asText() : "";
                    String responseData = responseNode.has("responseData") ?
                            responseNode.get("responseData").asText() : "";
                    responses.add(new ToolResponseMessage.ToolResponse(id, name, responseData));
                }
            }

            return   ToolResponseMessage.builder()
                        .responses(responses)
                        .metadata(metadata)
                        .build();
        }

        /**
         * 从 JsonNode 中提取 metadata
         */
        private Map<String, Object> extractMetadata(JsonNode node) {
            Map<String, Object> metadata = new HashMap<>();
            JsonNode metadataNode = node.get("metadata");
            if (metadataNode != null && metadataNode.isObject()) {
                metadataNode.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    JsonNode valueNode = entry.getValue();

                    if (valueNode.isTextual()) {
                        metadata.put(key, valueNode.asText());
                    } else if (valueNode.isBoolean()) {
                        metadata.put(key, valueNode.asBoolean());
                    } else if (valueNode.isInt()) {
                        metadata.put(key, valueNode.asInt());
                    } else if (valueNode.isDouble()) {
                        metadata.put(key, valueNode.asDouble());
                    } else if (valueNode.isObject() || valueNode.isArray()) {
                        try {
                            metadata.put(key, objectMapper.treeToValue(valueNode, Object.class));
                        } catch (IOException e) {
                            metadata.put(key, valueNode.toString());
                        }
                    }
                });
            }
            return metadata;
        }
    }
}
