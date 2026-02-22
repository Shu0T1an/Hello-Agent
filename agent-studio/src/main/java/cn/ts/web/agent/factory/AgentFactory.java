package cn.ts.web.agent.factory;

import cn.ts.agent.core.ReactAgent;
import cn.ts.agent.extension.interceptor.SubAgentInterceptor;
import cn.ts.agent.extension.interceptor.ToolPolicyInterceptor;
import cn.ts.agent.extension.tools.TaskTool;
import cn.ts.agent.hook.ClarificationQaHook;
import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.graph.hook.Hook;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.web.agent.dto.AgentConfigDTO;
import cn.ts.web.agent.dto.SubAgentMappingDTO;
import cn.ts.web.agent.dto.SubAgentToolsPolicy;
import cn.ts.web.agent.dto.ToolDefinitionDTO;
import cn.ts.web.agent.entity.AgentConfigEntity;
import cn.ts.web.agent.entity.SubAgentMappingEntity;
import cn.ts.web.agent.mapper.AgentConfigMapper;
import cn.ts.web.agent.mapper.SubAgentMappingMapper;
import cn.ts.web.agent.service.ModelConfigService;
import cn.ts.web.agent.service.SubAgentProgressBus;
import cn.ts.web.tool.service.ToolDefinitionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Factory for creating ReactAgent from persisted configuration.
 */
@Component
public class AgentFactory {

    private static final Logger logger = LoggerFactory.getLogger(AgentFactory.class);
    private static final int DEFAULT_MAX_PARALLEL_SUBAGENTS = 3;

    private final ModelConfigService modelConfigService;
    private final ToolDefinitionService toolDefinitionService;
    private final AgentConfigMapper agentConfigMapper;
    private final SubAgentMappingMapper subAgentMappingMapper;
    private final CheckpointManager checkpointManager;
    private final SubAgentProgressBus subAgentProgressBus;
    private final List<String> blockedToolNames;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentFactory(ModelConfigService modelConfigService,
                        ToolDefinitionService toolDefinitionService,
                        AgentConfigMapper agentConfigMapper,
                        SubAgentMappingMapper subAgentMappingMapper,
                        CheckpointManager checkpointManager,
                        SubAgentProgressBus subAgentProgressBus,
                        @Value("${agent.subagent.blocked-tool-names:}") String blockedToolNamesRaw) {
        this.modelConfigService = modelConfigService;
        this.toolDefinitionService = toolDefinitionService;
        this.agentConfigMapper = agentConfigMapper;
        this.subAgentMappingMapper = subAgentMappingMapper;
        this.checkpointManager = checkpointManager;
        this.subAgentProgressBus = subAgentProgressBus;
        this.blockedToolNames = parseBlockedToolNames(blockedToolNamesRaw);
    }

    public ReactAgent createAgent(AgentConfigDTO config) {
        return createAgentInternal(config, true, true, null);
    }

    private ReactAgent createAgentInternal(
            AgentConfigDTO config,
            boolean includeSubAgentInterceptor,
            boolean attachCheckpointManager,
            List<ToolDefinitionDTO> explicitToolDefinitions) {
        logger.info("Creating agent: {}", config.getAgentName());

        ChatModel chatModel = createChatModel(config);
        Object[] tools = instantiateTools(config, explicitToolDefinitions);

        ReactAgent.Builder builder = ReactAgent.builder()
                .name(config.getAgentName())
                .description(config.getDescription() != null ? config.getDescription() : config.getDisplayName())
                .chatModel(chatModel);
        builder.hooks(buildGlobalHooks());

        if (attachCheckpointManager) {
            builder.checkpointManager(checkpointManager);
        }

        if (config.getEnableStreaming() != null) {
            builder.streaming(config.getEnableStreaming());
        }

        List<ModelInterceptor> modelInterceptors = new ArrayList<>();
        if (includeSubAgentInterceptor && Boolean.TRUE.equals(config.getEnableSubAgentInterceptor())) {
            Map<String, ReactAgent> subAgents = buildSubAgentsFor(config);
            tools = appendTaskTool(tools, subAgents);
            modelInterceptors.add(new SubAgentInterceptor(null, subAgents, subAgentProgressBus));
        }
        if (!blockedToolNames.isEmpty()) {
            modelInterceptors.add(new ToolPolicyInterceptor(
                    "AgentFactoryGlobalToolPolicy",
                    List.of(),
                    blockedToolNames
            ));
        }
        if (!modelInterceptors.isEmpty()) {
            builder.modelInterceptors(modelInterceptors);
        }
        builder.tools(tools);

        ReactAgent agent = builder.build();
        logger.info("Agent created successfully: {}", config.getAgentName());
        return agent;
    }

