package cn.ts.graph;

import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 图执行上下文
 * <p>
 * 用于在图执行过程中传递状态和配置信息。
 * 本类不是线程安全的，应在单个线程中使用。
 * 参考 Spring AI Alibaba 的 GraphRunnerContext 设计
 * </p>
 *
 * @author tianshuo
 */
public class GraphRunnerContext {

    /**
     * 最大历史记录数量，防止长时间运行的图消耗过多内存
     */
    private static final int MAX_HISTORY_SIZE = 1000;

    private String currentNodeId;
    private final State overallState;
    private final RunnableConfig config;
    private final List<GraphResponse<?>> history;
    private final int iteration;
    private final CheckpointManager checkpointManager;

    private GraphRunnerContext(
            String currentNodeId,
            State overallState,
            RunnableConfig config,
            List<GraphResponse<?>> history,
            int iteration,
            CheckpointManager checkpointManager) {
        this.currentNodeId = currentNodeId;
        this.overallState = Objects.requireNonNull(overallState, "OverallState cannot be null");
        this.config = Objects.requireNonNull(config, "Config cannot be null");
        this.history = new ArrayList<>(history != null ? history : List.of());
        this.iteration = iteration;
        this.checkpointManager = checkpointManager; // 可以为 null
    }

    /**
     * 创建一个新的执行上下文
     *
     * @param initialState 初始状态
     * @param config       运行配置
     * @param stateInitializer 状态初始化器
     * @return GraphRunnerContext 实例
     */
    public static GraphRunnerContext create(Map<String, Object> initialState, RunnableConfig config, java.util.function.Supplier<State> stateInitializer) {
        return create(initialState, config, stateInitializer, null);
    }

    /**
     * 创建一个新的执行上下文（带检查点管理器）
     *
     * @param initialState 初始状态
     * @param config       运行配置
     * @param stateInitializer 状态初始化器
     * @param checkpointManager 检查点管理器（可选）
     * @return GraphRunnerContext 实例
     */
    public static GraphRunnerContext create(Map<String, Object> initialState, RunnableConfig config, java.util.function.Supplier<State> stateInitializer, CheckpointManager checkpointManager) {
        State state = stateInitializer != null ? stateInitializer.get() : new MapState();
        if (initialState != null && !initialState.isEmpty()) {
            state.merge(initialState);
        }
        return new GraphRunnerContext(null, state, config, new ArrayList<>(), 0, checkpointManager);
    }

    /**
     * 创建一个带有状态的执行上下文
     *
     * @param state  状态对象
     * @param config 运行配置
     * @return GraphRunnerContext 实例
     */
    public static GraphRunnerContext create(State state, RunnableConfig config) {
        return create(state, config, null);
    }

    /**
     * 创建一个带有状态的执行上下文（带检查点管理器）
     *
     * @param state  状态对象
     * @param config 运行配置
     * @param checkpointManager 检查点管理器（可选）
     * @return GraphRunnerContext 实例
     */
    public static GraphRunnerContext create(State state, RunnableConfig config, CheckpointManager checkpointManager) {
        return new GraphRunnerContext(null, state, config, new ArrayList<>(), 0, checkpointManager);
    }

    /**
     * 获取当前节点ID
     *
     * @return 当前节点ID
     */
    public String getCurrentNodeId() {
        return currentNodeId;
    }

    /**
     * 设置当前节点ID
     *
     * @param currentNodeId 当前节点ID
     */
    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    /**
     * 获取整体状态
     *
     * @return 整体状态
     */
    public State getOverallState() {
        return overallState;
    }

    /**
     * 获取运行配置
     *
     * @return 运行配置
     */
    public RunnableConfig getConfig() {
        return config;
    }

    /**
     * 获取执行历史
     *
     * @return 执行历史
     */
    public List<GraphResponse<?>> getHistory() {
        return new ArrayList<>(history);
    }

    /**
     * 获取当前迭代次数
     *
     * @return 迭代次数
     */
    public int getIteration() {
        return iteration;
    }

    /**
     * 合并更新到当前状态
     *
     * @param updates 要合并的更新
     */
    public void mergeIntoCurrentState(Map<String, Object> updates) {
        if (updates != null && !updates.isEmpty()) {
            overallState.merge(updates);
        }
    }

    /**
     * 添加响应到历史记录
     * <p>
     * 当历史记录超过最大限制时，移除最旧的记录
     * </p>
     *
     * @param response 响应
     */
    public void addToHistory(GraphResponse<?> response) {
        history.add(response);
        // 如果历史记录超过最大限制，移除最旧的记录
        if (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }
    }

    /**
     * 创建下一个迭代的上下文
     *
     * @param nextNodeId 下一个节点ID
     * @return 新的上下文
     */
    public GraphRunnerContext forNextIteration(String nextNodeId) {
        return new GraphRunnerContext(nextNodeId, overallState, config, history, iteration + 1, checkpointManager);
    }

    /**
     * 创建带有更新状态的上下文副本
     *
     * @param updates 状态更新
     * @return 新的上下文
     */
    public GraphRunnerContext withUpdates(Map<String, Object> updates) {
        State newState = new MapState();
        newState.merge(overallState.data());
        newState.merge(updates);
        return new GraphRunnerContext(currentNodeId, newState, config, history, iteration, checkpointManager);
    }

    /**
     * 获取检查点管理器（可选）
     *
     * @return 检查点管理器的 Optional
     */
    public Optional<CheckpointManager> getCheckpointManager() {
        return Optional.ofNullable(checkpointManager);
    }

    @Override
    public String toString() {
        return "GraphRunnerContext{" +
                "currentNodeId='" + currentNodeId + '\'' +
                ", overallState=" + overallState +
                ", iteration=" + iteration +
                '}';
    }
}
