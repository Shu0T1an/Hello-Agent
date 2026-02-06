package cn.ts.graph.observation.metric;

/**
 * 可观测性指标名称定义
 * <p>
 * 定义所有用于 Micrometer Observation 的指标名称
 * </p>
 *
 * @author tianshuo
 */
public class ObservationMetricNames {

    /**
     * 图执行指标
     */
    public static final String GRAPH_EXECUTION = "hello.agent.graph.execution";

    /**
     * 节点执行指标
     */
    public static final String GRAPH_NODE = "hello.agent.graph.node";

    /**
     * 边执行指标
     */
    public static final String GRAPH_EDGE = "hello.agent.graph.edge";

    private ObservationMetricNames() {
        // 工具类，禁止实例化
    }
}
