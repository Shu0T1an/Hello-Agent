package cn.ts.web.agent.mapper;

import cn.ts.web.agent.entity.AgentConfigEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

/**
 * Agent 配置 Mapper
 */
@Mapper
public interface AgentConfigMapper {

    /**
     * 插入Agent配置
     */
    @Insert("INSERT INTO agent_config (agent_name, display_name, description, model_id, system_prompt, max_iterations, temperature, enable_streaming, is_active, created_by, " +
            "enable_subagent_interceptor, include_general_purpose, subagent_tools_policy) " +
            "VALUES (#{agentName}, #{displayName}, #{description}, #{modelId}, #{systemPrompt}, #{maxIterations}, #{temperature}, #{enableStreaming}, #{isActive}, #{createdBy}, " +
            "#{enableSubAgentInterceptor}, #{includeGeneralPurpose}, #{subAgentToolsPolicy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AgentConfigEntity entity);

    /**
     * 更新Agent配置
     */
    @Update("UPDATE agent_config SET " +
            "display_name = #{displayName}, " +
            "description = #{description}, " +
            "model_id = #{modelId}, " +
            "system_prompt = #{systemPrompt}, " +
            "max_iterations = #{maxIterations}, " +
            "temperature = #{temperature}, " +
            "enable_streaming = #{enableStreaming}, " +
            "is_active = #{isActive}, " +
            "enable_subagent_interceptor = #{enableSubAgentInterceptor}, " +
            "include_general_purpose = #{includeGeneralPurpose}, " +
            "subagent_tools_policy = #{subAgentToolsPolicy} " +
            "WHERE id = #{id}")
    int updateById(AgentConfigEntity entity);

    /**
     * 删除Agent配置
     */
    @Delete("DELETE FROM agent_config WHERE id = #{id}")
    int deleteById(Long id);

    /**
     * 根据ID查询
     */
    @Select("SELECT * FROM agent_config WHERE id = #{id}")
    AgentConfigEntity selectById(Long id);

    /**
     * 根据Agent名称查询
     */
    @Select("SELECT * FROM agent_config WHERE agent_name = #{agentName}")
    Optional<AgentConfigEntity> selectByAgentName(String agentName);

    /**
     * 查询所有Agent配置
     */
    @Select("SELECT * FROM agent_config ORDER BY created_at DESC")
    List<AgentConfigEntity> selectAll();

    /**
     * 查询激活的Agent配置
     */
    @Select("SELECT * FROM agent_config WHERE is_active = TRUE ORDER BY created_at DESC")
    List<AgentConfigEntity> selectActive();

    /**
     * 根据模型ID查询Agent配置
     */
    @Select("SELECT * FROM agent_config WHERE model_id = #{modelId} ORDER BY created_at DESC")
    List<AgentConfigEntity> selectByModelId(Long modelId);

    /**
     * 根据创建者查询Agent配置
     */
    @Select("SELECT * FROM agent_config WHERE created_by = #{createdBy} ORDER BY created_at DESC")
    List<AgentConfigEntity> selectByCreatedBy(String createdBy);

    /**
     * 检查Agent名称是否存在
     */
    @Select("SELECT COUNT(*) FROM agent_config WHERE agent_name = #{agentName}")
    int countByAgentName(String agentName);

    /**
     * 检查Agent名称是否存在（排除指定ID）
     */
    @Select("SELECT COUNT(*) FROM agent_config WHERE agent_name = #{agentName} AND id != #{id}")
    int countByAgentNameExcludeId(@Param("agentName") String agentName, @Param("id") Long id);
}
