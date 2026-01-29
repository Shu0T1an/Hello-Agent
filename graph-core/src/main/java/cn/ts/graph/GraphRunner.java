package cn.ts.graph;

import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.edge.Edge;
import cn.ts.graph.node.Node;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/**
 * 图执行器（重构版）
 * <p>
 * 使用 GraphConfig 统一管理图数据，减少数据重复传递
 * </p>
 *
 * @author tianshuo
 */
public class GraphRunner {

    private static final Logger logger = LoggerFactory.getLogger(GraphRunner.class);

    private final GraphConfig config;
    private final NodeExecutor nodeExecutor;

    /**
     * 创建图执行器（使用 GraphConfig）
     *
     * @param config 图配置
     */
    public GraphRunner(GraphConfig config) {
        this.config = Objects.requireNonNull(config, "GraphConfig cannot be null");
        this.nodeExecutor = NodeExecutor.create();
    }

    /**
     * 创建图执行器（向后兼容构造函数）
     *
     * @param nodes            节点映射
     * @param edges            边列表
     * @param entryPoint       入口点节点标识
     * @param stateInitializer 状态初始化器
     */
    public GraphRunner(Map<String, Node> nodes, List<Edge> edges, String entryPoint, Supplier<State> stateInitializer) {
        this(new GraphConfig(nodes, edges, entryPoint, stateInitializer));
    }

    /**
     * 执行图（使用默认配置）
     *
     * @param initialState 初始状态
     * @return 执行结果
     */
    public GraphResult run(Map<String, Object> initialState) {
        return run(initialState, RunnableConfig.defaultConfig());
    }

    /**
     * 执行图（使用自定义配置）
     *
     * @param initialState 初始状态
     * @param config       运行配置
     * @return 执行结果
     */
    public GraphResult run(Map<String, Object> initialState, RunnableConfig config) {
        Instant startTime = Instant.now();
        List<GraphResult.NodeExecution> executionHistory = new ArrayList<>();

        try {
            // 使用状态初始化器创建状态
            Supplier<State> stateInitializer = this.config.stateInitializer() != null
                    ? this.config.stateInitializer()
                    : MapState::new;
            State state = stateInitializer.get();
            if (initialState != null && !initialState.isEmpty()) {
                state.merge(initialState);
            }
            String currentNodeId = this.config.entryPoint();
            int iteration = 0;
            int maxIter = config.maxIterations();

            while (currentNodeId != null && !GraphConstants.END.equals(currentNodeId)) {
                // 检查超时
                if (config.timeout() != null) {
                    Duration elapsed = Duration.between(startTime, Instant.now());
                    if (elapsed.compareTo(config.timeout()) > 0) {
                        throw new GraphException("Execution timeout exceeded: " + config.timeout());
                    }
                }

                if (iteration >= maxIter) {
                    throw new GraphException("Max iterations exceeded: " + maxIter);
                }

                // 执行节点（传递配置）
                NodeExecutionResult result = executeNode(currentNodeId, state, config);
                executionHistory.add(result.execution());

                if (result.hasError()) {
                    if (config.onError() != null) {
                        GraphResult errorResult = GraphResult.failure(result.error(), executionHistory, startTime, Instant.now());
                        config.onError().accept(errorResult);
                    }
                    if (config.interruptOnError()) {
                        Instant endTime = Instant.now();
                        return GraphResult.failure(result.error(), executionHistory, startTime, endTime);
                    }
                }

                // 更新状态
                state.merge(result.stateUpdates());

                // 找到下一个节点
                currentNodeId = findNextNode(currentNodeId, state);
                iteration++;
            }

            Instant endTime = Instant.now();
            GraphResult result = GraphResult.success(state, executionHistory, startTime, endTime);
            if (config.onComplete() != null) {
                config.onComplete().accept(result);
            }
            return result;

        } catch (Exception e) {
            Instant endTime = Instant.now();
            GraphResult result = GraphResult.failure(e, executionHistory, startTime, endTime);
            if (config.onError() != null) {
                config.onError().accept(result);
            }
            return result;
        }
    }

