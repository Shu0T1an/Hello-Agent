package cn.ts.web.agent.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * Agent-工具关联实体类
 */
@Data
public class AgentToolMappingEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * Agent配置ID
     */
    private Long agentConfigId;

    /**
     * 工具定义ID
     */
    private Long toolDefinitionId;

    /**
     * 创建时间
     */
    private Instant createdAt;
}
