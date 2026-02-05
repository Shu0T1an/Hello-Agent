package cn.ts.agent.constant;

/**
 * Agent 相关常量定义
 * <p>
 * 集中管理 Agent 服务中的魔法字符串和默认值
 * </p>
 *
 * @author tianshuo
 */
public final class AgentConstants {

    /**
     * 默认系统提示词
     */
    public static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant.";

    /**
     * 消息角色常量
     */
    public static final class MessageRoles {
        /**
         * 用户角色
         */
        public static final String USER = "user";

        /**
         * 助手角色
         */
        public static final String ASSISTANT = "assistant";

        /**
         * 工具调用角色
         */
        public static final String TOOL_CALL = "tool_call";

        /**
         * 工具响应角色
         */
        public static final String TOOL_RESPONSE = "tool_response";

        /**
         * 系统角色
         */
        public static final String SYSTEM = "system";

        private MessageRoles() {
            // 防止实例化
        }
    }

    /**
     * 输出类型常量
     */
    public static final class OutputTypes {
        /**
         * LLM 流输出键
         */
        public static final String LLM_STREAM = "llm_stream";

        /**
         * 聊天响应键
         */
        public static final String CHAT_RESPONSE = "chat_response";

        private OutputTypes() {
            // 防止实例化
        }
    }

    /**
     * 默认值常量
     */
    public static final class Defaults {
        /**
         * 默认启用重试
         */
        public static final boolean DEFAULT_ENABLE_RETRY = true;

        /**
         * 默认不启用流式输出
         */
        public static final boolean DEFAULT_STREAMING = false;

        private Defaults() {
            // 防止实例化
        }
    }

    private AgentConstants() {
        // 防止实例化
    }
}
