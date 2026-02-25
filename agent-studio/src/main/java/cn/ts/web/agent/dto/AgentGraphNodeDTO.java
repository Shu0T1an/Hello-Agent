package cn.ts.web.agent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * Graph node DTO for agent visualization.
 */
@Data
public class AgentGraphNodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String label;
    private String nodeType;
    private String className;
    private Map<String, Object> metadata;
}

