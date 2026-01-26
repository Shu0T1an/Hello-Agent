package cn.ts.web.service.impl;

import cn.ts.agent.core.ReactAgent;
import cn.ts.web.factory.AgentFactory;
import cn.ts.web.dto.agent.AgentConfigDTO;
import cn.ts.web.dto.agent.ToolDefinitionDTO;
import cn.ts.web.entity.AgentConfigEntity;
import cn.ts.web.mapper.AgentConfigMapper;
import cn.ts.web.mapper.AgentToolMappingMapper;
import cn.ts.web.service.AgentConfigService;
import cn.ts.web.service.AgentExecutionService;
import cn.ts.web.service.ToolDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent 配置服务实现
 */
@Service
public class AgentConfigServiceImpl implements AgentConfigService {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfigServiceImpl.class);

    private final AgentFactory agentFactory;
    private final AgentExecutionService agentExecutionService;
    private final AgentConfigMapper agentConfigMapper;
    private final AgentToolMappingMapper agentToolMappingMapper;
    private final ToolDefinitionService toolDefinitionService;

    // Agent 注册表（用于热重载）
    private final Map<String, ReactAgent> agentRegistry = new ConcurrentHashMap<>();

    public AgentConfigServiceImpl(
            AgentFactory agentFactory,
            AgentExecutionService agentExecutionService,
            AgentConfigMapper agentConfigMapper,
            AgentToolMappingMapper agentToolMappingMapper,
            ToolDefinitionService toolDefinitionService) {
        this.agentFactory = agentFactory;
        this.agentExecutionService = agentExecutionService;
        this.agentConfigMapper = agentConfigMapper;
        this.agentToolMappingMapper = agentToolMappingMapper;
        this.toolDefinitionService = toolDefinitionService;
    }

    @Override
    @Transactional
    public AgentConfigDTO createAgent(AgentConfigDTO dto) {
        // 检查 Agent 名称是否已存在
        if (agentConfigMapper.countByAgentName(dto.getAgentName()) > 0) {
            throw new IllegalArgumentException("Agent name already exists: " + dto.getAgentName());
        }

        // 转换为实体并插入
        AgentConfigEntity entity = toEntity(dto);
        agentConfigMapper.insert(entity);

        // 插入工具关联
        if (dto.getToolIds() != null && !dto.getToolIds().isEmpty()) {
            dto.getToolIds().forEach(toolId -> {
                var mapping = new cn.ts.web.entity.AgentToolMappingEntity();
                mapping.setAgentConfigId(entity.getId());
                mapping.setToolDefinitionId(toolId);
                agentToolMappingMapper.insert(mapping);
            });
        }

        // 重新加载完整的配置（包含工具关联）
        AgentConfigDTO result = getAgentById(entity.getId());

        // 如果是激活状态，自动组装并注册
        if (result.getIsActive() != null && result.getIsActive()) {
            try {
                ReactAgent agent = assembleAgent(result);
                registerAgentToExecutionService(result.getAgentName(), agent);
                logger.info("Agent '{}' registered successfully", result.getAgentName());
            } catch (Exception e) {
                logger.error("Failed to register agent '{}': {}", result.getAgentName(), e.getMessage());
                // 不影响创建，只是无法注册
            }
        }

        return result;
    }

    @Override
    @Transactional
    public AgentConfigDTO updateAgent(Long id, AgentConfigDTO dto) {
        // 检查是否存在
        AgentConfigEntity existing = agentConfigMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Agent not found with id: " + id);
        }

        // 检查 Agent 名称是否已被其他记录使用
        if (agentConfigMapper.countByAgentNameExcludeId(dto.getAgentName(), id) > 0) {
            throw new IllegalArgumentException("Agent name already exists: " + dto.getAgentName());
        }

        // 更新实体
        existing.setDisplayName(dto.getDisplayName());
        existing.setDescription(dto.getDescription());
        existing.setModelId(dto.getModelId());
        existing.setSystemPrompt(dto.getSystemPrompt());
        existing.setMaxIterations(dto.getMaxIterations());
        existing.setTemperature(dto.getTemperature());
        existing.setEnableStreaming(dto.getEnableStreaming());
        // 只有明确提供 isActive 时才更新，否则保持原值
        if (dto.getIsActive() != null) {
            existing.setIsActive(dto.getIsActive());
        }

        agentConfigMapper.updateById(existing);

        // 更新工具关联
        agentToolMappingMapper.deleteByAgentId(id);
        if (dto.getToolIds() != null && !dto.getToolIds().isEmpty()) {
            dto.getToolIds().forEach(toolId -> {
                var mapping = new cn.ts.web.entity.AgentToolMappingEntity();
                mapping.setAgentConfigId(id);
                mapping.setToolDefinitionId(toolId);
                agentToolMappingMapper.insert(mapping);
            });
        }

        // 重新加载完整的配置
        AgentConfigDTO result = getAgentById(id);

        // 热重载 Agent（只有激活的才重载）
        if (result.getIsActive() != null && result.getIsActive()) {
            hotReloadAgent(result.getAgentName());
        } else {
            // 如果停用，注销 Agent
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

        // 先注销 Agent
        unregisterAgentFromExecutionService(entity.getAgentName());

        // 删除工具关联（外键会自动级联删除）
        agentConfigMapper.deleteById(id);

        logger.info("Agent '{}' deleted", entity.getAgentName());
    }

    @Override
    public AgentConfigDTO getAgentById(Long id) {
        AgentConfigEntity entity = agentConfigMapper.selectById(id);
        if (entity == null) {
            return null;
        }

        // 加载关联的工具
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
                    // 加载关联的工具
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

        // 重新加载并注册
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

        // 注销 Agent
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
     * 热重载 Agent（不影响正在执行的请求）
     */
    private void hotReloadAgent(String agentName) {
        logger.info("Hot reloading agent: {}", agentName);

        // 1. 从数据库获取最新配置
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
            // 2. 重新组装 Agent
            ReactAgent newAgent = assembleAgent(config);

            // 3. 替换注册表中的 Agent
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
     * 实体转 DTO
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
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // 转换工具列表
        if (entity.getToolDefinitions() != null) {
            List<ToolDefinitionDTO> tools = entity.getToolDefinitions().stream()
                    .map(te -> {
                        ToolDefinitionDTO t = new ToolDefinitionDTO();
                        t.setId(te.getId());
                        t.setToolName(te.getToolName());
                        t.setDisplayName(te.getDisplayName());
                        t.setDescription(te.getDescription());
                        t.setToolType(cn.ts.web.dto.agent.ToolType.valueOf(te.getToolType()));
                        t.setClassName(te.getClassName());
                        t.setMcpConnectionName(te.getMcpConnectionName());
                        t.setMcpToolName(te.getMcpToolName());
                        t.setIsActive(te.getIsActive());
                        return t;
                    })
                    .collect(Collectors.toList());
            dto.setToolDefinitions(tools);
            // 同时设置 toolIds，方便前端使用
            dto.setToolIds(tools.stream().map(ToolDefinitionDTO::getId).collect(Collectors.toList()));
        }

        return dto;
    }

    /**
     * DTO 转实体
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
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : "system");
        return entity;
    }
}
