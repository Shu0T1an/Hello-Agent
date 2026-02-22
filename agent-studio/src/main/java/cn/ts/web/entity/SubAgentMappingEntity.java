package cn.ts.web.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * Main-agent to subagent mapping entity.
 */
@Data
public class SubAgentMappingEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long agentId;

    private String subagentType;

    private Long targetAgentId;

    private String description;

    private String toolsPolicy;

    /**
     * JSON text of tool id list for CUSTOM mode.
     */
    private String customToolIds;

    private Integer sortOrder;

    private Boolean enabled;

    private Instant createdAt;

    private Instant updatedAt;
}
