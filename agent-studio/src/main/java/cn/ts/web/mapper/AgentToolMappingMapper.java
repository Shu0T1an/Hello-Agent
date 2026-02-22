package cn.ts.web.mapper;

import cn.ts.web.entity.AgentToolMappingEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Agent-工具关联 Mapper
 */
@Mapper
public interface AgentToolMappingMapper {

    /**
     * 插入Agent-工具关联
     */
    @Insert("INSERT INTO agent_tool_mapping (agent_config_id, tool_definition_id) " +
            "VALUES (#{agentConfigId}, #{toolDefinitionId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AgentToolMappingEntity entity);

    /**
     * 批量插入Agent-工具关联
     */
    @Insert("<script>" +
            "INSERT INTO agent_tool_mapping (agent_config_id, tool_definition_id) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.agentConfigId}, #{item.toolDefinitionId})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<AgentToolMappingEntity> list);

    /**
     * 删除Agent-工具关联
     */
    @Delete("DELETE FROM agent_tool_mapping WHERE id = #{id}")
    int deleteById(Long id);

    /**
     * 删除Agent的所有工具关联
     */
    @Delete("DELETE FROM agent_tool_mapping WHERE agent_config_id = #{agentConfigId}")
    int deleteByAgentId(Long agentConfigId);

    /**
     * 删除工具的所有Agent关联
     */
    @Delete("DELETE FROM agent_tool_mapping WHERE tool_definition_id = #{toolDefinitionId}")
    int deleteByToolId(Long toolDefinitionId);

    /**
     * 根据ID查询
     */
    @Select("SELECT * FROM agent_tool_mapping WHERE id = #{id}")
    AgentToolMappingEntity selectById(Long id);

    /**
     * 根据Agent ID查询所有工具关联
     */
    @Select("SELECT * FROM agent_tool_mapping WHERE agent_config_id = #{agentConfigId}")
    List<AgentToolMappingEntity> selectByAgentId(Long agentConfigId);

    /**
     * 根据工具ID查询所有Agent关联
     */
    @Select("SELECT * FROM agent_tool_mapping WHERE tool_definition_id = #{toolDefinitionId}")
    List<AgentToolMappingEntity> selectByToolId(Long toolDefinitionId);

    /**
     * 查询Agent的工具ID列表
     */
    @Select("SELECT tool_definition_id FROM agent_tool_mapping WHERE agent_config_id = #{agentConfigId}")
    List<Long> selectToolIdsByAgentId(Long agentConfigId);

    /**
     * 检查Agent-工具关联是否存在
     */
    @Select("SELECT COUNT(*) FROM agent_tool_mapping WHERE agent_config_id = #{agentConfigId} AND tool_definition_id = #{toolDefinitionId}")
    int countByAgentAndTool(@Param("agentConfigId") Long agentConfigId, @Param("toolDefinitionId") Long toolDefinitionId);

    /**
     * 删除Agent的指定工具关联
     */
    @Delete("DELETE FROM agent_tool_mapping WHERE agent_config_id = #{agentConfigId} AND tool_definition_id = #{toolDefinitionId}")
    int deleteByAgentAndTool(@Param("agentConfigId") Long agentConfigId, @Param("toolDefinitionId") Long toolDefinitionId);
}
