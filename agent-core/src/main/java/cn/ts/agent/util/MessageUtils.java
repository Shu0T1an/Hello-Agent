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

    private MessageUtils() {
        // 防止实例化
    }

    /**
     * Message 路由器
     * <p>
     * 用于判断 Message 的类型并返回对应的路由目标
     * </p>
     */
    public static final class MessageRouter {

        /**
         * 从 LLM 节点路由
         * <p>
         * 根据最后一条消息决定下一个节点：
         * - 有 toolCalls → 工具节点
         * - 是 ToolResponseMessage → LLM 节点（继续循环）
         * - 其他 → 结束节点
         * </p>
         *
         * @param messages 消息列表
         * @param toolNodeName 工具节点名称
         * @param modelNodeName 模型节点名称
         * @param endNodeName 结束节点名称
         * @return 下一个节点名称
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
            } else if (isToolResponse(last)) {
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
     * <p>
     * 从 Message 对象提取角色、内容和元数据
     * </p>
     */
    public static final class MessageExtractor {

        /**
         * 从 Message 对象提取角色
         *
         * @param message 消息对象
         * @return 角色字符串
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
            return AgentConstants.MessageRoles.USER; // 默认
        }

        /**
         * 从 Message 对象提取内容
         *
         * @param message 消息对象
         * @return 内容字符串
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
         * <p>
         * 为前端提供结构化的工具调用和响应数据
         * </p>
         *
         * @param message 消息对象
         * @return 元数据 Map
         */
        public static Map<String, Object> extractMetadata(Message message) {
            Map<String, Object> metadata = new HashMap<>();

            if (message instanceof UserMessage || message instanceof SystemMessage) {
                return metadata;
            }

            if (message instanceof AssistantMessage assistantMessage) {
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

        /**
         * 格式化工具调用信息
         */
        private static String formatToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
            StringBuilder sb = new StringBuilder();
            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                String name = toolCall.name();
                String arguments = toolCall.arguments();
                sb.append("**调用工具**: `").append(name).append("`\n\n");
                sb.append("**参数**: ```json\n").append(arguments).append("\n```\n");
            }
            return sb.toString();
        }

        /**
         * 格式化工具响应信息
         */
        private static String formatToolResponses(List<ToolResponseMessage.ToolResponse> responses) {
            StringBuilder sb = new StringBuilder();
            for (ToolResponseMessage.ToolResponse response : responses) {
                String name = response.name();
                String result = response.responseData();
                sb.append("**工具结果**: `").append(name).append("` \n\n");
                sb.append("**返回值**: ```\n").append(result).append("\n```\n");
            }
            return sb.toString();
        }

        private MessageExtractor() {
            // 防止实例化
        }
    }

    /**
     * Message 构建器
     * <p>
     * 用于构建各种类型的 Message
     * </p>
     */
    public static final class MessageBuilder {

        /**
         * 创建用户消息
         */
        public static UserMessage createUserMessage(String content) {
            return new UserMessage(content);
        }

        /**
         * 创建系统消息
         */
        public static SystemMessage createSystemMessage(String content) {
            return new SystemMessage(content);
        }

        /**
         * 创建助手消息
         */
        public static AssistantMessage createAssistantMessage(String content) {
            return new AssistantMessage(content);
        }

        /**
         * 创建带有工具调用的助手消息
         */
        public static AssistantMessage createAssistantMessage(String content,
                                                               List<AssistantMessage.ToolCall> toolCalls) {
            // Spring AI 的 AssistantMessage 构造函数可能不同，这里提供一个简单的实现
            // 如果需要支持工具调用，请使用其他方式创建消息
            return new AssistantMessage(content, null, toolCalls);
        }

        /**
         * 创建工具响应消息
         */
        public static ToolResponseMessage createToolResponseMessage(
                List<ToolResponseMessage.ToolResponse> responses) {
            return new ToolResponseMessage(responses);
        }

        private MessageBuilder() {
            // 防止实例化
        }
    }
}
