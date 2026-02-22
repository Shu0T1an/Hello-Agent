package cn.ts.web.agent.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Agent 配置 DTO
 */
@Data
public class AgentConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * Agent名称（唯一标识）
     */
    private String agentName;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * Agent描述
     */
    private String description;

    /**
     * 关联模型ID
     */
    private Long modelId;

    /**
     * 关联的模型配置
     */
    private ModelConfigDTO modelConfig;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 最大迭代次数
     */
    private Integer maxIterations;

    /**
     * 温度参数
     */
    private BigDecimal temperature;

    /**
     * 是否启用流式输出
     */
    private Boolean enableStreaming;

    /**
     * 是否激活
     */
    private Boolean isActive;

    /**
     * 创建者
     */
    private String createdBy;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;

    /**
     * 关联的工具定义ID列表
     */
    private List<Long> toolIds;

    /**
     * 关联的工具定义列表
     */
    private List<ToolDefinitionDTO> toolDefinitions;

    /**
     * Enable SubAgentInterceptor for this agent.
     */
    private Boolean enableSubAgentInterceptor;

    /**
     * Include built-in general-purpose subagent.
     */
    private Boolean includeGeneralPurpose;

    /**
     * Default tools policy for subagents.
     */
    private SubAgentToolsPolicy subAgentToolsPolicy;

    /**
     * Subagent mappings for this agent.
     */
    private List<SubAgentMappingDTO> subAgents;
}
