package cn.ts.graph.node;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 节点定义类
 * <p>
 * 封装节点的标识和动作逻辑
 * 支持 InterruptableAction 类型的节点
 * </p>
 *
 * @author tianshuo
 */
public class Node {

    private final String id;
    private final NodeAction action;
    private final String description;
    private final InterruptableAction interruptableAction; // 保存原始的可中断动作（如果有）

    private Node(String id, NodeAction action, String description, InterruptableAction interruptableAction) {
        this.id = Objects.requireNonNull(id, "Node id cannot be null");
        this.action = Objects.requireNonNull(action, "Node action cannot be null");
        this.description = description;
        this.interruptableAction = interruptableAction;
    }

    /**
     * 创建一个节点
     *
     * @param id     节点标识
     * @param action 节点动作
     * @return 节点对象
     */
    public static Node of(String id, NodeAction action) {
        return new Node(id, action, null, null);
    }

    /**
     * 创建一个可中断节点
     * <p>
     * InterruptableAction 是独立接口，负责中断检测
     * 节点动作通过 AsyncNodeActionWithConfig 接口执行（如果实现）
     * </p>
     *
     * @param id           节点标识
     * @param interruptable 可中断动作
     * @return 节点对象
     */
    public static Node ofInterruptable(String id, InterruptableAction interruptable) {
        // 创建 NodeAction 包装器
        // 如果 interruptable 同时实现了 AsyncNodeActionWithConfig，使用它
        // 否则返回空 Map
        NodeAction wrapper = state -> {
            if (interruptable instanceof AsyncNodeActionWithConfig actionWithConfig) {
                // 使用默认配置调用异步方法并等待结果
                try {
                    return actionWithConfig.applyAsync(state, cn.ts.graph.config.RunnableConfig.defaultConfig()).get();
                } catch (Exception e) {
                    throw new RuntimeException("InterruptableAction execution failed", e);
                }
            }
            // 如果没有实现 AsyncNodeActionWithConfig，返回空 Map
            return Map.of();
        };
        return new Node(id, wrapper, null, interruptable);
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
        return new Node(id, action, description, null);
    }

    /**
     * 检查节点是否是可中断的
     *
     * @return true 如果节点是可中断的
     */
    public boolean isInterruptable() {
        return interruptableAction != null;
    }

    /**
     * 获取可中断动作（如果存在）
     *
     * @return 可中断动作，如果节点不可中断则返回 null
     */
    public InterruptableAction interruptableAction() {
        return interruptableAction;
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