    /**
     * 响应式执行图（使用默认配置）
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
    public Flux<GraphResponse<NodeOutput>> runStream(Map<String, Object> initialState) {
        return runStream(initialState, RunnableConfig.defaultConfig());
    }

    /**
     * 响应式执行图（使用自定义配置）
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
    public Flux<GraphResponse<NodeOutput>> runStream(
            Map<String, Object> initialState, RunnableConfig config) {

        return runStreamInternal(initialState, config, this.config.entryPoint());
    }

    /**
     * 响应式执行图的内部实现
     * <p>
     * 使用 Flux.defer() 实现响应式递归，避免栈溢出
     * </p>
     *
     * @param initialState 初始状态
     * @param config       运行配置
     * @param startNode    起始节点
     * @return Flux 流，发射节点执行结果
     */
    private Flux<GraphResponse<NodeOutput>> runStreamInternal(
            Map<String, Object> initialState, RunnableConfig config, String startNode) {

        // 创建上下文（使用 stateInitializer）
        Supplier<State> stateInitializer = this.config.stateInitializer() != null
                ? this.config.stateInitializer()
                : MapState::new;
        GraphRunnerContext context = GraphRunnerContext.create(initialState, config, stateInitializer);
        context.setCurrentNodeId(startNode);

        // 使用 defer() 实现响应式递归
        return executeNodeStream(context)
                .concatWith(Flux.defer(() -> executeNextStream(context)));
    }

    /**
     * 执行单个节点（流式）
     *
     * @param context 执行上下文
     * @return Flux 流，发射节点执行结果
     */
    private Flux<GraphResponse<NodeOutput>> executeNodeStream(GraphRunnerContext context) {
        String nodeId = context.getCurrentNodeId();

        // 检查是否结束
        if (nodeId == null || GraphConstants.END.equals(nodeId)) {
            return Flux.empty();
        }

        // 检查迭代次数
        if (context.getIteration() >= context.getConfig().maxIterations()) {
            return Flux.error(new GraphException("Max iterations exceeded: " + context.getConfig().maxIterations()));
        }

        Node node = this.config.nodes().get(nodeId);
        if (node == null) {
            return Flux.error(new GraphException.NodeNotFoundException(nodeId));
        }

        // 触发节点开始回调
        if (context.getConfig().onNodeStart() != null) {
            context.getConfig().onNodeStart().accept(
                    new GraphResult.NodeExecution(nodeId, java.time.Instant.now(), java.time.Instant.now()));
        }

        // 执行节点
        return nodeExecutor.execute(node, context)
                .doOnNext(response -> {
                    // 添加到历史
                    context.addToHistory(response);

                    // 触发节点完成回调
                    if (context.getConfig().onNodeComplete() != null && response.isComplete()) {
                        context.getConfig().onNodeComplete().accept(
                                new GraphResult.NodeExecution(nodeId, java.time.Instant.now(), java.time.Instant.now()));
                    }
                })
                .doOnError(error -> {
                    if (context.getConfig().onError() != null) {
                        context.getConfig().onError().accept(GraphResult.failure(error, List.of(),
                                java.time.Instant.now(), java.time.Instant.now()));
                    }
                });
    }