    private List<Hook> buildGlobalHooks() {
        return List.of(ClarificationQaHook.builder().build());
    }

    private ChatModel createChatModel(AgentConfigDTO config) {
        var modelConfig = config.getModelConfig();
        if (modelConfig == null) {
            modelConfig = modelConfigService.getModelById(config.getModelId());
        }

        if (modelConfig == null) {
            throw new IllegalArgumentException("Model configuration not found for agent: " + config.getAgentName());
        }

        return modelConfigService.createChatModel(modelConfig);
    }

    private Object[] instantiateTools(AgentConfigDTO config, List<ToolDefinitionDTO> explicitToolDefinitions) {
        List<ToolDefinitionDTO> toolDefs = explicitToolDefinitions != null ? explicitToolDefinitions : config.getToolDefinitions();
        if ((toolDefs == null || toolDefs.isEmpty()) && config.getId() != null) {
            toolDefs = toolDefinitionService.getToolsByAgentId(config.getId());
        }
        if (toolDefs == null || toolDefs.isEmpty()) {
            return new Object[0];
        }
        return toolDefinitionService.instantiateTools(toolDefs);
    }

    private Object[] appendTaskTool(Object[] tools, Map<String, ReactAgent> subAgents) {
        Object[] merged = new Object[tools.length + 1];
        System.arraycopy(tools, 0, merged, 0, tools.length);
        merged[tools.length] = new TaskTool(subAgents, subAgentProgressBus, DEFAULT_MAX_PARALLEL_SUBAGENTS);
        return merged;
    }

    private Map<String, ReactAgent> buildSubAgentsFor(AgentConfigDTO mainConfig) {
        Map<String, ReactAgent> subAgents = new LinkedHashMap<>();
        List<SubAgentMappingDTO> mappings = getEffectiveMappings(mainConfig);

        for (SubAgentMappingDTO mapping : mappings) {
            if (!Boolean.TRUE.equals(mapping.getEnabled())
                    || mapping.getSubagentType() == null
                    || mapping.getSubagentType().isBlank()
                    || mapping.getTargetAgentId() == null) {
                continue;
            }

            AgentConfigDTO target = loadAgentConfig(mapping.getTargetAgentId());
            if (target == null) {
                logger.warn("Skip subagent mapping '{}' because target agent {} is missing",
                        mapping.getSubagentType(), mapping.getTargetAgentId());
                continue;
            }
            if ((target.getDescription() == null || target.getDescription().isBlank())
                    && mapping.getDescription() != null && !mapping.getDescription().isBlank()) {
                target.setDescription(mapping.getDescription());
            }
            if (Boolean.TRUE.equals(target.getEnableSubAgentInterceptor())) {
                logger.warn(
                        "Recursion guard enabled: subagent '{}' points to agent '{}' with subagent interceptor enabled; it will be disabled for this subagent instance",
                        mapping.getSubagentType(), target.getAgentName()
                );
            }

            List<ToolDefinitionDTO> toolsForSubAgent = resolveToolsForSubAgent(target, mainConfig, mapping);
            ReactAgent subAgent = createAgentInternal(target, false, false, toolsForSubAgent);
            subAgents.put(mapping.getSubagentType(), subAgent);
        }

        boolean includeGeneralPurpose = mainConfig.getIncludeGeneralPurpose() == null || mainConfig.getIncludeGeneralPurpose();
        if (includeGeneralPurpose && !subAgents.containsKey("general-purpose")) {
            try {
                List<ToolDefinitionDTO> gpTools = resolveGeneralPurposeTools(mainConfig);
                ReactAgent generalPurpose = createAgentInternal(mainConfig, false, false, gpTools);
                subAgents.put("general-purpose", generalPurpose);
            } catch (Exception e) {
                logger.warn("Failed to build general-purpose subagent for '{}': {}", mainConfig.getAgentName(), e.getMessage());
            }
        }

        return subAgents;
    }

    private List<SubAgentMappingDTO> getEffectiveMappings(AgentConfigDTO config) {
        if (config.getSubAgents() != null) {
            return config.getSubAgents();
        }
        if (config.getId() == null) {
            return List.of();
        }

        List<SubAgentMappingEntity> entities = subAgentMappingMapper.selectByAgentId(config.getId());
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }

