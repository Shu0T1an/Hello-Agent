package cn.ts.web.shared.constant;

/**
 * Session 相关常量定义
 * <p>
 * 集中管理 Session 服务中的魔法字符串和默认值
 * </p>
 *
 * @author tianshuo
 */
public final class SessionConstants {

    /**
     * 默认会话标题
     */
    public static final String DEFAULT_SESSION_TITLE = "新对话";

    /**
     * 活跃状态
     */
    public static final String STATUS_ACTIVE = "active";

    /**
     * State 中的键名常量
     */
    public static final class StateKeys {
        /**
         * 消息列表键
         */
        public static final String MESSAGES = "messages";

        /**
         * 当前 Agent 键
         */
        public static final String CURRENT_AGENT = "current_agent";

        /**
         * Agent 历史键
         */
        public static final String AGENT_HISTORY = "agent_history";

        /**
         * 迭代次数键
         */
        public static final String ITERATION = "iteration";

        /**
         * 最大迭代次数键
         */
        public static final String MAX_ITERATIONS = "max_iterations";

        /**
         * 输入键
         */
        public static final String INPUT = "input";

        /**
         * 执行记录键
         */
        public static final String EXECUTE_RECORD = "execute_record";

        private StateKeys() {
            // 防止实例化
        }
    }

    /**
     * Checkpoint 相关常量
     */
    public static final class Checkpoint {
        /**
         * 初始节点名称
         */
        public static final String INIT_NODE = "INIT";

        /**
         * 手动来源
         */
        public static final String SOURCE_MANUAL = "manual";

        /**
         * 自动来源
         */
        public static final String SOURCE_AUTO = "auto";

        /**
         * 错误来源
         */
        public static final String SOURCE_ERROR = "error";

        private Checkpoint() {
            // 防止实例化
        }
    }

    /**
     * 默认值常量
     */
    public static final class Defaults {
        /**
         * 默认迭代次数
         */
        public static final int DEFAULT_ITERATION = 0;

        /**
         * 默认最大迭代次数
         */
        public static final int DEFAULT_MAX_ITERATIONS = 10;

        private Defaults() {
            // 防止实例化
        }
    }

    private SessionConstants() {
        // 防止实例化
    }
}
