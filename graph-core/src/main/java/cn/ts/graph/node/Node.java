package cn.ts.graph.node;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 节点定义类
 * <p>
 * 封装节点的标识和动作逻辑
 * 参考 Spring AI Alibaba Graph 的 Node 设计
 * </p>
 *
 * @author tianshuo
 */
public class Node {

    private final String id;
    private final NodeAction action;
    private final String description;

    private Node(String id, NodeAction action, String description) {
        this.id = Objects.requireNonNull(id, "Node id cannot be null");
        this.action = Objects.requireNonNull(action, "Node action cannot be null");
        this.description = description;
    }

    /**
     * 创建一个节点
     *
     * @param id     节点标识
     * @param action 节点动作
     * @return 节点对象
     */
    public static Node of(String id, NodeAction action) {
        return new Node(id, action, null);
    }

    /**
     * 创建一个带描述的节点
     *
     * @param id          节点标识
     * @param action      节点动作
     * @param description 节点描述
     * @return 节点对象
     */
    public static Node of(String id, NodeAction action, String description) {
        return new Node(id, action, description);
    }

    /**
     * 获取节点标识
     *
     * @return 节点标识
     */
    public String id() {
        return id;
    }

    /**
     * 获取节点动作
     *
     * @return 节点动作
     */
    public NodeAction action() {
        return action;
    }

    /**
     * 获取节点描述
     *
     * @return 节点描述，可能为 null
     */
    public String description() {
        return description;
    }

    /**
     * 检查节点是否有描述
     *
     * @return 如果有描述返回 true，否则返回 false
     */
    public boolean hasDescription() {
        return description != null && !description.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return Objects.equals(id, node.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Node{" +
                "id='" + id + '\'' +
                ", description=" + (hasDescription() ? "'" + description + "'" : "null") +
                '}';
    }
}
