package cn.ts.web.tool.mapper;

import cn.ts.web.tool.entity.ToolDefinitionEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

/**
 * 工具定义 Mapper
 */
@Mapper
public interface ToolDefinitionMapper {

    /**
     * 插入工具定义
     */
    @Insert("INSERT INTO tool_definition (tool_name, display_name, description, tool_type, class_name, mcp_connection_name, mcp_tool_name, is_active) " +
            "VALUES (#{toolName}, #{displayName}, #{description}, #{toolType}, #{className}, #{mcpConnectionName}, #{mcpToolName}, #{isActive})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ToolDefinitionEntity entity);

    /**
     * 更新工具定义
     */
    @Update("UPDATE tool_definition SET " +
            "display_name = #{displayName}, " +
            "description = #{description}, " +
            "is_active = #{isActive} " +
            "WHERE id = #{id}")
    int updateById(ToolDefinitionEntity entity);

    /**
     * 删除工具定义
     */
    @Delete("DELETE FROM tool_definition WHERE id = #{id}")
    int deleteById(Long id);

    /**
     * 根据ID查询
     */
    @Select("SELECT * FROM tool_definition WHERE id = #{id}")
    ToolDefinitionEntity selectById(Long id);

    /**
     * 根据工具名称查询
     */
    @Select("SELECT * FROM tool_definition WHERE tool_name = #{toolName}")
    Optional<ToolDefinitionEntity> selectByToolName(String toolName);

    /**
     * 查询所有工具定义
     */
    @Select("SELECT * FROM tool_definition ORDER BY tool_type, created_at DESC")
    List<ToolDefinitionEntity> selectAll();

    /**
     * 查询激活的工具定义
     */
    @Select("SELECT * FROM tool_definition WHERE is_active = TRUE ORDER BY tool_type, created_at DESC")
    List<ToolDefinitionEntity> selectActive();

    /**
     * 根据类型查询工具
     */
    @Select("SELECT * FROM tool_definition WHERE tool_type = #{toolType} ORDER BY created_at DESC")
    List<ToolDefinitionEntity> selectByType(String toolType);

    /**
     * 根据MCP连接名称查询工具
     */
    @Select("SELECT * FROM tool_definition WHERE mcp_connection_name = #{mcpConnectionName} ORDER BY created_at DESC")
    List<ToolDefinitionEntity> selectByMcpConnection(String mcpConnectionName);

    /**
     * 根据Agent ID查询关联的工具
     */
    @Select("SELECT td.* FROM tool_definition td " +
            "JOIN agent_tool_mapping atm ON td.id = atm.tool_definition_id " +
            "WHERE atm.agent_config_id = #{agentConfigId} AND td.is_active = TRUE " +
            "ORDER BY td.tool_type, td.created_at DESC")
    List<ToolDefinitionEntity> selectByAgentId(Long agentConfigId);

    /**
     * 禁用指定连接的所有工具
     */
    @Update("UPDATE tool_definition SET is_active = FALSE WHERE mcp_connection_name = #{mcpConnectionName}")
    int disableByMcpConnection(String mcpConnectionName);

    /**
     * 检查工具名称是否存在
     */
    @Select("SELECT COUNT(*) FROM tool_definition WHERE tool_name = #{toolName}")
    int countByToolName(String toolName);

    /**
     * 检查工具名称是否存在（排除指定ID）
     */
    @Select("SELECT COUNT(*) FROM tool_definition WHERE tool_name = #{toolName} AND id != #{id}")
    int countByToolNameExcludeId(@Param("toolName") String toolName, @Param("id") Long id);
}
