package cn.ts.graph.observation.graph;

import io.micrometer.observation.Observation;

import java.util.Map;

/**
 * 图级别观测上下文
 * <p>
 * 封装图执行过程中的观测上下文信息，用于 Micrometer Observation API
 * </p>
 *
 * @author tianshuo
 */
public class GraphObservationContext extends Observation.Context {

    private final String graphName;
    private final Map<String, Object> inputState;
    private Map<String, Object> outputState;

    /**
     * 创建图观测上下文
     *
     * @param graphName   图名称
     * @param inputState  输入状态
     */
    public GraphObservationContext(String graphName, Map<String, Object> inputState) {
        this.graphName = graphName;
        this.inputState = inputState;
    }

    /**
     * 获取图名称
     *
     * @return 图名称
     */
    public String getGraphName() {
        return graphName;
    }

    /**
     * 获取输入状态
     *
     * @return 输入状态
     */
    public Map<String, Object> getInputState() {
        return inputState;
    }

    /**
     * 获取输出状态
     *
     * @return 输出状态
     */
    public Map<String, Object> getOutputState() {
        return outputState;
    }

    /**
     * 设置输出状态
     *
     * @param outputState 输出状态
     */
    public void setOutputState(Map<String, Object> outputState) {
        this.outputState = outputState;
    }
}