    /**
     * 执行下一个节点（响应式递归）
     *
     * @param context 当前上下文
     * @return Flux 流，发射后续节点执行结果
     */
    private Flux<GraphResponse<NodeOutput>> executeNextStream(GraphRunnerContext context) {
        String currentNodeId = context.getCurrentNodeId();
        State currentState = context.getOverallState();

        // 查找下一个节点
        String nextNodeId = findNextNode(currentNodeId, currentState);

        logger.debug("executeNextStream: currentNodeId={}, nextNodeId={}", currentNodeId, nextNodeId);

        // 没有下一个节点，结束
        if (nextNodeId == null || GraphConstants.END.equals(nextNodeId)) {
            logger.info("执行完成: currentNodeId={}, 发送 GRAPH_COMPLETED 事件", currentNodeId);
            // 触发完成回调
            if (context.getConfig().onComplete() != null) {
                context.getConfig().onComplete().accept(GraphResult.success(currentState,
                        new ArrayList<>(), java.time.Instant.now(), java.time.Instant.now()));
            }
            // 发送图完成事件
            return Flux.just(GraphResponse.complete());
        }

        // 创建下一个迭代的上下文
        GraphRunnerContext nextContext = context.forNextIteration(nextNodeId);

        // 递归执行
        return executeNodeStream(nextContext)
                .concatWith(Flux.defer(() -> executeNextStream(nextContext)));
    }

    /**
     * 执行单个节点
     *
     * @param nodeId 节点ID
     * @param state  当前状态
     * @param config 运行配置
     * @return 节点执行结果
     */
    private NodeExecutionResult executeNode(String nodeId, State state, RunnableConfig config) {
        Instant nodeStartTime = Instant.now();
        Map<String, Object> stateUpdates = new HashMap<>();

        try {
            Node node = this.config.nodes().get(nodeId);
            if (node == null) {
                throw new GraphException.NodeNotFoundException(nodeId);
            }

            // 触发节点开始回调
            if (config.onNodeStart() != null) {
                GraphResult.NodeExecution startExec = new GraphResult.NodeExecution(nodeId, nodeStartTime, Instant.now());
                config.onNodeStart().accept(startExec);
            }

            // 执行节点动作
            Map<String, Object> updates = node.action().apply(state);
            if (updates != null) {
                stateUpdates.putAll(updates);
            }

            Instant nodeEndTime = Instant.now();
            GraphResult.NodeExecution execution = new GraphResult.NodeExecution(
                    nodeId, nodeStartTime, nodeEndTime);

            // 触发节点完成回调
            if (config.onNodeComplete() != null) {
                config.onNodeComplete().accept(execution);
            }

            return new NodeExecutionResult(stateUpdates, execution, null);

        } catch (Exception e) {
            Instant nodeEndTime = Instant.now();
            GraphResult.NodeExecution execution = new GraphResult.NodeExecution(
                    nodeId, nodeStartTime, nodeEndTime, e);
            return new NodeExecutionResult(stateUpdates, execution, e);
        }
    }

    /**
     * 查找下一个节点
     * <p>
     * 对于没有出边的节点，返回 {@link GraphConstants#END} 表示正常结束执行
     * </p>
     *
     * @param currentNodeId 当前节点ID
     * @param state         当前状态
     * @return 下一个节点ID，如果没有下一个节点则返回 {@link GraphConstants#END}
     * @throws GraphException.EdgeConfigurationException 如果条件边的路由值没有对应的目标节点
     */
    private String findNextNode(String currentNodeId, State state) {
        for (Edge edge : this.config.edges()) {
            if (edge.from().equals(currentNodeId)) {
                if (edge.isNormal()) {
                    return edge.to();
                } else if (edge.isConditional()) {
                    String conditionValue = edge.action().route(state);
                    String targetNode = edge.routeMapping().get(conditionValue);
                    if (targetNode == null) {
                        throw new GraphException.EdgeConfigurationException(
                                String.format("No route mapping for condition value '%s' from node '%s'. " +
                                        "Available routes: %s", conditionValue, currentNodeId, edge.routeMapping().keySet()));
                    }
                    return targetNode;
                }
            }
        }
        // 没有找到下一个节点，正常结束执行
        return GraphConstants.END;
    }

    /**
     * 节点执行结果
     */
    private record NodeExecutionResult(
            Map<String, Object> stateUpdates,
            GraphResult.NodeExecution execution,
            Throwable error
    ) {
        boolean hasError() {
            return error != null;
        }
    }
}
