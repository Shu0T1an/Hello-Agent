package cn.ts.graph;

import cn.ts.graph.node.Node;
import cn.ts.graph.state.State;
import org.springframework.ai.chat.metadata.Usage;

import java.util.Map;
import java.util.Objects;

/**
 * 节点输出包装类
 * <p>
 * 统一所有节点的返回格式，携带元数据
 * 参考 Spring AI Alibaba 的 NodeOutput 设计
 * </p>
 *
 * @author tianshuo
 */
public class NodeOutput {

    private final String nodeId;
    private final Node node;
    private final Object resultValue;
    private final State state;
    private final Usage usage;
    private final Map<String, Object> metadata;

    protected NodeOutput(
            String nodeId,
            Node node,
            Object resultValue,
            State state,
            Usage usage,
            Map<String, Object> metadata) {
        this.nodeId = nodeId;
        this.node = node;
        this.resultValue = resultValue;
        this.state = state;
        this.usage = usage;
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * 创建一个基本的节点输出
     *
     * @param nodeId       节点ID
     * @param resultValue  结果值
     * @param state        执行后的状态快照
     * @return NodeOutput 实例
     */
    public static NodeOutput of(String nodeId, Object resultValue, State state) {
        return new NodeOutput(nodeId, null, resultValue, state, null, Map.of());
    }

    /**
     * 创建一个带 Node 的节点输出
     *
     * @param nodeId       节点ID
     * @param node         节点对象
     * @param resultValue  结果值
     * @param state        执行后的状态快照
     * @return NodeOutput 实例
     */
    public static NodeOutput of(String nodeId, Node node, Object resultValue, State state) {
        return new NodeOutput(nodeId, node, resultValue, state, null, Map.of());
    }

    /**
     * 创建一个带 Usage 的节点输出
     *
     * @param nodeId       节点ID
     * @param resultValue  结果值
     * @param state        执行后的状态快照
     * @param usage        Token 使用统计
     * @return NodeOutput 实例
     */
    public static NodeOutput of(String nodeId, Object resultValue, State state, Usage usage) {
        return new NodeOutput(nodeId, null, resultValue, state, usage, Map.of());
    }

    /**
     * 创建一个带 Node 和 Usage 的节点输出
     *
     * @param nodeId       节点ID
     * @param node         节点对象
     * @param resultValue  结果值
     * @param state        执行后的状态快照
     * @param usage        Token 使用统计
     * @return NodeOutput 实例
     */
    public static NodeOutput of(String nodeId, Node node, Object resultValue, State state, Usage usage) {
        return new NodeOutput(nodeId, node, resultValue, state, usage, Map.of());
    }

    /**
     * 创建一个带元数据的节点输出
     *
     * @param nodeId       节点ID
     * @param resultValue  结果值
     * @param state        执行后的状态快照
     * @param metadata     元数据
     * @return NodeOutput 实例
     */
    public static NodeOutput of(String nodeId, Object resultValue, State state, Map<String, Object> metadata) {
        return new NodeOutput(nodeId, null, resultValue, state, null, metadata);
    }

    /**
     * 创建一个完整的节点输出
     *
     * @param nodeId       节点ID
     * @param resultValue  结果值
     * @param state        执行后的状态快照
     * @param usage        Token 使用统计
     * @param metadata     元数据
     * @return NodeOutput 实例
     */
    public static NodeOutput of(String nodeId, Object resultValue, State state, Usage usage, Map<String, Object> metadata) {
        return new NodeOutput(nodeId, null, resultValue, state, usage, metadata);
    }

    /**
     * 创建一个完整的节点输出（带 Node）
     *
     * @param nodeId       节点ID
     * @param node         节点对象
     * @param resultValue  结果值
     * @param state        执行后的状态快照
     * @param usage        Token 使用统计
     * @param metadata     元数据
     * @return NodeOutput 实例
     */
    public static NodeOutput of(String nodeId, Node node, Object resultValue, State state, Usage usage, Map<String, Object> metadata) {
        return new NodeOutput(nodeId, node, resultValue, state, usage, metadata);
    }

    /**
     * 获取节点ID
     *
     * @return 节点ID
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * 获取节点对象
     *
     * @return 节点对象，可能为 null
     */
    public Node getNode() {
        return node;
    }

    /**
     * 获取结果值
     *
     * @return 结果值
     */
    public Object getResultValue() {
        return resultValue;
    }

    /**
     * 获取执行后的状态快照
     *
     * @return 状态快照
     */
    public State getState() {
        return state;
    }

    /**
     * 获取 Token 使用统计
     *
     * @return Token 使用统计，如果没有则返回 null
     */
    public Usage getUsage() {
        return usage;
    }

    /**
     * 获取元数据
     *
     * @return 元数据
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeOutput that = (NodeOutput) o;
        return Objects.equals(nodeId, that.nodeId)
                && Objects.equals(node, that.node)
                && Objects.equals(resultValue, that.resultValue)
                && Objects.equals(state, that.state)
                && Objects.equals(usage, that.usage)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, node, resultValue, state, usage, metadata);
    }

    @Override
    public String toString() {
        return "NodeOutput{" +
                "nodeId='" + nodeId + '\'' +
                ", node=" + node +
                ", resultValue=" + resultValue +
                ", state=" + state +
                ", usage=" + usage +
                ", metadata=" + metadata +
                '}';
    }
}
