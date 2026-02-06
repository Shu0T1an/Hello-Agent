package cn.ts.graph;

import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.edge.Edge;
import cn.ts.graph.node.Node;
import cn.ts.graph.observation.GraphLifecycleListener;
import cn.ts.graph.state.State;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 图配置 record
 * <p>
 * 统一管理图的结构数据和配置，避免在多个类之间重复传递数据
 * </p>
 *
 * @author tianshuo
 */
public record GraphConfig(
        Map<String, Node> nodes,
        List<Edge> edges,
        String entryPoint,
        Supplier<State> stateInitializer,
        CheckpointManager checkpointManager,
        List<GraphLifecycleListener> lifecycleListeners
) {
    /**
     * Compact constructor 用于数据验证和防御性复制
     */
    public GraphConfig {
        // 防御性复制，确保不可变性
        nodes = Map.copyOf(nodes);
        edges = List.copyOf(edges);
        lifecycleListeners = lifecycleListeners != null ? List.copyOf(lifecycleListeners) : List.of();
        Objects.requireNonNull(entryPoint, "Entry point cannot be null");
    }

    /**
     * 创建不带 checkpoint manager 和监听器的配置
     */
    public GraphConfig(Map<String, Node> nodes, List<Edge> edges, String entryPoint, Supplier<State> stateInitializer) {
        this(nodes, edges, entryPoint, stateInitializer, null, List.of());
    }

    /**
     * 创建不带监听器的配置
     */
    public GraphConfig(Map<String, Node> nodes, List<Edge> edges, String entryPoint,
                      Supplier<State> stateInitializer, CheckpointManager checkpointManager) {
        this(nodes, edges, entryPoint, stateInitializer, checkpointManager, List.of());
    }

    /**
     * 验证图结构是否完整
     *
     * @throws GraphException 如果图结构不完整
     */
    public void validate() {
        if (nodes.isEmpty()) {
            throw new GraphException.GraphCompileException("Graph must have at least one node");
        }
        if (!nodes.containsKey(entryPoint)) {
            throw new GraphException.GraphCompileException("Entry point node not found: " + entryPoint);
        }
    }

    /**
     * 创建不可修改的副本
     *
     * @return 不可修改的副本（当前实例已不可变，返回自身）
     */
    public GraphConfig immutableCopy() {
        return this;
    }
}
