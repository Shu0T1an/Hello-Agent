package cn.ts.web.service.impl;

import cn.ts.agent.mcp.McpManager;
import cn.ts.agent.mcp.model.McpConnection;
import cn.ts.web.agent.dto.ToolDefinitionDTO;
import cn.ts.web.agent.dto.ToolType;
import cn.ts.web.entity.ToolDefinitionEntity;
import cn.ts.web.mapper.ToolDefinitionMapper;
import cn.ts.web.service.ToolDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 工具定义服务实现
 */
@Service
public class ToolDefinitionServiceImpl implements ToolDefinitionService {

    private static final Logger logger = LoggerFactory.getLogger(ToolDefinitionServiceImpl.class);

    private final ToolDefinitionMapper mapper;
    private final ApplicationContext applicationContext;
    private final McpManager mcpManager;

    public ToolDefinitionServiceImpl(
            ToolDefinitionMapper mapper,
            ApplicationContext applicationContext,
            McpManager mcpManager) {
        this.mapper = mapper;
        this.applicationContext = applicationContext;
        this.mcpManager = mcpManager;
    }

    @Override
    @Transactional
    public ToolDefinitionDTO createTool(ToolDefinitionDTO dto) {
        // 检查工具名称是否已存在
        if (mapper.countByToolName(dto.getToolName()) > 0) {
            throw new IllegalArgumentException("Tool name already exists: " + dto.getToolName());
        }

        ToolDefinitionEntity entity = toEntity(dto);
        mapper.insert(entity);

        return toDTO(entity);
    }

    @Override
    @Transactional
    public ToolDefinitionDTO createOrUpdateTool(ToolDefinitionDTO dto) {
        Optional<ToolDefinitionEntity> existing = mapper.selectByToolName(dto.getToolName());

        if (existing.isPresent()) {
            // 更新现有工具
            ToolDefinitionEntity entity = existing.get();
            entity.setDisplayName(dto.getDisplayName());
            entity.setDescription(dto.getDescription());
            entity.setIsActive(dto.getIsActive());
            mapper.updateById(entity);
            return toDTO(entity);
        } else {
            // 创建新工具
            ToolDefinitionEntity entity = toEntity(dto);
            mapper.insert(entity);
            return toDTO(entity);
        }
    }

    @Override
    @Transactional
    public ToolDefinitionDTO updateTool(Long id, ToolDefinitionDTO dto) {
        ToolDefinitionEntity existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Tool not found with id: " + id);
        }

        // 检查工具名称是否已被其他记录使用
        if (mapper.countByToolNameExcludeId(dto.getToolName(), id) > 0) {
            throw new IllegalArgumentException("Tool name already exists: " + dto.getToolName());
        }

        existing.setDisplayName(dto.getDisplayName());
        existing.setDescription(dto.getDescription());
        existing.setIsActive(dto.getIsActive());

        mapper.updateById(existing);

