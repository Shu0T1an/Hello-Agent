package cn.ts.web.infra.mcp.mapper;

import cn.ts.web.infra.mcp.entity.McpConnectionConfigEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

/**
 * MCP 连接配置 Mapper
 */
@Mapper
public interface McpConnectionConfigMapper {

    /**
     * 插入MCP连接配置
     */
    @Insert("INSERT INTO mcp_connection_config (connection_name, description, connection_type, command, args, env, url, timeout_seconds, auto_reconnect, max_retries, retry_interval_seconds, is_active) " +
            "VALUES (#{connectionName}, #{description}, #{connectionType}, #{command}, #{args}::jsonb, #{env}::jsonb, #{url}, #{timeoutSeconds}, #{autoReconnect}, #{maxRetries}, #{retryIntervalSeconds}, #{isActive})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(McpConnectionConfigEntity entity);

    /**
     * 更新MCP连接配置
     */
    @Update("UPDATE mcp_connection_config SET " +
            "description = #{description}, " +
            "connection_type = #{connectionType}, " +
            "command = #{command}, " +
            "args = #{args}::jsonb, " +
            "env = #{env}::jsonb, " +
            "url = #{url}, " +
            "timeout_seconds = #{timeoutSeconds}, " +
            "auto_reconnect = #{autoReconnect}, " +
            "max_retries = #{maxRetries}, " +
            "retry_interval_seconds = #{retryIntervalSeconds}, " +
            "is_active = #{isActive} " +
            "WHERE id = #{id}")
    int updateById(McpConnectionConfigEntity entity);

    /**
     * 删除MCP连接配置
     */
    @Delete("DELETE FROM mcp_connection_config WHERE id = #{id}")
    int deleteById(Long id);

    /**
     * 根据ID查询
     */
    @Select("SELECT * FROM mcp_connection_config WHERE id = #{id}")
    McpConnectionConfigEntity selectById(Long id);

    /**
     * 根据连接名称查询
     */
    @Select("SELECT * FROM mcp_connection_config WHERE connection_name = #{connectionName}")
    Optional<McpConnectionConfigEntity> selectByConnectionName(String connectionName);

    /**
     * 查询所有MCP连接配置
     */
    @Select("SELECT * FROM mcp_connection_config ORDER BY created_at DESC")
    List<McpConnectionConfigEntity> selectAll();

    /**
     * 查询激活的MCP连接配置
     */
    @Select("SELECT * FROM mcp_connection_config WHERE is_active = TRUE ORDER BY created_at DESC")
    List<McpConnectionConfigEntity> selectActive();

    /**
     * 根据连接类型查询
     */
    @Select("SELECT * FROM mcp_connection_config WHERE connection_type = #{connectionType} ORDER BY created_at DESC")
    List<McpConnectionConfigEntity> selectByType(String connectionType);

    /**
     * 检查连接名称是否存在
     */
    @Select("SELECT COUNT(*) FROM mcp_connection_config WHERE connection_name = #{connectionName}")
    int countByConnectionName(String connectionName);

    /**
     * 检查连接名称是否存在（排除指定ID）
     */
    @Select("SELECT COUNT(*) FROM mcp_connection_config WHERE connection_name = #{connectionName} AND id != #{id}")
    int countByConnectionNameExcludeId(@Param("connectionName") String connectionName, @Param("id") Long id);
}
