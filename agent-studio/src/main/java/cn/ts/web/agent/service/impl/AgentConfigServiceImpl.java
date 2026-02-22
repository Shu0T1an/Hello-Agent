package cn.ts.web.agent.service.impl;

import cn.ts.agent.core.ReactAgent;
import cn.ts.web.agent.factory.AgentFactory;
import cn.ts.web.agent.dto.AgentConfigDTO;
import cn.ts.web.agent.dto.SubAgentMappingDTO;
import cn.ts.web.agent.dto.SubAgentToolsPolicy;
import cn.ts.web.agent.dto.ToolDefinitionDTO;
import cn.ts.web.agent.entity.AgentConfigEntity;
import cn.ts.web.agent.entity.SubAgentMappingEntity;
import cn.ts.web.agent.mapper.AgentConfigMapper;
import cn.ts.web.agent.mapper.AgentToolMappingMapper;
import cn.ts.web.agent.mapper.SubAgentMappingMapper;
import cn.ts.web.agent.service.AgentConfigService;
import cn.ts.web.agent.service.AgentExecutionService;
import cn.ts.web.service.ToolDefinitionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent 閰嶇疆鏈嶅姟瀹炵幇
 */
@Service
public class AgentConfigServiceImpl implements AgentConfigService {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfigServiceImpl.class);

    private final AgentFactory agentFactory;
    private final AgentExecutionService agentExecutionService;
    private final AgentConfigMapper agentConfigMapper;
    private final AgentToolMappingMapper agentToolMappingMapper;
    private final SubAgentMappingMapper subAgentMappingMapper;
    private final ToolDefinitionService toolDefinitionService;
    private final ObjectMapper objectMapper;

    // Agent 娉ㄥ唽琛紙鐢ㄤ簬鐑噸杞斤級
    private final Map<String, ReactAgent> agentRegistry = new ConcurrentHashMap<>();

    public AgentConfigServiceImpl(
            AgentFactory agentFactory,
            AgentExecutionService agentExecutionService,
            AgentConfigMapper agentConfigMapper,
            AgentToolMappingMapper agentToolMappingMapper,
            SubAgentMappingMapper subAgentMappingMapper,
            ToolDefinitionService toolDefinitionService) {
        this.agentFactory = agentFactory;
        this.agentExecutionService = agentExecutionService;
        this.agentConfigMapper = agentConfigMapper;
        this.agentToolMappingMapper = agentToolMappingMapper;
        this.subAgentMappingMapper = subAgentMappingMapper;
        this.toolDefinitionService = toolDefinitionService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @Transactional
    public AgentConfigDTO createAgent(AgentConfigDTO dto) {
        // 妫€鏌?Agent 鍚嶇О鏄惁宸插瓨鍦?
        if (agentConfigMapper.countByAgentName(dto.getAgentName()) > 0) {
            throw new IllegalArgumentException("Agent name already exists: " + dto.getAgentName());
        }

        // 杞崲涓哄疄浣撳苟鎻掑叆
        AgentConfigEntity entity = toEntity(dto);
        agentConfigMapper.insert(entity);

        // 鎻掑叆宸ュ叿鍏宠仈
        if (dto.getToolIds() != null && !dto.getToolIds().isEmpty()) {
            dto.getToolIds().forEach(toolId -> {
                var mapping = new cn.ts.web.agent.entity.AgentToolMappingEntity();
                mapping.setAgentConfigId(entity.getId());
                mapping.setToolDefinitionId(toolId);
                agentToolMappingMapper.insert(mapping);
            });
        }
        saveSubAgentMappings(entity.getId(), dto.getSubAgents());

        // 閲嶆柊鍔犺浇瀹屾暣鐨勯厤缃紙鍖呭惈宸ュ叿鍏宠仈锛?
        AgentConfigDTO result = getAgentById(entity.getId());

        // 濡傛灉鏄縺娲荤姸鎬侊紝鑷姩缁勮骞舵敞鍐?
        if (result.getIsActive() != null && result.getIsActive()) {
            try {
                ReactAgent agent = assembleAgent(result);
                registerAgentToExecutionService(result.getAgentName(), agent);
                logger.info("Agent '{}' registered successfully", result.getAgentName());
            } catch (Exception e) {
                logger.error("Failed to register agent '{}': {}", result.getAgentName(), e.getMessage());
                // 涓嶅奖鍝嶅垱寤猴紝鍙槸鏃犳硶娉ㄥ唽
            }
        }

        return result;
    }

    @Override
    @Transactional
    public AgentConfigDTO updateAgent(Long id, AgentConfigDTO dto) {
        // 妫€鏌ユ槸鍚﹀瓨鍦?
        AgentConfigEntity existing = agentConfigMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Agent not found with id: " + id);
        }

        // 妫€鏌?Agent 鍚嶇О鏄惁宸茶鍏朵粬璁板綍浣跨敤
        if (agentConfigMapper.countByAgentNameExcludeId(dto.getAgentName(), id) > 0) {
            throw new IllegalArgumentException("Agent name already exists: " + dto.getAgentName());
        }

        // 鏇存柊瀹炰綋
        existing.setDisplayName(dto.getDisplayName());
        existing.setDescription(dto.getDescription());
        existing.setModelId(dto.getModelId());
        existing.setSystemPrompt(dto.getSystemPrompt());
        existing.setMaxIterations(dto.getMaxIterations());
        existing.setTemperature(dto.getTemperature());
        existing.setEnableStreaming(dto.getEnableStreaming());
        existing.setEnableSubAgentInterceptor(dto.getEnableSubAgentInterceptor());
        existing.setIncludeGeneralPurpose(dto.getIncludeGeneralPurpose());
        if (dto.getSubAgentToolsPolicy() != null) {
            existing.setSubAgentToolsPolicy(dto.getSubAgentToolsPolicy().name());
        }
        // 鍙湁鏄庣‘鎻愪緵 isActive 鏃舵墠鏇存柊锛屽惁鍒欎繚鎸佸師鍊?
        if (dto.getIsActive() != null) {
            existing.setIsActive(dto.getIsActive());
        }

        agentConfigMapper.updateById(existing);

        // 鏇存柊宸ュ叿鍏宠仈
        agentToolMappingMapper.deleteByAgentId(id);
        if (dto.getToolIds() != null && !dto.getToolIds().isEmpty()) {
            dto.getToolIds().forEach(toolId -> {
                var mapping = new cn.ts.web.agent.entity.AgentToolMappingEntity();
                mapping.setAgentConfigId(id);
                mapping.setToolDefinitionId(toolId);
                agentToolMappingMapper.insert(mapping);
            });
        }
        saveSubAgentMappings(id, dto.getSubAgents());

        // 閲嶆柊鍔犺浇瀹屾暣鐨勯厤缃?
        AgentConfigDTO result = getAgentById(id);

        // 鐑噸杞?Agent锛堝彧鏈夋縺娲荤殑鎵嶉噸杞斤級
        if (result.getIsActive() != null && result.getIsActive()) {
            hotReloadAgent(result.getAgentName());
        } else {
            // 濡傛灉鍋滅敤锛屾敞閿€ Agent
            unregisterAgentFromExecutionService(result.getAgentName());
        }

        return result;
    }

    @Override
    @Transactional
    public void deleteAgent(Long id) {
        AgentConfigEntity entity = agentConfigMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Agent not found with id: " + id);
        }

        // 鍏堟敞閿€ Agent
        unregisterAgentFromExecutionService(entity.getAgentName());

        // 鍒犻櫎宸ュ叿鍏宠仈锛堝閿細鑷姩绾ц仈鍒犻櫎锛?
        agentToolMappingMapper.deleteByAgentId(id);
        subAgentMappingMapper.deleteByAgentId(id);
        agentConfigMapper.deleteById(id);

        logger.info("Agent '{}' deleted", entity.getAgentName());
    }

    @Override
    public AgentConfigDTO getAgentById(Long id) {
        AgentConfigEntity entity = agentConfigMapper.selectById(id);
        if (entity == null) {
            return null;
        }

        // 鍔犺浇鍏宠仈鐨勫伐鍏?
        List<ToolDefinitionDTO> tools = toolDefinitionService.getToolsByAgentId(id);
        entity.setToolDefinitions(
                tools.stream()
                        .map(t -> {
                            cn.ts.web.entity.ToolDefinitionEntity te = new cn.ts.web.entity.ToolDefinitionEntity();
                            te.setId(t.getId());
                            te.setToolName(t.getToolName());
                            te.setDisplayName(t.getDisplayName());
                            te.setDescription(t.getDescription());
                            te.setToolType(t.getToolType().name());
                            te.setClassName(t.getClassName());
                            te.setMcpConnectionName(t.getMcpConnectionName());
                            te.setMcpToolName(t.getMcpToolName());
                            te.setIsActive(t.getIsActive());
                            return te;
                        })
                        .collect(Collectors.toList())
        );

        return toDTO(entity);
    }

    @Override
    public AgentConfigDTO getAgentByName(String agentName) {
        return agentConfigMapper.selectByAgentName(agentName)
                .map(entity -> {
                    // 鍔犺浇鍏宠仈鐨勫伐鍏?
                    List<ToolDefinitionDTO> tools = toolDefinitionService.getToolsByAgentId(entity.getId());
                    entity.setToolDefinitions(
                            tools.stream()
                                    .map(t -> {
                                        cn.ts.web.entity.ToolDefinitionEntity te = new cn.ts.web.entity.ToolDefinitionEntity();
                                        te.setId(t.getId());
                                        te.setToolName(t.getToolName());
                                        te.setDisplayName(t.getDisplayName());
                                        te.setDescription(t.getDescription());
                                        te.setToolType(t.getToolType().name());
                                        te.setClassName(t.getClassName());
                                        te.setMcpConnectionName(t.getMcpConnectionName());
                                        te.setMcpToolName(t.getMcpToolName());
                                        te.setIsActive(t.getIsActive());
                                        return te;
                                    })
                                    .collect(Collectors.toList())
                    );
                    return toDTO(entity);
                })
                .orElse(null);
    }

    @Override
    public List<AgentConfigDTO> getAllAgents() {
        return agentConfigMapper.selectAll().stream()
                .map(entity -> toDTO(entity))
                .collect(Collectors.toList());
    }

    @Override
    public List<AgentConfigDTO> getActiveAgents() {
        return agentConfigMapper.selectActive().stream()
                .map(entity -> toDTO(entity))
                .collect(Collectors.toList());
    }

    @Override
    public ReactAgent assembleAgent(AgentConfigDTO config) {
        return agentFactory.createAgent(config);
    }

    @Override
    public void registerAgentToExecutionService(String agentName, ReactAgent agent) {
        agentExecutionService.registerGraph(agentName, agent.getGraph());
        agentRegistry.put(agentName, agent);
        logger.info("Agent '{}' registered to execution service", agentName);
    }

    @Override
    public void unregisterAgentFromExecutionService(String agentName) {
        agentExecutionService.unregisterAgent(agentName);
        agentRegistry.remove(agentName);
        logger.info("Agent '{}' unregistered from execution service", agentName);
    }

    @Override
    public void registerAllActiveAgents() {
        List<AgentConfigDTO> activeAgents = getActiveAgents();
        logger.info("Registering {} active agents...", activeAgents.size());

        for (AgentConfigDTO config : activeAgents) {
            try {
                ReactAgent agent = assembleAgent(config);
                registerAgentToExecutionService(config.getAgentName(), agent);
                logger.info("Agent '{}' registered", config.getAgentName());
            } catch (Exception e) {
                logger.error("Failed to register agent '{}': {}", config.getAgentName(), e.getMessage());
            }
        }

        logger.info("Agent registration completed. Total: {}", agentRegistry.size());
    }

    @Override
    @Transactional
    public void activateAgent(Long id) {
        AgentConfigEntity entity = agentConfigMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Agent not found with id: " + id);
        }

        entity.setIsActive(true);
        agentConfigMapper.updateById(entity);

        // 閲嶆柊鍔犺浇骞舵敞鍐?
        AgentConfigDTO config = getAgentById(id);
        ReactAgent agent = assembleAgent(config);
        registerAgentToExecutionService(config.getAgentName(), agent);

        logger.info("Agent '{}' activated", config.getAgentName());
    }

    @Override
    @Transactional
    public void deactivateAgent(Long id) {
        AgentConfigEntity entity = agentConfigMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Agent not found with id: " + id);
        }

        entity.setIsActive(false);
        agentConfigMapper.updateById(entity);

        // 娉ㄩ攢 Agent
        unregisterAgentFromExecutionService(entity.getAgentName());

        logger.info("Agent '{}' deactivated", entity.getAgentName());
    }

    @Override
    public void reloadAgent(String agentName) {
        hotReloadAgent(agentName);
    }

    @Override
    public void reloadAllAgents() {
        logger.info("Reloading all agents...");

        List<String> agentNames = agentRegistry.keySet().stream().toList();
        for (String agentName : agentNames) {
            try {
                hotReloadAgent(agentName);
            } catch (Exception e) {
                logger.error("Failed to reload agent '{}': {}", agentName, e.getMessage());
            }
        }

        logger.info("All agents reloaded");
    }

    /**
     * 鐑噸杞?Agent锛堜笉褰卞搷姝ｅ湪鎵ц鐨勮姹傦級
     */
    private void hotReloadAgent(String agentName) {
        logger.info("Hot reloading agent: {}", agentName);

        // 1. 浠庢暟鎹簱鑾峰彇鏈€鏂伴厤缃?
        AgentConfigDTO config = getAgentByName(agentName);
        if (config == null) {
            logger.warn("Agent '{}' not found in database", agentName);
            return;
        }

        if (!config.getIsActive()) {
            logger.info("Agent '{}' is not active, skipping reload", agentName);
            return;
        }

        try {
            // 2. 閲嶆柊缁勮 Agent
            ReactAgent newAgent = assembleAgent(config);

            // 3. 鏇挎崲娉ㄥ唽琛ㄤ腑鐨?Agent
            ReactAgent oldAgent = agentRegistry.get(agentName);
            if (oldAgent != null) {
                agentExecutionService.unregisterAgent(agentName);
            }
            agentExecutionService.registerGraph(agentName, newAgent.getGraph());
            agentRegistry.put(agentName, newAgent);

            logger.info("Agent '{}' hot reloaded successfully", agentName);
        } catch (Exception e) {
            logger.error("Failed to hot reload agent '{}': {}", agentName, e.getMessage(), e);
            throw new RuntimeException("Failed to reload agent: " + agentName, e);
        }
    }

    /**
     * 瀹炰綋杞?DTO
     */
    private AgentConfigDTO toDTO(AgentConfigEntity entity) {
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
        dto.setSubAgents(loadSubAgentMappings(entity.getId()));
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // 杞崲宸ュ叿鍒楄〃
        if (entity.getToolDefinitions() != null) {
            List<ToolDefinitionDTO> tools = entity.getToolDefinitions().stream()
                    .map(te -> {
                        ToolDefinitionDTO t = new ToolDefinitionDTO();
                        t.setId(te.getId());
                        t.setToolName(te.getToolName());
                        t.setDisplayName(te.getDisplayName());
                        t.setDescription(te.getDescription());
                        t.setToolType(cn.ts.web.agent.dto.ToolType.valueOf(te.getToolType()));
                        t.setClassName(te.getClassName());
                        t.setMcpConnectionName(te.getMcpConnectionName());
                        t.setMcpToolName(te.getMcpToolName());
                        t.setIsActive(te.getIsActive());
                        return t;
                    })
                    .collect(Collectors.toList());
            dto.setToolDefinitions(tools);
            // 鍚屾椂璁剧疆 toolIds锛屾柟渚垮墠绔娇鐢?
            dto.setToolIds(tools.stream().map(ToolDefinitionDTO::getId).collect(Collectors.toList()));
        }

        return dto;
    }

    /**
     * DTO 杞疄浣?
     */
    private AgentConfigEntity toEntity(AgentConfigDTO dto) {
        AgentConfigEntity entity = new AgentConfigEntity();
        entity.setAgentName(dto.getAgentName());
        entity.setDisplayName(dto.getDisplayName());
        entity.setDescription(dto.getDescription());
        entity.setModelId(dto.getModelId());
        entity.setSystemPrompt(dto.getSystemPrompt());
        entity.setMaxIterations(dto.getMaxIterations() != null ? dto.getMaxIterations() : 10);
        entity.setTemperature(dto.getTemperature());
        entity.setEnableStreaming(dto.getEnableStreaming() != null ? dto.getEnableStreaming() : true);
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        entity.setEnableSubAgentInterceptor(dto.getEnableSubAgentInterceptor() != null ? dto.getEnableSubAgentInterceptor() : false);
        entity.setIncludeGeneralPurpose(dto.getIncludeGeneralPurpose() != null ? dto.getIncludeGeneralPurpose() : true);
        entity.setSubAgentToolsPolicy(
                dto.getSubAgentToolsPolicy() != null ? dto.getSubAgentToolsPolicy().name() : SubAgentToolsPolicy.INHERIT.name()
        );
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : "system");
        return entity;
    }

    private SubAgentToolsPolicy parseToolsPolicy(String value) {
        if (value == null || value.isBlank()) {
            return SubAgentToolsPolicy.INHERIT;
        }
        try {
            return SubAgentToolsPolicy.valueOf(value);
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown subAgentToolsPolicy '{}', fallback to INHERIT", value);
            return SubAgentToolsPolicy.INHERIT;
        }
    }

    private void saveSubAgentMappings(Long agentId, List<SubAgentMappingDTO> mappings) {
        subAgentMappingMapper.deleteByAgentId(agentId);
        if (mappings == null || mappings.isEmpty()) {
            return;
        }

        int index = 0;
        for (SubAgentMappingDTO mapping : mappings) {
            if (mapping == null || mapping.getSubagentType() == null || mapping.getSubagentType().isBlank()
                    || mapping.getTargetAgentId() == null) {
                continue;
            }

            SubAgentMappingEntity entity = new SubAgentMappingEntity();
            entity.setAgentId(agentId);
            entity.setSubagentType(mapping.getSubagentType());
            entity.setTargetAgentId(mapping.getTargetAgentId());
            entity.setDescription(mapping.getDescription());
            SubAgentToolsPolicy toolsPolicy = mapping.getToolsPolicy() != null
                    ? mapping.getToolsPolicy()
                    : SubAgentToolsPolicy.INHERIT;
            entity.setToolsPolicy(toolsPolicy.name());
            entity.setCustomToolIds(toJson(mapping.getCustomToolIds()));
            entity.setSortOrder(mapping.getSortOrder() != null ? mapping.getSortOrder() : index);
            entity.setEnabled(mapping.getEnabled() != null ? mapping.getEnabled() : true);
            subAgentMappingMapper.insert(entity);
            index++;
        }
    }

    private List<SubAgentMappingDTO> loadSubAgentMappings(Long agentId) {
        if (agentId == null) {
            return List.of();
        }
        List<SubAgentMappingEntity> entities = subAgentMappingMapper.selectByAgentId(agentId);
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<SubAgentMappingDTO> result = new ArrayList<>(entities.size());
        for (SubAgentMappingEntity entity : entities) {
            result.add(toSubAgentDTO(entity));
        }
        return result;
    }

    private SubAgentMappingDTO toSubAgentDTO(SubAgentMappingEntity entity) {
        SubAgentMappingDTO dto = new SubAgentMappingDTO();
        dto.setId(entity.getId());
        dto.setAgentId(entity.getAgentId());
        dto.setSubagentType(entity.getSubagentType());
        dto.setTargetAgentId(entity.getTargetAgentId());
        dto.setDescription(entity.getDescription());
        dto.setToolsPolicy(parseToolsPolicy(entity.getToolsPolicy()));
        dto.setCustomToolIds(fromJsonArray(entity.getCustomToolIds()));
        dto.setSortOrder(entity.getSortOrder());
        dto.setEnabled(entity.getEnabled());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private String toJson(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            logger.warn("Failed to serialize custom tool ids, fallback to []", e);
            return "[]";
        }
    }

    private List<Long> fromJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            logger.warn("Failed to parse custom tool ids JSON, fallback to empty list: {}", json, e);
            return List.of();
        }
    }
}


