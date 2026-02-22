package cn.ts.graph;

import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.edge.Edge;
import cn.ts.graph.execution.NodeExecutor;
import cn.ts.graph.hook.JumpTo;
import cn.ts.graph.node.AsyncNodeActionWithConfig;
import cn.ts.graph.node.InterruptableAction;
import cn.ts.graph.node.Node;
import cn.ts.graph.node.NodeActionWithConfig;
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

        // 调用 onStart 监听器
        this.config.lifecycleListeners().forEach(listener -> {
            try {
                listener.onStart(this.config.entryPoint(), initialState, config);
            } catch (Exception e) {
                logger.warn("Error in start listener", e);
            }
        });

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

                // 更新状态（移除 jump_to 和 feedbackData 避免污染状态）
                Map<String, Object> cleanUpdates = new HashMap<>(result.stateUpdates());
                cleanUpdates.remove("jump_to");
                cleanUpdates.remove("feedbackData");
                state.merge(cleanUpdates);

                // 找到下一个节点
                currentNodeId = findNextNode(currentNodeId, state, config);
                iteration++;
            }

            Instant endTime = Instant.now();
            GraphResult result = GraphResult.success(state, executionHistory, startTime, endTime);
            if (config.onComplete() != null) {
                config.onComplete().accept(result);
            }

            // 调用 onComplete 监听器
            this.config.lifecycleListeners().forEach(listener -> {
                try {
                    listener.onComplete(GraphConstants.END, state.data(), config);
                } catch (Exception e) {
                    logger.warn("Error in complete listener", e);
                }
            });

            return result;

        } catch (Exception e) {
            Instant endTime = Instant.now();
            GraphResult result = GraphResult.failure(e, executionHistory, startTime, endTime);
            if (config.onError() != null) {
                config.onError().accept(result);
            }

            // 调用 onError 监听器
            this.config.lifecycleListeners().forEach(listener -> {
                try {
                    listener.onError(null, initialState, e, config);
                } catch (Exception ex) {
                    logger.warn("Error in error listener", ex);
                }
            });

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

        // 优先使用 config.startNode()，如果没有则使用 entryPoint
        String startNode = config.startNode() != null
                ? config.startNode()
                : this.config.entryPoint();

        logger.debug("runStream: startNode={}, entryPoint={}, config.startNode()={}",
                startNode, this.config.entryPoint(), config.startNode());

        return runStreamInternal(initialState, config, startNode);
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

        // 调用 onStart 监听器
        logger.info("Calling lifecycle listeners for stream start, listeners count: {}", this.config.lifecycleListeners().size());
        this.config.lifecycleListeners().forEach(listener -> {
            try {
                logger.info("Calling onStart listener: {}", listener.getClass().getSimpleName());
                listener.onStart(startNode, initialState, config);
            } catch (Exception e) {
                logger.warn("Error in start listener (stream)", e);
            }
        });

        // 创建上下文（使用 stateInitializer 和 checkpointManager）
        Supplier<State> stateInitializer = this.config.stateInitializer() != null
                ? this.config.stateInitializer()
                : MapState::new;
        GraphRunnerContext context = GraphRunnerContext.create(
                initialState,
                config,
                stateInitializer,
                this.config.checkpointManager()
        );
        context.setCurrentNodeId(startNode);

        // 使用 defer() 实现响应式递归
        return executeNodeStream(context)
                .concatWith(Flux.defer(() -> executeNextStream(context)))
                .doOnComplete(() -> {
                    // 调用 onComplete 监听器
                    this.config.lifecycleListeners().forEach(listener -> {
                        try {
                            listener.onComplete(GraphConstants.END, context.getOverallState().data(), config);
                        } catch (Exception e) {
                            logger.warn("Error in complete listener (stream)", e);
                        }
                    });
                })
                .doOnError(error -> {
                    // 调用 onError 监听器
                    this.config.lifecycleListeners().forEach(listener -> {
                        try {
                            listener.onError(null, initialState, error, config);
                        } catch (Exception e) {
                            logger.warn("Error in error listener (stream)", e);
                        }
                    });
                });
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

        // 节点执行前调用监听器
        long startTime = System.currentTimeMillis();
        this.config.lifecycleListeners().forEach(listener -> {
            try {
                listener.before(nodeId, context.getOverallState().data(), context.getConfig(), startTime);
            } catch (Exception e) {
                logger.warn("Error in before listener for node: {} (stream)", nodeId, e);
            }
        });

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
                .doOnComplete(() -> {
                    // 节点流式执行完成后调用监听器（记录整个流式持续时间）
                    long endTime = System.currentTimeMillis();
                    this.config.lifecycleListeners().forEach(listener -> {
                        try {
                            listener.after(nodeId, context.getOverallState().data(), context.getConfig(), endTime);
                        } catch (Exception e) {
                            logger.warn("Error in after listener for node: {} (stream)", nodeId, e);
                        }
                    });
                })
                .doOnError(error -> {
                    if (context.getConfig().onError() != null) {
                        context.getConfig().onError().accept(GraphResult.failure(error, List.of(),
                                java.time.Instant.now(), java.time.Instant.now()));
                    }

                    // 节点错误时调用监听器
                    this.config.lifecycleListeners().forEach(listener -> {
                        try {
                            listener.onError(nodeId, context.getOverallState().data(), error, context.getConfig());
                        } catch (Exception e) {
                            logger.warn("Error in error listener for node: {} (stream)", nodeId, e);
                        }
                    });
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

        List<GraphResponse<?>> history = context.getHistory();
        if (!history.isEmpty()) {
            GraphResponse<?> lastResponse = history.get(history.size() - 1);
            if (lastResponse.isInterruption()) {
                logger.info("Execution interrupted at node: {}, stop scheduling next node", currentNodeId);
                return Flux.empty();
            }
        }

        // 查找下一个节点
        String nextNodeId = findNextNode(currentNodeId, currentState, context.getConfig());

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

        logger.info("Executing next node: currentNodeId={}, nextNodeId={} iteration={}", currentNodeId, nextNodeId, context.getIteration());
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

        // 节点执行前调用监听器
        this.config.lifecycleListeners().forEach(listener -> {
            try {
                listener.before(nodeId, state.data(), config, nodeStartTime.toEpochMilli());
            } catch (Exception e) {
                logger.warn("Error in before listener for node: {}", nodeId, e);
            }
        });

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

            // 使用 NodeActionWithConfig 执行节点动作
            // 优先检查新接口，包装旧接口
            NodeActionWithConfig actionWithConfig;
            if (node.action() instanceof NodeActionWithConfig) {
                actionWithConfig = (NodeActionWithConfig) node.action();
            } else {
                actionWithConfig = NodeActionWithConfig.from(node.action());
            }

            Map<String, Object> updates = actionWithConfig.apply(state, config);
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

            // 节点执行后调用监听器
            Instant finalNodeEndTime = nodeEndTime;
            this.config.lifecycleListeners().forEach(listener -> {
                try {
                    listener.after(nodeId, state.data(), config, finalNodeEndTime.toEpochMilli());
                } catch (Exception e) {
                    logger.warn("Error in after listener for node: {}", nodeId, e);
                }
            });

            return new NodeExecutionResult(stateUpdates, execution, null);

        } catch (Exception e) {
            Instant nodeEndTime = Instant.now();
            GraphResult.NodeExecution execution = new GraphResult.NodeExecution(
                    nodeId, nodeStartTime, nodeEndTime, e);

            // 节点错误时调用监听器
            Instant finalNodeEndTime = nodeEndTime;
            this.config.lifecycleListeners().forEach(listener -> {
                try {
                    listener.onError(nodeId, state.data(), e, config);
                } catch (Exception ex) {
                    logger.warn("Error in error listener for node: {}", nodeId, ex);
                }
            });

            return new NodeExecutionResult(stateUpdates, execution, e);
        }
    }

    /**
     * 查找下一个节点
     * <p>
     * 对于没有出边的节点，返回 {@link GraphConstants#END} 表示正常结束执行
     * 支持 JumpTo 路由控制
     * </p>
     *
     * @param currentNodeId 当前节点ID
     * @param state         当前状态
     * @param config        运行配置
     * @return 下一个节点ID，如果没有下一个节点则返回 {@link GraphConstants#END}
     * @throws GraphException.EdgeConfigurationException 如果条件边的路由值没有对应的目标节点
     */
    private String findNextNode(String currentNodeId, State state, RunnableConfig config) {
        // 优先检查 JumpTo 跳转（来自配置）
        if (config.jumpTo() != null) {
            logger.debug("使用 config.jumpTo() 跳转: {}", config.jumpTo());
            return jumpToToNode(config.jumpTo());
        }

        // 检查 state 中的 jump_to（来自节点执行结果，如 HumanInTheLoopHook）
        Optional<JumpTo> stateJumpTo = state.value("jump_to");
        if (stateJumpTo.isPresent()) {
            logger.debug("使用 state.jump_to 跳转: {}", stateJumpTo.get());
            // Consume one-shot jump_to to avoid persistent re-routing loops in stream mode.
            state.update("jump_to", null);
            return jumpToToNode(stateJumpTo.get());
        }

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
     * 将 JumpTo 枚举转换为节点ID
     *
     * @param jumpTo 跳转目标
     * @return 节点ID
     */
    private String jumpToToNode(JumpTo jumpTo) {
        return switch (jumpTo) {
            case END -> GraphConstants.END;
            case MODEL -> GraphConstants.AGENT_MODEL;
            case TOOL -> GraphConstants.AGENT_TOOL;
        };
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
