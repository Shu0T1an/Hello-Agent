package cn.ts.web.infra.checkpoint.mapper;

import cn.ts.web.infra.checkpoint.entity.CheckpointEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

/**
 * Checkpoint Mapper（重构版）
 * <p>
 * 使用 session_id 替代 thread_id，移除 is_latest 相关逻辑
 * </p>
 *
 * @author tianshuo
 */
@Mapper
public interface CheckpointMapper {

    /**
     * 插入 Checkpoint
     */
    @Insert("INSERT INTO checkpoint_snapshots " +
            "(session_id, checkpoint_id, node_id, last_node_id, parent_id, state_json, metadata_json, source, iteration) " +
            "VALUES (#{sessionId}, #{checkpointId}, #{nodeId}, #{lastNodeId}, #{parentId}, " +
            "#{stateJson}::jsonb, #{metadataJson}::jsonb, #{source}, #{iteration})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CheckpointEntity entity);

    /**
     * 根据 session_id 查询最新的 Checkpoint（按创建时间倒序取第一条）
     */
    @Select("SELECT * FROM checkpoint_snapshots " +
            "WHERE session_id = #{sessionId} ORDER BY created_at DESC LIMIT 1")
    Optional<CheckpointEntity> selectLatestBySessionId(String sessionId);

    /**
     * 根据 session_id 和 checkpoint_id 查询
     */
    @Select("SELECT * FROM checkpoint_snapshots " +
            "WHERE session_id = #{sessionId} AND checkpoint_id = #{checkpointId}")
    Optional<CheckpointEntity> selectBySessionIdAndCheckpointId(
            @Param("sessionId") String sessionId,
            @Param("checkpointId") String checkpointId);

    /**
     * 根据 checkpoint_id 查询
     */
    @Select("SELECT * FROM checkpoint_snapshots WHERE checkpoint_id = #{checkpointId}")
    Optional<CheckpointEntity> selectByCheckpointId(String checkpointId);

    /**
     * 根据 session_id 查询所有 Checkpoint（按创建时间倒序）
     */
    @Select("SELECT * FROM checkpoint_snapshots " +
            "WHERE session_id = #{sessionId} ORDER BY created_at DESC")
    List<CheckpointEntity> selectHistoryBySessionId(String sessionId);

    /**
     * 根据 parent_id 查询子 Checkpoint
     */
    @Select("SELECT * FROM checkpoint_snapshots WHERE parent_id = #{parentId} ORDER BY created_at ASC")
    List<CheckpointEntity> selectByParentId(String parentId);

    /**
     * 删除 session 的所有 Checkpoint
     */
    @Delete("DELETE FROM checkpoint_snapshots WHERE session_id = #{sessionId}")
    int deleteBySessionId(String sessionId);

    /**
     * 删除指定的 Checkpoint
     */
    @Delete("DELETE FROM checkpoint_snapshots WHERE checkpoint_id = #{checkpointId}")
    int deleteByCheckpointId(String checkpointId);

    /**
     * 统计 session 的 Checkpoint 数量
     */
    @Select("SELECT COUNT(*) FROM checkpoint_snapshots WHERE session_id = #{sessionId}")
    int countBySessionId(String sessionId);
}