        List<SubAgentMappingDTO> mappings = new ArrayList<>(entities.size());
        for (SubAgentMappingEntity entity : entities) {
            mappings.add(toSubAgentMappingDTO(entity));
        }
        return mappings;
    }

    private SubAgentMappingDTO toSubAgentMappingDTO(SubAgentMappingEntity entity) {
        SubAgentMappingDTO dto = new SubAgentMappingDTO();
        dto.setId(entity.getId());
        dto.setAgentId(entity.getAgentId());
        dto.setSubagentType(entity.getSubagentType());
        dto.setTargetAgentId(entity.getTargetAgentId());
        dto.setDescription(entity.getDescription());
        dto.setToolsPolicy(parseToolsPolicy(entity.getToolsPolicy()));
        dto.setCustomToolIds(parseCustomToolIds(entity.getCustomToolIds()));
        dto.setSortOrder(entity.getSortOrder());
        dto.setEnabled(entity.getEnabled());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private AgentConfigDTO loadAgentConfig(Long agentId) {
        AgentConfigEntity entity = agentConfigMapper.selectById(agentId);
        if (entity == null) {
            return null;
        }

        AgentConfigDTO dto = new AgentConfigDTO();
        dto.setId(entity.getId());
        dto.setAgentName(entity.getAgentName());
        dto.setDisplayName(entity.getDisplayName());
        dto.setDescription(entity.getDescription());
        dto.setModelId(entity.getModelId());
        dto.setSystemPrompt(entity.getSystemPrompt());
        dto.setMaxIterations(entity.getMaxIterations());
        dto.setTemperature(entity.getTemperature());
        dto.setEnableStreaming(entity.getEnableStreaming());
        dto.setIsActive(entity.getIsActive());
        dto.setEnableSubAgentInterceptor(entity.getEnableSubAgentInterceptor());
        dto.setIncludeGeneralPurpose(entity.getIncludeGeneralPurpose());
        dto.setSubAgentToolsPolicy(parseToolsPolicy(entity.getSubAgentToolsPolicy()));
        dto.setToolDefinitions(toolDefinitionService.getToolsByAgentId(agentId));
        return dto;
    }

    private List<ToolDefinitionDTO> resolveToolsForSubAgent(
            AgentConfigDTO targetConfig,
            AgentConfigDTO mainConfig,
            SubAgentMappingDTO mapping) {
        SubAgentToolsPolicy policy = resolvePolicy(mainConfig, mapping);
        if (policy == SubAgentToolsPolicy.CUSTOM) {
            Set<Long> customIds = mapping.getCustomToolIds() == null
                    ? Set.of()
                    : mapping.getCustomToolIds().stream().filter(Objects::nonNull).collect(Collectors.toSet());
            if (customIds.isEmpty()) {
                return List.of();
            }
            return toolDefinitionService.getActiveTools().stream()
                    .filter(t -> t.getId() != null && customIds.contains(t.getId()))
                    .collect(Collectors.toList());
        }

        List<ToolDefinitionDTO> inherited = targetConfig.getToolDefinitions();
        if (inherited != null) {
            return inherited;
        }
        if (targetConfig.getId() != null) {
            return toolDefinitionService.getToolsByAgentId(targetConfig.getId());
        }
        return List.of();
    }

    private List<ToolDefinitionDTO> resolveGeneralPurposeTools(AgentConfigDTO mainConfig) {
        SubAgentToolsPolicy defaultPolicy = mainConfig.getSubAgentToolsPolicy() != null
                ? mainConfig.getSubAgentToolsPolicy()
                : SubAgentToolsPolicy.INHERIT;
        if (defaultPolicy == SubAgentToolsPolicy.CUSTOM) {
            return List.of();
        }
        if (mainConfig.getToolDefinitions() != null) {
            return mainConfig.getToolDefinitions();
        }
        if (mainConfig.getId() != null) {
            return toolDefinitionService.getToolsByAgentId(mainConfig.getId());
        }
        return List.of();
    }

    private SubAgentToolsPolicy resolvePolicy(AgentConfigDTO mainConfig, SubAgentMappingDTO mapping) {
        if (mapping.getToolsPolicy() != null) {
            return mapping.getToolsPolicy();
        }
        if (mainConfig.getSubAgentToolsPolicy() != null) {
            return mainConfig.getSubAgentToolsPolicy();
        }
        return SubAgentToolsPolicy.INHERIT;
    }

    private SubAgentToolsPolicy parseToolsPolicy(String value) {
        if (value == null || value.isBlank()) {
            return SubAgentToolsPolicy.INHERIT;
        }
        try {
            return SubAgentToolsPolicy.valueOf(value);
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown tools policy '{}', fallback to INHERIT", value);
            return SubAgentToolsPolicy.INHERIT;
        }
    }

    private List<Long> parseCustomToolIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            logger.warn("Failed to parse custom tool ids JSON, fallback to empty: {}", json, e);
            return List.of();
        }
    }

    private List<String> parseBlockedToolNames(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
