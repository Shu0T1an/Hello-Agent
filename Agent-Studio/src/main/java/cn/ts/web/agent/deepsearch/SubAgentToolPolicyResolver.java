package cn.ts.web.agent.deepsearch;

import cn.ts.agent.extension.interceptor.ToolPolicyInterceptor;

import java.util.List;

/**
 * Resolves tool policy interceptor for DeepSearch subagent types.
 */
public class SubAgentToolPolicyResolver {

    private final DeepSearchProperties properties;

    public SubAgentToolPolicyResolver(DeepSearchProperties properties) {
        this.properties = properties;
    }

    public ToolPolicyInterceptor forResearchAgent() {
        return new ToolPolicyInterceptor(
                "DeepSearchResearchToolPolicy",
                properties.getResearchToolAllowlist(),
                properties.getBlockedToolNames()
        );
    }

    public ToolPolicyInterceptor forCritiqueAgent() {
        return new ToolPolicyInterceptor(
                "DeepSearchCritiqueToolPolicy",
                properties.getCritiqueToolAllowlist(),
                properties.getBlockedToolNames()
        );
    }

    public ToolPolicyInterceptor forGeneralAgent() {
        return new ToolPolicyInterceptor(
                "DeepSearchGeneralToolPolicy",
                properties.getGeneralToolAllowlist(),
                properties.getBlockedToolNames()
        );
    }

    public List<String> blockedToolNames() {
        return properties.getBlockedToolNames();
    }
}
