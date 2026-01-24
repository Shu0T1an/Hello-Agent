package cn.ts.graph;

import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.edge.Edge;
import cn.ts.graph.edge.EdgeAction;
import cn.ts.graph.node.Node;
import cn.ts.graph.node.NodeAction;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import cn.ts.graph.visualization.MermaidGraphVisualizer;
import cn.ts.graph.visualization.VisualizationConfig;

import java.util.*;
import java.util.function.Supplier;

/**
 * 图构建器
 * <p>
 * 用于定义图的节点和边，提供流畅的 DSL 风格 API
 * 参考 Spring AI Alibaba Graph 的 StateGraph 设计
 * </p>
 *
 * @author tianshuo
 */
public class StateGraph {

    private final Map<String, Node> nodes;
    private final List<Edge> edges;
    private String entryPoint;
    private Supplier<State> stateInitializer = MapState::new;
    private CheckpointManager checkpointManager;

    /**
     * 创建一个空的图构建器
     */
    public StateGraph() {
        this.nodes = new LinkedHashMap<>();
        this.edges = new ArrayList<>();
        this.entryPoint = null;
    }

    /**
     * 添加一个节点
     *
     * @param id     节点标识
     * @param action 节点动作
     * @return 当前图构建器，支持链式调用
     */
    public StateGraph addNode(String id, NodeAction action) {
        if (id == null || id.trim().isEmpty()) {
            throw new GraphException.EdgeConfigurationException("Node id cannot be null or empty");
        }
        if (nodes.containsKey(id)) {
            throw new GraphException.EdgeConfigurationException("Node already exists: " + id);
        }
        nodes.put(id, Node.of(id, action));
        return this;
    }

    /**
     * 添加一个带描述的节点
     *
     * @param id          节点标识
     * @param action      节点动作
     * @param description 节点描述
     * @return 当前图构建器，支持链式调用
     */
    public StateGraph addNode(String id, NodeAction action, String description) {
        if (id == null || id.trim().isEmpty()) {
            throw new GraphException.EdgeConfigurationException("Node id cannot be null or empty");
        }
        if (nodes.containsKey(id)) {
            throw new GraphException.EdgeConfigurationException("Node already exists: " + id);
        }
        nodes.put(id, Node.of(id, action, description));
        return this;
    }

    /**
     * 添加一条普通边
     *
     * @param from 源节点标识
     * @param to   目标节点标识
     * @return 当前图构建器，支持链式调用
     */
    public StateGraph addEdge(String from, String to) {
        validateNodeExists(from);
        validateNodeExists(to);
        edges.add(Edge.of(from, to));

        // 如果是从 START 出发的边，设置入口点
        if (GraphConstants.START.equals(from)) {
            this.entryPoint = to;
        }

        return this;
    }

    /**
     * 添加一条条件边
     *
     * @param from         源节点标识
     * @param action       路由动作
     * @param routeMapping 路由映射：条件值 -> 目标节点标识
     * @return 当前图构建器，支持链式调用
     */
    public StateGraph addConditionalEdge(String from, EdgeAction action, Map<String, String> routeMapping) {
        validateNodeExists(from);
        validateRouteMapping(routeMapping);
        edges.add(Edge.conditional(from, action, routeMapping));

        // 如果是从 START 出发的边，设置入口点（取第一个路由目标）
        if (GraphConstants.START.equals(from) && !routeMapping.isEmpty()) {
            this.entryPoint = routeMapping.values().iterator().next();
        }

        return this;
    }

    /**
     * 设置状态初始化器
     * <p>
     * 用于自定义状态的创建和初始化，例如注册键的合并策略
     * </p>
     *
     * @param stateInitializer 状态初始化器
     * @return 当前图构建器，支持链式调用
     */
    public StateGraph setStateInitializer(Supplier<State> stateInitializer) {
        if (stateInitializer != null) {
            this.stateInitializer = stateInitializer;
        }
        return this;
    }

    /**
     * 设置检查点管理器
     * <p>
     * 用于启用检查点功能，支持状态持久化和恢复
     * </p>
     *
     * @param checkpointManager 检查点管理器
     * @return 当前图构建器，支持链式调用
     */
    public StateGraph setCheckpointManager(CheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
        return this;
    }

    /**
     * 编译图为可执行的结构
     * <p>
     * 使用 GraphConfig 封装图数据，然后创建 CompiledGraph
     * </p>
     *
     * @return 编译后的图
     */
    public CompiledGraph compile() {
        validateGraphStructure();
        GraphConfig graphConfig = new GraphConfig(
                new HashMap<>(nodes),
                new ArrayList<>(edges),
                entryPoint,
                stateInitializer,
                checkpointManager
        );
        return new CompiledGraph(graphConfig);
    }

    /**
     * 验证节点是否存在
     */
    private void validateNodeExists(String nodeId) {
        if (!GraphConstants.START.equals(nodeId) &&
                !GraphConstants.END.equals(nodeId) &&
                !nodes.containsKey(nodeId)) {
            throw new GraphException.NodeNotFoundException(nodeId);
        }
    }

    /**
     * 验证路由映射中的所有目标节点都存在
     */
    private void validateRouteMapping(Map<String, String> routeMapping) {
        if (routeMapping == null || routeMapping.isEmpty()) {
            throw new GraphException.EdgeConfigurationException("Route mapping cannot be null or empty");
        }
        for (String target : routeMapping.values()) {
            validateNodeExists(target);
        }
    }

    /**
     * 验证图结构
     */
    private void validateGraphStructure() {
        if (nodes.isEmpty()) {
            throw new GraphException.GraphCompileException("Graph must have at least one node");
        }
        if (entryPoint == null) {
            throw new GraphException.GraphCompileException("Graph must have an entry point (add edge from START)");
        }
    }

    /**
     * 获取所有节点
     *
     * @return 节点映射的不可修改副本
     */
    public Map<String, Node> getNodes() {
        return Collections.unmodifiableMap(new HashMap<>(nodes));
    }

    /**
     * 获取所有边
     *
     * @return 边列表的不可修改副本
     */
    public List<Edge> getEdges() {
        return Collections.unmodifiableList(new ArrayList<>(edges));
    }

    /**
     * 获取入口点节点标识
     *
     * @return 入口点节点标识，可能为 null
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

    @Override
    public String toString() {
        return "StateGraph{" +
                "nodes=" + nodes.keySet() +
                ", edges=" + edges.size() +
                ", entryPoint='" + entryPoint + '\'' +
                '}';
    }
}
