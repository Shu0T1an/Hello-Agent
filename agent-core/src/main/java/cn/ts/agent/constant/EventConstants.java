package cn.ts.agent.constant;

/**
 * Agent 事件常量定义
 * <p>
 * 集中管理 Agent 执行过程中的事件类型常量
 * </p>
 *
 * @author tianshuo
 */
public final class EventConstants {

    /**
     * 节点开始事件
     */
    public static final String NODE_STARTED = "NODE_STARTED";

    /**
     * 节点完成事件
     */
    public static final String NODE_COMPLETED = "NODE_COMPLETED";

    /**
     * 节点失败事件
     */
    public static final String NODE_FAILED = "NODE_FAILED";

    /**
     * 图完成事件
     */
    public static final String GRAPH_COMPLETED = "GRAPH_COMPLETED";

    /**
     * 中断事件
     */
    public static final String INTERRUPTION = "INTERRUPTION";

    /**
     * 错误事件
     */
    public static final String ERROR = "ERROR";

    /**
     * 速率限制错误事件
     */
    public static final String RATE_LIMIT = "RATE_LIMIT";

    /**
     * 服务不可用错误事件
     */
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    /**
     * 认证失败错误事件
     */
    public static final String AUTH_FAILED = "AUTH_FAILED";

    /**
     * API 错误事件
     */
    public static final String API_ERROR = "API_ERROR";

    /**
     * 流式输出事件
     */
    public static final String STREAMING = "STREAMING";

    /**
     * 工具调用事件
     */
    public static final String TOOL_CALL = "TOOL_CALL";

    /**
     * 工具响应事件
     */
    public static final String TOOL_RESPONSE = "TOOL_RESPONSE";

    /**
     * LLM 输出事件
     */
    public static final String LLM_OUTPUT = "LLM_OUTPUT";

    /**
     * 会话标题生成事件
     */
    public static final String TITLE_GENERATED = "TITLE_GENERATED";

    private EventConstants() {
        // 防止实例化
    }
}
