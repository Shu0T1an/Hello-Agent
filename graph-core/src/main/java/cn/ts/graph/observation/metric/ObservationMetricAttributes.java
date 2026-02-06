package cn.ts.graph.observation.metric;

/**
 * 可观测性指标属性定义
 * <p>
 * 定义所有用于 Micrometer Observation 的指标属性键名
 * </p>
 *
 * @author tianshuo
 */
public enum ObservationMetricAttributes {

    /**
     * 观测类型（低基数标签）
     */
    KIND("hello.agent.kind"),

    /**
     * 图名称（低基数标签）
     */
    GRAPH_NAME("hello.agent.graph.name"),

    /**
     * 图执行成功标志（低基数标签）
     */
    GRAPH_SUCCESS("hello.agent.graph.success"),

    /**
     * 节点名称（低基数标签）
     */
    NODE_NAME("hello.agent.graph.node.name"),

    /**
     * 节点执行成功标志（低基数标签）
     */
    NODE_SUCCESS("hello.agent.graph.node.success"),

    /**
     * 边名称（低基数标签）
     */
    EDGE_NAME("hello.agent.graph.edge.name"),

    /**
     * 边执行成功标志（低基数标签）
     */
    EDGE_SUCCESS("hello.agent.graph.edge.success"),

    /**
     * 执行ID（低基数标签）
     */
    EXECUTION_ID("hello.agent.execution.id"),

    /**
     * 输入状态（高基数标签）
     */
    INPUT_STATE("hello.agent.state.input"),

    /**
     * 输出状态（高基数标签）
     */
    OUTPUT_STATE("hello.agent.state.output");

    private final String value;

    ObservationMetricAttributes(String value) {
        this.value = value;
    }

    /**
     * 获取属性值
     *
     * @return 属性值
     */
    public String value() {
        return value;
    }
}
