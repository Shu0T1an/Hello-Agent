package cn.ts.web.mapper;

import cn.ts.web.entity.CheckpointEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

/**
 * Checkpoint Mapper
 * 用于统一会话和状态管理
 */
@Mapper
public interface CheckpointMapper {

    /**
     * 插入 Checkpoint
     */
    @Insert("INSERT INTO checkpoint_snapshots " +
            "(thread_id, checkpoint_id, node_id, parent_id, state_json, metadata_json, source, iteration, is_latest) " +
            "VALUES (#{threadId}, #{checkpointId}, #{nodeId}, #{parentId}, " +
            "#{stateJson}::jsonb, #{metadataJson}::jsonb, #{source}, #{iteration}, #{isLatest})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CheckpointEntity entity);

    /**
     * 根据 threadId 查询最新的 Checkpoint
     */
    @Select("SELECT * FROM checkpoint_snapshots WHERE thread_id = #{threadId} AND is_latest = TRUE")
    Optional<CheckpointEntity> selectLatestByThreadId(String threadId);

    /**
     * 根据 threadId 和 checkpointId 查询
     */
    @Select("SELECT * FROM checkpoint_snapshots WHERE thread_id = #{threadId} AND checkpoint_id = #{checkpointId}")
    Optional<CheckpointEntity> selectByThreadIdAndCheckpointId(
            @Param("threadId") String threadId,
            @Param("checkpointId") String checkpointId);

    /**
     * 根据 checkpointId 查询
     */
    @Select("SELECT * FROM checkpoint_snapshots WHERE checkpoint_id = #{checkpointId}")
    Optional<CheckpointEntity> selectByCheckpointId(String checkpointId);

    /**
     * 根据 threadId 查询所有 Checkpoint（按创建时间倒序）
     */
    @Select("SELECT * FROM checkpoint_snapshots WHERE thread_id = #{threadId} ORDER BY created_at DESC")
    List<CheckpointEntity> selectByThreadIdOrderByCreatedAt(String threadId);

    /**
     * 查询所有唯一的 threadId（用于会话列表）
     */
    @Select("SELECT DISTINCT thread_id FROM checkpoint_snapshots ORDER BY thread_id")
    List<String> selectAllThreadIds();

    /**
     * 清除 thread 的所有 is_latest 标志
     */
    @Update("UPDATE checkpoint_snapshots SET is_latest = FALSE WHERE thread_id = #{threadId}")
    int clearLatestFlag(String threadId);

    /**
     * 设置指定的 Checkpoint 为最新
     */
    @Update("UPDATE checkpoint_snapshots SET is_latest = TRUE " +
            "WHERE thread_id = #{threadId} AND checkpoint_id = #{checkpointId}")
    int setLatest(@Param("threadId") String threadId, @Param("checkpointId") String checkpointId);

    /**
     * 删除 thread 的所有 Checkpoint
     */
    @Delete("DELETE FROM checkpoint_snapshots WHERE thread_id = #{threadId}")
    int deleteByThreadId(String threadId);

    /**
     * 删除指定的 Checkpoint
     */
    @Delete("DELETE FROM checkpoint_snapshots WHERE checkpoint_id = #{checkpointId}")
    int deleteByCheckpointId(String checkpointId);

    /**
     * 统计 thread 的 Checkpoint 数量
     */
    @Select("SELECT COUNT(*) FROM checkpoint_snapshots WHERE thread_id = #{threadId}")
    int countByThreadId(String threadId);

    /**
     * 查询指定父 Checkpoint 的所有子 Checkpoint
     */
    @Select("SELECT * FROM checkpoint_snapshots WHERE parent_id = #{parentId} ORDER BY created_at ASC")
    List<CheckpointEntity> selectByParentId(String parentId);
}
