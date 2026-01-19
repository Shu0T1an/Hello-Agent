package cn.ts.graph;

import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.checkpoint.StateSnapshot;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.edge.Edge;
import cn.ts.graph.node.Node;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import cn.ts.graph.visualization.MermaidGraphVisualizer;
import cn.ts.graph.visualization.VisualizationConfig;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 编译后的图
 * <p>
 * 包含编译后的节点和边，可以直接执行
 * 参考 Spring AI Alibaba Graph 的 CompiledGraph 设计
 * </p>
 *
 * @author tianshuo
 */
public class CompiledGraph {

    private final Map<String, Node> nodes;
    private final List<Edge> edges;
    private final String entryPoint;
    private final Supplier<State> stateInitializer;
    private final CheckpointManager checkpointManager;

    /**
     * 创建编译后的图
     *
     * @param nodes            节点映射
     * @param edges            边列表
     * @param entryPoint       入口点节点标识
     * @param stateInitializer 状态初始化器
     * @param checkpointManager 检查点管理器
     */
    CompiledGraph(Map<String, Node> nodes, List<Edge> edges, String entryPoint,
                  Supplier<State> stateInitializer, CheckpointManager checkpointManager) {
        this.nodes = Map.copyOf(nodes);
        this.edges = List.copyOf(edges);
        this.entryPoint = entryPoint;
        this.stateInitializer = stateInitializer != null ? stateInitializer : MapState::new;
        this.checkpointManager = checkpointManager;
    }

    /**
     * 创建编译后的图（不带检查点管理器）
     *
     * @param nodes            节点映射
     * @param edges            边列表
     * @param entryPoint       入口点节点标识
     * @param stateInitializer 状态初始化器
     */
    CompiledGraph(Map<String, Node> nodes, List<Edge> edges, String entryPoint,
                  Supplier<State> stateInitializer) {
        this(nodes, edges, entryPoint, stateInitializer, null);
    }

    /**
     * 执行图（同步，使用默认配置）
     *
     * @param initialState 初始状态
     * @return 执行结果
     */
    public GraphResult invoke(Map<String, Object> initialState) {
        GraphRunner runner = new GraphRunner(nodes, edges, entryPoint, stateInitializer);
        return runner.run(initialState);
    }

    /**
     * 执行图（同步，使用自定义配置）
     *
     * @param initialState 初始状态
     * @param config       运行配置
     * @return 执行结果
     */
    public GraphResult invoke(Map<String, Object> initialState, RunnableConfig config) {
        GraphRunner runner = new GraphRunner(nodes, edges, entryPoint, stateInitializer);
        return runner.run(initialState, config);
    }

    /**
     * 流式执行图（响应式，使用默认配置）
     * <p>
     * 返回一个响应式流，支持实时接收节点执行结果
     * 适用于 SSE 场景和流式 LLM 响应
     * </p>
     *
     * @param initialState 初始状态
     * @return Flux 流，发射节点执行结果
     *         - 普通节点：GraphResponse<NodeOutput>（状态更新）
     *         - 流式节点：GraphResponse<NodeOutput>（单个流元素，如 String token）
     */
    public Flux<GraphResponse<NodeOutput>> stream(Map<String, Object> initialState) {
        GraphRunner runner = new GraphRunner(nodes, edges, entryPoint, stateInitializer);
        return runner.runStream(initialState);
    }

    /**
     * 流式执行图（响应式，使用自定义配置）
     * <p>
     * 返回一个响应式流，支持实时接收节点执行结果
     * 适用于 SSE 场景和流式 LLM 响应
     * </p>
     *
     * @param initialState 初始状态
     * @param config       运行配置
     * @return Flux 流，发射节点执行结果
     *         - 普通节点：GraphResponse<NodeOutput>（状态更新）
     *         - 流式节点：GraphResponse<NodeOutput>（单个流元素，如 String token）
     */
    public Flux<GraphResponse<NodeOutput>> stream(
            Map<String, Object> initialState, RunnableConfig config) {
        GraphRunner runner = new GraphRunner(nodes, edges, entryPoint, stateInitializer);
        return runner.runStream(initialState, config);
    }

    /**
     * 获取所有节点
     *
     * @return 节点映射
     */
    public Map<String, Node> getNodes() {
        return nodes;
    }

    /**
     * 获取所有边
     *
     * @return 边列表
     */
    public List<Edge> getEdges() {
        return edges;
    }

    /**
     * 获取入口点节点标识
     *
     * @return 入口点节点标识
     */
    public String getEntryPoint() {
        return entryPoint;
    }

    /**
     * 将图转换为 Mermaid 格式的流程图
     *
     * @return Mermaid 格式的流程图字符串
     */
    public String toMermaidDiagram() {
        return new MermaidGraphVisualizer().visualize(this);
    }

    /**
     * 将图转换为 Mermaid 格式的流程图（带配置）
     *
     * @param config 可视化配置
     * @return Mermaid 格式的流程图字符串
     */
    public String toMermaidDiagram(VisualizationConfig config) {
        return new MermaidGraphVisualizer().visualize(this, config);
    }

    /**
     * 获取最新状态
     * <p>
     * 需要配置 CheckpointManager 才能使用
     * </p>
     *
     * @param threadId 会话ID
     * @return 状态快照的 Optional
     */
    public Optional<StateSnapshot> getState(String threadId) {
        if (checkpointManager == null) {
            throw new IllegalStateException("CheckpointManager not configured. Use StateGraph.setCheckpointManager() to enable checkpoint functionality.");
        }
        return checkpointManager.getState(threadId);
    }

    /**
     * 获取状态历史
     * <p>
     * 需要配置 CheckpointManager 才能使用
     * </p>
     *
     * @param threadId 会话ID
     * @return 状态历史列表
     */
    public List<StateSnapshot> getStateHistory(String threadId) {
        if (checkpointManager == null) {
            throw new IllegalStateException("CheckpointManager not configured. Use StateGraph.setCheckpointManager() to enable checkpoint functionality.");
        }
        return checkpointManager.getStateHistory(threadId);
    }

    /**
     * 更新状态
     * <p>
     * 需要配置 CheckpointManager 才能使用
     * </p>
     *
     * @param threadId 会话ID
     * @param updates  状态更新
     * @param asNode   作为哪个节点更新
     */
    public void updateState(String threadId, Map<String, Object> updates, String asNode) {
        if (checkpointManager == null) {
            throw new IllegalStateException("CheckpointManager not configured. Use StateGraph.setCheckpointManager() to enable checkpoint functionality.");
        }
        checkpointManager.updateState(threadId, updates, asNode);
    }

    /**
     * 删除会话的所有检查点
     * <p>
     * 需要配置 CheckpointManager 才能使用
     * </p>
     *
     * @param threadId 会话ID
     */
    public void deleteThread(String threadId) {
        if (checkpointManager == null) {
            throw new IllegalStateException("CheckpointManager not configured. Use StateGraph.setCheckpointManager() to enable checkpoint functionality.");
        }
        checkpointManager.deleteThread(threadId);
    }

    /**
     * 检查是否配置了检查点管理器
     *
     * @return true 如果配置了检查点管理器
     */
    public boolean hasCheckpointManager() {
        return checkpointManager != null;
    }

    @Override
    public String toString() {
        return "CompiledGraph{" +
                "nodes=" + nodes.keySet() +
                ", edges=" + edges.size() +
                ", entryPoint='" + entryPoint + '\'' +
                ", hasCheckpointManager=" + (checkpointManager != null) +
                '}';
    }
}
