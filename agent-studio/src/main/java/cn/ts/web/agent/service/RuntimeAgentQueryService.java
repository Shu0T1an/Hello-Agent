package cn.ts.web.agent.service;

import cn.ts.web.agent.deepsearch.DeepSearchProperties;
import cn.ts.web.agent.dto.AgentConfigDTO;
import cn.ts.web.agent.dto.RuntimeAgentDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Provides runtime registered agent summaries.
 */
@Service
public class RuntimeAgentQueryService {

    private final AgentExecutionService agentExecutionService;
    private final AgentConfigService agentConfigService;
    private final DeepSearchProperties deepSearchProperties;

    public RuntimeAgentQueryService(
            AgentExecutionService agentExecutionService,
            AgentConfigService agentConfigService,
            DeepSearchProperties deepSearchProperties) {
        this.agentExecutionService = agentExecutionService;
        this.agentConfigService = agentConfigService;
        this.deepSearchProperties = deepSearchProperties;
    }

    public List<RuntimeAgentDTO> listRuntimeAgents() {
        Set<String> names = agentExecutionService.getRegisteredAgents();
        List<RuntimeAgentDTO> result = new ArrayList<>();
        for (String name : names) {
            RuntimeAgentDTO dto = new RuntimeAgentDTO();
            dto.setAgentName(name);

            AgentConfigDTO config = agentConfigService.getAgentByName(name);
            boolean builtIn = isDeepSearchBuiltIn(name);
            dto.setBuiltIn(builtIn);
            dto.setManaged(config != null);

            if (config != null) {
                dto.setDisplayName(config.getDisplayName());
                dto.setDescription(config.getDescription());
            } else if (builtIn) {
                dto.setDisplayName(fallback(deepSearchProperties.getDisplayName(), name));
                dto.setDescription(fallback(deepSearchProperties.getDescription(), "Built-in runtime agent."));
            } else {
                dto.setDisplayName(name);
                dto.setDescription("Runtime registered agent.");
            }

            result.add(dto);
        }
        result.sort(Comparator.comparing(RuntimeAgentDTO::getAgentName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private boolean isDeepSearchBuiltIn(String agentName) {
        return deepSearchProperties != null
                && deepSearchProperties.isEnabled()
                && agentName != null
                && agentName.equals(deepSearchProperties.getAgentName());
    }

    private String fallback(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
