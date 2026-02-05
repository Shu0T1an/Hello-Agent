package cn.ts.web.constant;

/**
 * API 相关常量定义
 * <p>
 * 集中管理 Web API 中使用的常量，避免魔法字符串散布在代码中
 * </p>
 *
 * @author tianshuo
 */
public final class ApiConstants {

    /**
     * API 路径常量
     */
    public static final class Paths {
        /**
         * Agent 执行路径
         */
        public static final String AGENT_EXECUTE = "/api/stream/agent/{agentName}/execute";

        /**
         * Agent 恢复路径
         */
        public static final String AGENT_RESUME = "/api/stream/agent/{agentName}/resume";

        /**
         * Agent 检查路径
         */
        public static final String AGENT_EXISTS = "/api/stream/agent/{agentName}/exists";

        /**
         * 获取所有 Agent 路径
         */
        public static final String AGENTS = "/api/stream/agents";

        /**
         * Session 路径
         */
        public static final String SESSION = "/api/session";

        /**
         * Session 列表路径
         */
        public static final String SESSIONS = "/api/sessions";

        private Paths() {
            // 防止实例化
        }
    }

    /**
     * HTTP 状态码常量
     */
    public static final class HttpStatus {
        /**
         * 400 - 错误的请求
         */
        public static final int BAD_REQUEST = 400;

        /**
         * 401 - 未授权
         */
        public static final int UNAUTHORIZED = 401;

        /**
         * 404 - 未找到
         */
        public static final int NOT_FOUND = 404;

        /**
         * 429 - 请求过多
         */
        public static final int TOO_MANY_REQUESTS = 429;

        /**
         * 500 - 服务器内部错误
         */
        public static final int INTERNAL_SERVER_ERROR = 500;

        /**
         * 502 - 网关错误
         */
        public static final int BAD_GATEWAY = 502;

        /**
         * 503 - 服务不可用
         */
        public static final int SERVICE_UNAVAILABLE = 503;

        private HttpStatus() {
            // 防止实例化
        }
    }

    /**
     * 消息类型常量
     */
    public static final class MessageTypes {
        /**
         * 用户消息类型
         */
        public static final String USER = "USER";

        /**
         * 助手消息类型
         */
        public static final String ASSISTANT = "ASSISTANT";

        /**
         * 系统消息类型
         */
        public static final String SYSTEM = "SYSTEM";

        /**
         * 工具响应消息类型
         */
        public static final String TOOL_RESPONSE = "TOOL_RESPONSE";

        /**
         * 工具消息类型（别名）
         */
        public static final String TOOL = "TOOL";

        private MessageTypes() {
            // 防止实例化
        }
    }

    /**
     * 错误消息常量
     */
    public static final class ErrorMessages {
        /**
         * Agent 未找到错误
         */
        public static final String AGENT_NOT_FOUND = "Agent not found: ";

        /**
         * Session 未找到错误
         */
        public static final String SESSION_NOT_FOUND = "Session not found: ";

        /**
         * 检查点未找到错误
         */
        public static final String CHECKPOINT_NOT_FOUND = "Checkpoint not found: ";

        /**
         * 会话ID必需错误
         */
        public static final String SESSION_ID_REQUIRED = "SessionId is required for resume";

        /**
         * 请求过于频繁错误
         */
        public static final String RATE_LIMIT_EXCEEDED = "请求过于频繁，请稍后再试";

        /**
         * 服务不可用错误
         */
        public static final String SERVICE_UNAVAILABLE_MSG = "外部服务暂时不可用";

        /**
         * 认证失败错误
         */
        public static final String AUTH_FAILED_MSG = "API 认证失败，请检查密钥配置";

        /**
         * API 调用失败错误
         */
        public static final String API_ERROR_MSG = "外部 API 调用失败";

        /**
         * 执行错误
         */
        public static final String EXECUTION_ERROR = "执行错误: ";

        private ErrorMessages() {
            // 防止实例化
        }
    }

    /**
     * 节点类型常量
     */
    public static final class NodeTypes {
        /**
         * LLM 节点类型
         */
        public static final String LLM = "llm";

        /**
         * 工具节点类型
         */
        public static final String TOOL = "tool";

        /**
         * 自定义节点类型
         */
        public static final String CUSTOM = "custom";

        private NodeTypes() {
            // 防止实例化
        }
    }

    private ApiConstants() {
        // 防止实例化
    }
}
