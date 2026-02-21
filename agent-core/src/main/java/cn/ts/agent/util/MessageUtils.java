package cn.ts.agent.util;

import cn.ts.agent.constant.AgentConstants;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Message 工具类
 * <p>
 * 统一管理 Message 类型的判断、转换和元数据提取
 * </p>
 *
 * @author tianshuo
 */
public final class MessageUtils {

    private static final String THINK = "think";
    private static final String THINK_DELTA = "think_delta";
    private static final String REASONING_CONTENT = "reasoningContent";
    private static final String REASONING_CONTENT_SNAKE = "reasoning_content";

    private MessageUtils() {
        // 防止实例化
    }

    /**
     * Message 路由器
     */
    public static final class MessageRouter {

        /**
         * 从 LLM 节点路由
         */
        public static String routeFromModel(List<Message> messages,
                                            String toolNodeName,
                                            String modelNodeName,
                                            String endNodeName) {
            if (messages == null || messages.isEmpty()) {
                return endNodeName;
            }

            Message last = messages.get(messages.size() - 1);
            if (isAssistantWithToolCalls(last)) {
                return toolNodeName;
            }
            if (isToolResponse(last)) {
                return modelNodeName;
            }
            return endNodeName;
        }

        /**
         * 检查消息是否是带有工具调用的 AssistantMessage
         */
        public static boolean isAssistantWithToolCalls(Message message) {
            return message instanceof AssistantMessage am && am.hasToolCalls();
        }

        /**
         * 检查消息是否是 ToolResponseMessage
         */
        public static boolean isToolResponse(Message message) {
            return message instanceof ToolResponseMessage;
        }

        private MessageRouter() {
            // 防止实例化
        }
    }

    /**
     * Message 类型提取器
     */
    public static final class MessageExtractor {

        /**
         * 从 Message 对象提取角色
         */
        public static String extractRole(Message message) {
            if (message instanceof UserMessage) {
                return AgentConstants.MessageRoles.USER;
            }
            if (message instanceof AssistantMessage am && am.hasToolCalls()) {
                return AgentConstants.MessageRoles.TOOL_CALL;
            }
            if (message instanceof AssistantMessage) {
                return AgentConstants.MessageRoles.ASSISTANT;
            }
            if (message instanceof ToolResponseMessage) {
                return AgentConstants.MessageRoles.TOOL_RESPONSE;
            }
            if (message instanceof SystemMessage) {
                return AgentConstants.MessageRoles.SYSTEM;
            }
            return AgentConstants.MessageRoles.USER;
        }

        /**
         * 从 Message 对象提取内容
         */
        public static String extractContent(Message message) {
            if (message instanceof UserMessage userMessage) {
                return userMessage.getText();
            }

            if (message instanceof AssistantMessage assistantMessage) {
                if (assistantMessage.hasToolCalls()) {
                    return formatToolCalls(assistantMessage.getToolCalls());
                }
                return assistantMessage.getText();
            }

            if (message instanceof ToolResponseMessage toolResponseMessage) {
                return formatToolResponses(toolResponseMessage.getResponses());
            }

            return "这是一条空消息";
        }

        /**
         * 从 Message 对象提取元数据
         */
        public static Map<String, Object> extractMetadata(Message message) {
            Map<String, Object> metadata = new HashMap<>();

            if (message instanceof UserMessage || message instanceof SystemMessage) {
                return metadata;
            }

            if (message instanceof AssistantMessage assistantMessage) {
                mergeThinkingMetadata(assistantMessage.getMetadata(), metadata);

                if (assistantMessage.hasToolCalls()) {
                    List<Map<String, Object>> toolCallsList = new ArrayList<>();
                    for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                        Map<String, Object> toolCallInfo = new HashMap<>();
                        toolCallInfo.put("id", toolCall.id());
                        toolCallInfo.put("name", toolCall.name());
                        toolCallInfo.put("type", toolCall.type());
                        toolCallInfo.put("arguments", toolCall.arguments());
                        toolCallsList.add(toolCallInfo);
                    }
                    metadata.put("tool_calls", toolCallsList);
                }
                return metadata;
            }

            if (message instanceof ToolResponseMessage toolResponseMessage) {
                List<Map<String, Object>> toolResponsesList = new ArrayList<>();
                for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                    Map<String, Object> responseInfo = new HashMap<>();
                    responseInfo.put("id", response.id());
                    responseInfo.put("name", response.name());
                    responseInfo.put("response", response.responseData());
                    toolResponsesList.add(responseInfo);
                }
                metadata.put("tool_responses", toolResponsesList);
                return metadata;
            }

            return metadata;
        }

        private static void mergeThinkingMetadata(Map<String, Object> source, Map<String, Object> target) {
            if (source == null || source.isEmpty()) {
                return;
            }
            copyIfPresent(source, target, THINK);
            copyIfPresent(source, target, THINK_DELTA);
            copyIfPresent(source, target, REASONING_CONTENT);
            copyIfPresent(source, target, REASONING_CONTENT_SNAKE);
        }

        private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
            Object value = source.get(key);
            if (value != null) {
                target.put(key, value.toString());
            }
        }

        private static String formatToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
            StringBuilder sb = new StringBuilder();
            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                sb.append("**调用工具**: `")
                        .append(toolCall.name())
                        .append("`\n\n");
                sb.append("**参数**: ```json\n")
                        .append(toolCall.arguments())
                        .append("\n```\n");
            }
            return sb.toString();
        }

        private static String formatToolResponses(List<ToolResponseMessage.ToolResponse> responses) {
            StringBuilder sb = new StringBuilder();
            for (ToolResponseMessage.ToolResponse response : responses) {
                sb.append("**工具结果**: `")
                        .append(response.name())
                        .append("` \n\n");
                sb.append("**返回值**: ```\n")
                        .append(response.responseData())
                        .append("\n```\n");
            }
            return sb.toString();
        }

        private MessageExtractor() {
            // 防止实例化
        }
    }

    /**
     * Message 构建器
     */
    public static final class MessageBuilder {

        public static UserMessage createUserMessage(String content) {
            return new UserMessage(content);
        }

        public static SystemMessage createSystemMessage(String content) {
            return new SystemMessage(content);
        }

        public static AssistantMessage createAssistantMessage(String content) {
            return new AssistantMessage(content);
        }

        public static AssistantMessage createAssistantMessage(String content,
                                                              List<AssistantMessage.ToolCall> toolCalls) {
            return AssistantMessage.builder()
                    .content(content)
                    .toolCalls(toolCalls != null ? toolCalls : List.of())
                    .build();
        }

        public static ToolResponseMessage createToolResponseMessage(
                List<ToolResponseMessage.ToolResponse> responses) {
            return ToolResponseMessage.builder()
                    .responses(responses != null ? responses : List.of())
                    .build();
        }

        private MessageBuilder() {
            // 防止实例化
        }
    }
}

