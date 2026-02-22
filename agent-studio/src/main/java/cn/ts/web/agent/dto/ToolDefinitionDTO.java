package cn.ts.web.agent.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * 工具定义 DTO
 */
@Data
public class ToolDefinitionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 工具名称（唯一标识）
     */
    private String toolName;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 工具类型
     */
    private ToolType toolType;

    /**
     * Java类名（本地工具）
     */
    private String className;

    /**
     * MCP连接名称（MCP工具）
     */
    private String mcpConnectionName;

    /**
     * MCP工具名称
     */
    private String mcpToolName;

    /**
     * 是否可用
     */
    private Boolean isActive;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;
}
