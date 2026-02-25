package cn.ts.web.agent.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Agent runtime graph DTO.
 */
@Data
public class AgentGraphDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long agentId;
    private String agentName;
    private String entryPoint;
    private List<AgentGraphNodeDTO> nodes;
    private List<AgentGraphEdgeDTO> edges;
    private GraphStats stats;
    private Instant generatedAt;

    @Data
    public static class GraphStats implements Serializable {
        private static final long serialVersionUID = 1L;

        private int nodeCount;
        private int edgeCount;
    }
}

