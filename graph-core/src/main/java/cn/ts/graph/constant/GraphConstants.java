package cn.ts.graph.constant;

/**
 * Graph 框架常量定义
 * <p>
 * 参考 Spring AI Alibaba Graph 的设计，定义图的起始和结束节点标识
 * </p>
 *
 * @author tianshuo
 */
public final class GraphConstants {

    /**
     * 起始节点标识
     * <p>
     * 用于标识图的入口点，所有流程从 START 开始
     * </p>
     */
    public static final String START = "__start__";

    /**
     * 结束节点标识
     * <p>
     * 用于标识图的出口点，流程到达 END 时终止执行
     * </p>
     */
    public static final String END = "__end__";

    /**
     * Agent LLM 节点标识
     * <p>
     * 用于 ReAct Agent 中的 LLM 调用节点
     * </p>
     */
    public static final String AGENT_MODEL = "_AGENT_MODEL_";

    /**
     * Agent 工具节点标识
     * <p>
     * 用于 ReAct Agent 中的工具执行节点
     * </p>
     */
    public static final String AGENT_TOOL = "_AGENT_TOOL_";

    /**
     * Agent 中间结束节点标识
     * <p>
     * 用于 ReAct Agent 中提前终止流程的中间节点
     * </p>
     */
    public static final String AGENT_END = "_AGENT_END_";

    private GraphConstants() {
        // 防止实例化
    }
}
