package cn.ts.agent.constant;

/**
 * Agent 状态键常量定义
 * <p>
 * 集中管理 Agent 执行过程中使用的状态键名，避免魔法字符串散布在代码中
 * </p>
 *
 * @author tianshuo
 */
public final class StateKeys {

    /**
     * 输入键
     * <p>
     * 用户输入内容，通常是 String 类型
     * </p>
     */
    public static final String INPUT = "input";

    /**
     * 消息列表键
     * <p>
     * 存储对话历史，List&lt;Message&gt; 类型
     * </p>
     */
    public static final String MESSAGES = "messages";

    /**
     * 迭代次数键
     * <p>
     * 当前迭代次数，Integer 类型，初始值为 0
     * </p>
     */
    public static final String ITERATION = "iteration";

    /**
     * 最大迭代次数键
     * <p>
     * 最大允许迭代次数，Integer 类型，默认为 10
     * </p>
     */
    public static final String MAX_ITERATIONS = "max_iterations";

    /**
     * 执行记录键
     * <p>
     * 记录执行过程中的关键信息，List&lt;Map&lt;String, Object&gt;&gt; 类型
     * </p>
     */
    public static final String EXECUTE_RECORD = "execute_record";

    /**
     * 聊天响应键
     * <p>
     * 存储最新的 ChatResponse 对象
     * </p>
     */
    public static final String CHAT_RESPONSE = "chat_response";

    /**
     * 跳转目标键
     * <p>
     * 用于 Hook 跳转逻辑，存储 JumpTo 对象
     * </p>
     */
    public static final String JUMP_TO = "jump_to";

    /**
     * 当前 Agent 键
     * <p>
     * 当前使用的 Agent 名称
     * </p>
     */
    public static final String CURRENT_AGENT = "current_agent";

    /**
     * Agent 历史键
     * <p>
     * Agent 切换历史记录
     * </p>
     */
    public static final String AGENT_HISTORY = "agent_history";

    /**
     * 中断数据键
     * <p>
     * 用于存储中断相关的信息
     * </p>
     */
    public static final String INTERRUPTION = "interruption";

    /**
     * 中断标志键
     * <p>
     * 标记是否被中断
     * </p>
     */
    public static final String INTERRUPTED = "interrupted";

    /**
     * 澄清轮次键
     * <p>
     * 记录当前会话已触发的澄清轮次，避免无限循环澄清
     * </p>
     */
    public static final String CLARIFICATION_ROUND = "clarification_round";

    /**
     * 最近一次澄清问题签名
     * <p>
     * 用于防止相同澄清问题在连续轮次重复触发
     * </p>
     */
    public static final String CLARIFICATION_LAST_SIGNATURE = "clarification_last_signature";
    public static final String CLARIFICATION_LAST_FEEDBACK_SIGNATURE = "clarification_last_feedback_signature";

    /**
     * Todo 列表键
     */
    public static final String TODOS = "todos";

    /**
     * Todo 元信息键（version/updatedAt/updatedByToolCallId/lastOperation）
     */
    public static final String TODOS_META = "todos_meta";

    private StateKeys() {
        // 防止实例化
    }
}
