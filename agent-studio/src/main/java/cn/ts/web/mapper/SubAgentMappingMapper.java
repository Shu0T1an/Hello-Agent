package cn.ts.web.mapper;

import cn.ts.web.entity.SubAgentMappingEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Mapper for subagent mapping table.
 */
@Mapper
public interface SubAgentMappingMapper {

    @Insert("INSERT INTO agent_subagent_mapping (agent_id, subagent_type, target_agent_id, description, tools_policy, custom_tool_ids, sort_order, enabled) " +
            "VALUES (#{agentId}, #{subagentType}, #{targetAgentId}, #{description}, #{toolsPolicy}, CAST(#{customToolIds} AS jsonb), #{sortOrder}, #{enabled})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SubAgentMappingEntity entity);

    @Delete("DELETE FROM agent_subagent_mapping WHERE agent_id = #{agentId}")
    int deleteByAgentId(Long agentId);

    @Select("SELECT * FROM agent_subagent_mapping WHERE agent_id = #{agentId} AND enabled = TRUE ORDER BY sort_order ASC, id ASC")
    List<SubAgentMappingEntity> selectByAgentId(Long agentId);

    @Select("SELECT * FROM agent_subagent_mapping WHERE target_agent_id = #{targetAgentId} AND enabled = TRUE ORDER BY sort_order ASC, id ASC")
    List<SubAgentMappingEntity> selectByTargetAgentId(Long targetAgentId);
}
