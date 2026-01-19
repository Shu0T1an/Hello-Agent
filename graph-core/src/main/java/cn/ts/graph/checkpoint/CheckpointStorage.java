package cn.ts.graph.checkpoint;

import java.util.List;
import java.util.Optional;

/**
 * 检查点存储抽象
 * <p>
 * 定义检查点持久化的接口，支持多种存储后端（内存、数据库等）
 * 参考 Spring AI Alibaba 的 CheckpointStorage 设计
 * </p>
 *
 * @author tianshuo
 */
public interface CheckpointStorage {

    /**
     * 保存检查点
     *
     * @param threadId  会话ID
     * @param snapshot  状态快照
     * @return 检查点ID
     */
    String saveCheckpoint(String threadId, StateSnapshot snapshot);

    /**
     * 获取指定检查点
     *
     * @param threadId     会话ID
     * @param checkpointId 检查点ID
     * @return 状态快照的 Optional
     */
    Optional<StateSnapshot> getCheckpoint(String threadId, String checkpointId);

    /**
     * 获取最新检查点
     *
     * @param threadId 会话ID
     * @return 状态快照的 Optional
     */
    Optional<StateSnapshot> getLatestCheckpoint(String threadId);

    /**
     * 获取检查点历史
     *
     * @param threadId 会话ID
     * @return 检查点历史列表，按时间顺序排列
     */
    List<StateSnapshot> getCheckpointHistory(String threadId);

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
}
