package cn.ts.web.infra.audit.mapper;

import cn.ts.web.infra.audit.entity.LlmPromptAuditEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Mapper for model prompt audit records.
 */
@Mapper
public interface LlmPromptAuditMapper {

    @Insert("INSERT INTO llm_prompt_audit " +
            "(trace_id, session_id, execution_id, agent_name, phase, request_json, response_json, error_message, created_at) " +
            "VALUES (#{traceId}, #{sessionId}, #{executionId}, #{agentName}, #{phase}, " +
            "#{requestJson}::jsonb, #{responseJson}::jsonb, #{errorMessage}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LlmPromptAuditEntity entity);

    @Select("SELECT id, trace_id, session_id, execution_id, agent_name, phase, request_json, response_json, error_message, created_at " +
            "FROM llm_prompt_audit " +
            "WHERE session_id = #{sessionId} " +
            "ORDER BY created_at DESC, id DESC " +
            "LIMIT #{limit}")
    List<LlmPromptAuditEntity> selectBySessionId(@Param("sessionId") String sessionId, @Param("limit") int limit);

    @Select("SELECT COUNT(1) FROM llm_prompt_audit WHERE session_id = #{sessionId}")
    long countBySessionId(@Param("sessionId") String sessionId);
}
