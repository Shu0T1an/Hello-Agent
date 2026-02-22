package cn.ts.web.tool.service;

import cn.ts.web.agent.dto.ToolDefinitionDTO;
import cn.ts.web.agent.dto.ToolType;
import cn.ts.web.tool.entity.ToolDefinitionEntity;

import java.util.List;

/**
 * 工具定义服务接口
 */
public interface ToolDefinitionService {

    /**
     * 创建工具定义
     *
     * @param dto 工具定义 DTO
     * @return 创建后的工具定义
     */
    ToolDefinitionDTO createTool(ToolDefinitionDTO dto);

    /**
     * 创建或更新工具定义
     *
     * @param dto 工具定义 DTO
     * @return 创建或更新后的工具定义
     */
    ToolDefinitionDTO createOrUpdateTool(ToolDefinitionDTO dto);

    /**
     * 更新工具定义
     *
     * @param id  工具定义 ID
     * @param dto 工具定义 DTO
     * @return 更新后的工具定义
     */
    ToolDefinitionDTO updateTool(Long id, ToolDefinitionDTO dto);

    /**
     * 删除工具定义
     *
     * @param id 工具定义 ID
     */
    void deleteTool(Long id);

    /**
     * 根据ID获取工具定义
     *
     * @param id 工具定义 ID
     * @return 工具定义
     */
    ToolDefinitionDTO getToolById(Long id);

    /**
     * 根据工具名称获取工具定义
     *
     * @param toolName 工具名称
     * @return 工具定义
     */
    ToolDefinitionDTO getToolByName(String toolName);

    /**
     * 获取所有工具定义
     *
     * @return 工具定义列表
     */
    List<ToolDefinitionDTO> getAllTools();

    /**
     * 获取激活的工具定义
     *
     * @return 工具定义列表
     */
    List<ToolDefinitionDTO> getActiveTools();

    /**
     * 根据类型获取工具
     *
     * @param toolType 工具类型
     * @return 工具定义列表
     */
    List<ToolDefinitionDTO> getToolsByType(ToolType toolType);

    /**
     * 根据 Agent ID 获取关联的工具
     *
     * @param agentConfigId Agent 配置 ID
     * @return 工具定义列表
     */
    List<ToolDefinitionDTO> getToolsByAgentId(Long agentConfigId);

    /**
     * 实例化工具列表
     *
     * @param toolDefs 工具定义列表
     * @return 工具实例数组
     */
    Object[] instantiateTools(List<ToolDefinitionDTO> toolDefs);

    /**
     * 禁用指定连接的所有工具
     *
     * @param mcpConnectionName MCP 连接名称
     */
    void disableToolsByConnection(String mcpConnectionName);

    /**
     * 同步 MCP 工具
     *
     * @param mcpConnectionName MCP 连接名称
     * @param mcpTools         MCP 工具列表
     */
    void syncMcpTools(String mcpConnectionName, List<McpToolInfo> mcpTools);

    /**
     * MCP 工具信息
     */
    record McpToolInfo(String name, String description) {
    }
}
