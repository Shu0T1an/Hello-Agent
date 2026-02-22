package cn.ts.web.dto.agent;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Mapping from a main agent to a typed subagent.
 */
@Data
public class SubAgentMappingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long agentId;

    private String subagentType;

    private Long targetAgentId;

    private String description;

    private SubAgentToolsPolicy toolsPolicy;

    private List<Long> customToolIds;

    private Integer sortOrder;

    private Boolean enabled;

    private Instant createdAt;

    private Instant updatedAt;
}
