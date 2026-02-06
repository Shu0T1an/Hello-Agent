package cn.ts.graph.observation.node;

import io.micrometer.observation.Observation;

import java.util.Map;

/**
 * 节点级别观测上下文
 * <p>
 * 封装节点执行过程中的观测上下文信息，用于 Micrometer Observation API
 * </p>
 *
 * @author tianshuo
 */
public class NodeObservationContext extends Observation.Context {

    private final String nodeName;
    private final Map<String, Object> inputState;
    private Map<String, Object> outputState;
    private Observation parentObservation;

    /**
     * 创建节点观测上下文
     *
     * @param nodeName   节点名称
     * @param inputState 输入状态
     */
    public NodeObservationContext(String nodeName, Map<String, Object> inputState) {
        this.nodeName = nodeName;
        this.inputState = inputState;
    }

    /**
     * 获取节点名称
     *
     * @return 节点名称
     */
    public String getNodeName() {
        return nodeName;
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

    /**
     * 获取父观测
     *
     * @return 父观测
     */
    public Observation getParentObservation() {
        return parentObservation;
    }

    /**
     * 设置父观测
     *
     * @param parent 父观测
     */
    public void setParentObservation(Observation parent) {
        this.parentObservation = parent;
    }
}
