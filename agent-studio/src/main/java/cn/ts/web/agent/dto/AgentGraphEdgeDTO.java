package cn.ts.web.agent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Graph edge DTO for agent visualization.
 */
@Data
public class AgentGraphEdgeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String source;
    private String target;
    private String edgeType;
    private String label;
}

