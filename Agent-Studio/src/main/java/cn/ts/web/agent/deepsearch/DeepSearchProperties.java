package cn.ts.web.agent.deepsearch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for built-in DeepSearch agent.
 */
@Component
@ConfigurationProperties(prefix = "agent.deep-search")
public class DeepSearchProperties {

    private boolean enabled = true;
    private String agentName = "deep-search";
    private String displayName = "Deep Search";
    private String description = "Built-in deep search agent for multi-step research and synthesis.";
    private boolean includeGeneralPurposeSubagent = true;
    private int maxIterations = 25;
    private boolean streamEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isIncludeGeneralPurposeSubagent() {
        return includeGeneralPurposeSubagent;
    }

    public void setIncludeGeneralPurposeSubagent(boolean includeGeneralPurposeSubagent) {
        this.includeGeneralPurposeSubagent = includeGeneralPurposeSubagent;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public boolean isStreamEnabled() {
        return streamEnabled;
    }

    public void setStreamEnabled(boolean streamEnabled) {
        this.streamEnabled = streamEnabled;
    }
}
