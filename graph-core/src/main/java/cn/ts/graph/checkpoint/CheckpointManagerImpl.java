package cn.ts.graph.checkpoint;

import cn.ts.graph.GraphRunnerContext;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.state.MapState;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 检查点管理器实现
 * <p>
 * 负责管理图执行过程中的检查点创建、恢复和查询
 * 参考 Spring AI Alibaba 的 CheckpointManagerImpl 设计
 * </p>
 *
 * @author tianshuo
 */
public class CheckpointManagerImpl implements CheckpointManager {

    private final CheckpointStorage storage;
    private final CheckpointConfig config;

    /**
     * 创建检查点管理器
     *
     * @param storage 存储实现
     * @param config  检查点配置
     */
    public CheckpointManagerImpl(CheckpointStorage storage, CheckpointConfig config) {
        this.storage = storage;
        this.config = config;
    }

    @Override
    public String createCheckpoint(GraphRunnerContext context, String source) {
        String threadId = getThreadId(context);

        // 创建元数据
        CheckpointMetadata metadata = CheckpointMetadata.builder()
                .source(source)
                .stepInfo(Map.of(
                        "iteration", context.getIteration(),
                        "currentNode", context.getCurrentNodeId()
                ))
                .build();

        // 创建状态快照
        StateSnapshot snapshot = StateSnapshot.builder()
                .checkpointId(UUID.randomUUID().toString())
                .threadId(threadId)
                .nodeId(context.getCurrentNodeId())
                .state(context.getOverallState().data())
                .metadata(metadata)
                .timestamp(Instant.now())
                .iteration(context.getIteration())
                .build();

        // 保存检查点
        return storage.saveCheckpoint(threadId, snapshot);
    }

    @Override
    public GraphRunnerContext restoreContext(String threadId, String checkpointId) {
        StateSnapshot snapshot = storage.getCheckpoint(threadId, checkpointId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found: " + checkpointId));

        // 创建新的状态
        MapState state = new MapState();
        state.merge(snapshot.getState());

        // 创建配置
        RunnableConfig runConfig = RunnableConfig.builder()
                .executionId(threadId)
                .build();

        // 创建上下文
        GraphRunnerContext context = GraphRunnerContext.create(state, runConfig);
        context.setCurrentNodeId(snapshot.getNodeId());

        return context;
    }

    @Override
    public Optional<StateSnapshot> getState(String threadId) {
        return storage.getLatestCheckpoint(threadId);
    }

    @Override
    public List<StateSnapshot> getStateHistory(String threadId) {
        return storage.getCheckpointHistory(threadId);
    }

    @Override
    public void updateState(String threadId, Map<String, Object> updates, String asNode) {
        Optional<StateSnapshot> latestOpt = storage.getLatestCheckpoint(threadId);
        if (latestOpt.isEmpty()) {
            throw new IllegalArgumentException("No checkpoint found for thread: " + threadId);
        }

        StateSnapshot latest = latestOpt.get();

        // 创建元数据
        CheckpointMetadata metadata = CheckpointMetadata.builder()
                .source("manual")
                .parentId(latest.getCheckpointId())
                .stepInfo(Map.of(
                        "updatedByNode", asNode
                ))
                .build();

        // 创建新的状态快照
        Map<String, Object> newState = new java.util.HashMap<>(latest.getState());
        newState.putAll(updates);

        StateSnapshot snapshot = StateSnapshot.builder()
                .checkpointId(UUID.randomUUID().toString())
                .threadId(threadId)
                .nodeId(asNode)
                .state(newState)
                .metadata(metadata)
                .timestamp(Instant.now())
                .iteration(latest.getIteration())
                .build();

        storage.saveCheckpoint(threadId, snapshot);
    }

    @Override
    public void deleteCheckpoint(String threadId, String checkpointId) {
        storage.deleteCheckpoint(threadId, checkpointId);
    }

    @Override
    public void deleteThread(String threadId) {
        storage.deleteThread(threadId);
    }

    @Override
    public boolean shouldCheckpoint(String nodeId) {
        return config.shouldCheckpoint(nodeId);
    }

    @Override
    public boolean shouldCheckpointOnError() {
        return config.shouldCheckpointOnError();
    }

    @Override
    public CheckpointConfig getConfig() {
        return config;
    }

    /**
     * 从上下文中获取会话ID
     *
     * @param context 执行上下文
     * @return 会话ID
     */
    private String getThreadId(GraphRunnerContext context) {
        String threadId = context.getConfig().executionId();
        if (threadId == null) {
            threadId = UUID.randomUUID().toString();
        }
        return threadId;
    }

    /**
     * 获取存储实现
     *
     * @return 存储实现
     */
    public CheckpointStorage getStorage() {
        return storage;
    }
}
