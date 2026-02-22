package cn.ts.web.mapper;

import cn.ts.web.entity.SessionEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Session Mapper
 * <p>
 * 负责会话数据的数据库操作
 * </p>
 *
 * @author tianshuo
 */
@Mapper
public interface SessionMapper {

    /**
     * 插入会话
     */
    @Insert("INSERT INTO sessions (session_id, title, current_agent, status, agent_switch_history) " +
            "VALUES (#{sessionId}, #{title}, #{currentAgent}, #{status}, #{agentSwitchHistory}::jsonb)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SessionEntity session);

    /**
     * 根据 session_id 查询会话
     */
    @Select("SELECT * FROM sessions WHERE session_id = #{sessionId}")
    SessionEntity selectBySessionId(String sessionId);

    /**
     * 查询所有活跃会话（按更新时间倒序）
     */
    @Select("SELECT * FROM sessions WHERE status = 'active' ORDER BY updated_at DESC")
    List<SessionEntity> selectActiveSessions();

    /**
     * 更新会话标题
     */
    @Update("UPDATE sessions SET title = #{title}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE session_id = #{sessionId}")
    int updateTitle(@Param("sessionId") String sessionId, @Param("title") String title);

    /**
     * 更新会话的 Agent 和切换历史
     */
    @Update("UPDATE sessions SET current_agent = #{currentAgent}, " +
            "agent_switch_history = #{agentSwitchHistory}::jsonb, updated_at = CURRENT_TIMESTAMP " +
            "WHERE session_id = #{sessionId}")
    int updateAgent(@Param("sessionId") String sessionId,
                    @Param("currentAgent") String currentAgent,
                    @Param("agentSwitchHistory") String history);

    /**
     * 更新会话时间戳
     */
    @Update("UPDATE sessions SET updated_at = CURRENT_TIMESTAMP WHERE session_id = #{sessionId}")
    int updateTimestamp(String sessionId);

    /**
     * 更新会话状态
     */
    @Update("UPDATE sessions SET status = #{status}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE session_id = #{sessionId}")
    int updateStatus(@Param("sessionId") String sessionId, @Param("status") String status);

    /**
     * 删除会话（软删除）
     */
    @Update("UPDATE sessions SET status = 'deleted', updated_at = CURRENT_TIMESTAMP " +
            "WHERE session_id = #{sessionId}")
    int softDelete(String sessionId);

    /**
     * 物理删除会话
     */
    @Delete("DELETE FROM sessions WHERE session_id = #{sessionId}")
    int delete(String sessionId);
}
