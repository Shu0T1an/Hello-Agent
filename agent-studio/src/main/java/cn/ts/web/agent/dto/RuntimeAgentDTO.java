package cn.ts.web.agent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Runtime agent summary for frontend display.
 */
@Data
public class RuntimeAgentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String agentName;
    private String displayName;
    private String description;
    private boolean builtIn;
    private boolean managed;
}
