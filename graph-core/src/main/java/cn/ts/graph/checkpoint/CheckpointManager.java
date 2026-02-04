package cn.ts.graph.checkpoint;

import cn.ts.graph.GraphRunnerContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 检查点管理器
 * <p>
 * 负责管理图执行过程中的检查点创建、恢复和查询
 * 参考 Spring AI Alibaba 的 CheckpointManager 设计
 * </p>
 *
 * @author tianshuo
 */
public interface CheckpointManager {

    /**
     * 创建检查点
     *
     * @param context 执行上下文
     * @param source  来源（auto/manual/error/restore）
     * @return 检查点ID
     */
    String createCheckpoint(GraphRunnerContext context, String source);

    /**
     * 从检查点恢复上下文
     *
     * @param threadId     会话ID
     * @param checkpointId 检查点ID
     * @return 恢复后的执行上下文
     */
    GraphRunnerContext restoreContext(String threadId, String checkpointId);

    /**
     * 获取最新状态
     *
     * @param threadId 会话ID
     * @return 状态快照的 Optional
     */
    Optional<StateSnapshot> getState(String threadId);

    /**
     * 获取状态历史
     *
     * @param threadId 会话ID
     * @return 状态历史列表
     */
    List<StateSnapshot> getStateHistory(String threadId);

    /**
     * 更新状态
     *
     * @param threadId 会话ID
     * @param updates  状态更新
     * @param asNode   作为哪个节点更新
     */
    void updateState(String threadId, Map<String, Object> updates, String asNode);

    /**
     * 删除检查点
     *
     * @param threadId     会话ID
     * @param checkpointId 检查点ID
     */
    void deleteCheckpoint(String threadId, String checkpointId);

    /**
     * 删除会话的所有检查点
     *
     * @param threadId 会话ID
     */
    void deleteThread(String threadId);

    /**
     * 检查是否应该为指定节点创建检查点
     *
     * @param nodeId 节点ID
     * @return true 如果应该创建检查点
     */
    boolean shouldCheckpoint(String nodeId);

    /**
     * 检查是否应该在错误时创建检查点
     *
     * @return true 如果应该在错误时创建检查点
     */
    boolean shouldCheckpointOnError();

    /**
     * 获取检查点配置
     *
     * @return 检查点配置
     */
    CheckpointConfig getConfig();

    /**
     * 获取存储实现
     *
     * @return 存储实现
     */
    CheckpointStorage getStorage();
}