        return toDTO(existing);
    }

    @Override
    @Transactional
    public void deleteTool(Long id) {
        // 检查是否被 Agent 引用
        // TODO: 添加外键检查逻辑

        mapper.deleteById(id);
    }

    @Override
    public ToolDefinitionDTO getToolById(Long id) {
        ToolDefinitionEntity entity = mapper.selectById(id);
        return entity != null ? toDTO(entity) : null;
    }

    @Override
    public ToolDefinitionDTO getToolByName(String toolName) {
        return mapper.selectByToolName(toolName)
                .map(this::toDTO)
                .orElse(null);
    }

    @Override
    public List<ToolDefinitionDTO> getAllTools() {
        return mapper.selectAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolDefinitionDTO> getActiveTools() {
        return mapper.selectActive().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolDefinitionDTO> getToolsByType(ToolType toolType) {
        return mapper.selectByType(toolType.name()).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolDefinitionDTO> getToolsByAgentId(Long agentConfigId) {
        return mapper.selectByAgentId(agentConfigId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Object[] instantiateTools(List<ToolDefinitionDTO> toolDefs) {
        // 使用 Set 去重，避免同一个 Bean 被重复添加
        // 一个类（如 SimpleTools）可能包含多个 @Tool 方法，但只需要实例化一次
        java.util.Set<String> instantiatedClasses = new java.util.HashSet<>();
        java.util.Set<String> instantiatedConnections = new java.util.HashSet<>();
        List<Object> tools = new ArrayList<>();

        for (ToolDefinitionDTO toolDef : toolDefs) {
            if (!toolDef.getIsActive()) {
                continue;
            }

            try {
                Object tool = instantiateTool(toolDef);
                if (tool != null) {
                    // 本地工具：按类名去重
                    // MCP 工具：按连接名去重（同一连接的所有工具在一个客户端中）
                    String key = toolDef.getToolType() == ToolType.LOCAL
                            ? toolDef.getClassName()
                            : toolDef.getMcpConnectionName();

                    boolean isDuplicate = toolDef.getToolType() == ToolType.LOCAL
                            ? !instantiatedClasses.add(key)
                            : !instantiatedConnections.add(key);

                    if (!isDuplicate) {
                        tools.add(tool);
                        logger.debug("Instantiated tool: {} (type: {})", toolDef.getToolName(), toolDef.getToolType());
                    } else {
                        logger.debug("Skipped duplicate tool: {} (key: {})", toolDef.getToolName(), key);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to instantiate tool: {}", toolDef.getToolName(), e);
            }
        }

        return tools.toArray();
    }

    /**
     * 实例化单个工具
     */
    private Object instantiateTool(ToolDefinitionDTO toolDef) {
        return switch (toolDef.getToolType()) {
            case LOCAL -> instantiateLocalTool(toolDef);
            case MCP -> instantiateMcpTool(toolDef);
        };
    }

    /**
     * 实例化本地工具（从 Spring 容器获取）
     */
    private Object instantiateLocalTool(ToolDefinitionDTO toolDef) {
        try {
            Class<?> clazz = Class.forName(toolDef.getClassName());
            return applicationContext.getBean(clazz);
        } catch (ClassNotFoundException e) {
            logger.error("Class not found for tool: {}", toolDef.getClassName(), e);
            return null;
        }
    }

    /**
     * 实例化 MCP 工具（从 McpManager 获取）
     */
    private Object instantiateMcpTool(ToolDefinitionDTO toolDef) {
        String connectionName = toolDef.getMcpConnectionName();

        return mcpManager.getConnection(connectionName)
                .map(McpConnection::getClient)
                .orElse(null);
    }

    @Override
    @Transactional
    public void disableToolsByConnection(String mcpConnectionName) {
        mapper.disableByMcpConnection(mcpConnectionName);
        logger.info("Disabled all tools for MCP connection: {}", mcpConnectionName);
    }

    @Override
    @Transactional
    public void syncMcpTools(String mcpConnectionName, List<McpToolInfo> mcpTools) {
        for (McpToolInfo toolInfo : mcpTools) {
            ToolDefinitionDTO dto = new ToolDefinitionDTO();
            dto.setToolName(mcpConnectionName + ":" + toolInfo.name());
            dto.setDisplayName(toolInfo.name());
            dto.setDescription(toolInfo.description());
            dto.setToolType(ToolType.MCP);
            dto.setMcpConnectionName(mcpConnectionName);
            dto.setMcpToolName(toolInfo.name());
            dto.setIsActive(true);

            createOrUpdateTool(dto);
        }

        logger.info("Synced {} tools for MCP connection: {}", mcpTools.size(), mcpConnectionName);
    }

    /**
     * 实体转 DTO
     */
    private ToolDefinitionDTO toDTO(ToolDefinitionEntity entity) {
        ToolDefinitionDTO dto = new ToolDefinitionDTO();
        dto.setId(entity.getId());
        dto.setToolName(entity.getToolName());
        dto.setDisplayName(entity.getDisplayName());
        dto.setDescription(entity.getDescription());
        dto.setToolType(ToolType.valueOf(entity.getToolType()));
        dto.setClassName(entity.getClassName());
        dto.setMcpConnectionName(entity.getMcpConnectionName());
        dto.setMcpToolName(entity.getMcpToolName());
        dto.setIsActive(entity.getIsActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    /**
     * DTO 转实体
     */
    private ToolDefinitionEntity toEntity(ToolDefinitionDTO dto) {
        ToolDefinitionEntity entity = new ToolDefinitionEntity();
        entity.setToolName(dto.getToolName());
        entity.setDisplayName(dto.getDisplayName());
        entity.setDescription(dto.getDescription());
        entity.setToolType(dto.getToolType().name());
        entity.setClassName(dto.getClassName());
        entity.setMcpConnectionName(dto.getMcpConnectionName());
        entity.setMcpToolName(dto.getMcpToolName());
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        return entity;
    }
}
