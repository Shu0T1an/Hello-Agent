package cn.ts.web.mapper;

import cn.ts.web.entity.AgentConfigEntity;
import cn.ts.web.entity.AgentToolMappingEntity;
import cn.ts.web.entity.ModelConfigEntity;
import cn.ts.web.entity.ToolDefinitionEntity;
import cn.ts.web.entity.McpConnectionConfigEntity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatis + PostgreSQL 集成测试
 * <p>
 * 验证 MyBatis Mapper 与 PostgreSQL 数据库的交互是否正常
 * </p>
 *
 * @author tianshuo
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional // 每个测试方法执行后自动回滚，保证测试数据隔离
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MyBatisPostgreSQLIntegrationTest {

    @Autowired
    private ModelConfigMapper modelConfigMapper;

    @Autowired
    private ToolDefinitionMapper toolDefinitionMapper;

    @Autowired
    private AgentConfigMapper agentConfigMapper;

    @Autowired
    private AgentToolMappingMapper agentToolMappingMapper;

    @Autowired
    private McpConnectionConfigMapper mcpConnectionConfigMapper;

    /**
     * 测试前检查数据库连接
     */
    @Test
    @Order(1)
    @DisplayName("1. 验证数据库连接")
    void testDatabaseConnection() {
        // 执行简单查询验证数据库连接
        List<ModelConfigEntity> allModels = modelConfigMapper.selectAll();
        assertNotNull(allModels);
        System.out.println("✓ 数据库连接成功，当前模型配置数量: " + allModels.size());
    }

    // ==================== ModelConfigMapper 测试 ====================

    @Test
    @Order(10)
    @DisplayName("10. 创建模型配置")
    void testCreateModelConfig() {
        // Arrange
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setModelName("test-gpt4");
        entity.setDisplayName("GPT-4 Test Model");
        entity.setProvider("openai");
        entity.setModelId("gpt-4-turbo");
        entity.setBaseUrl("https://api.openai.com/v1");
        entity.setApiKeyEncrypted("encrypted_api_key_12345");
        entity.setIsActive(true);

        // Act
        int result = modelConfigMapper.insert(entity);

        // Assert
        assertEquals(1, result);
        assertNotNull(entity.getId());

        ModelConfigEntity saved = modelConfigMapper.selectById(entity.getId());
        assertNotNull(saved);
        assertEquals("test-gpt4", saved.getModelName());
        assertEquals("GPT-4 Test Model", saved.getDisplayName());
        assertEquals("openai", saved.getProvider());

        System.out.println("✓ 创建模型配置成功，ID: " + entity.getId());
    }

    @Test
    @Order(11)
    @DisplayName("11. 查询所有模型配置")
    void testGetAllModelConfigs() {
        // Arrange
        ModelConfigEntity entity = createTestModelConfig("query-all-test");

        // Act
        List<ModelConfigEntity> list = modelConfigMapper.selectAll();

        // Assert
        assertNotNull(list);
        assertTrue(list.size() >= 1);
        System.out.println("✓ 查询到 " + list.size() + " 个模型配置");
    }

    @Test
    @Order(12)
    @DisplayName("12. 根据模型名称查询")
    void testGetModelByName() {
        // Arrange
        String modelName = "get-by-name-test";
        createTestModelConfig(modelName);

        // Act
        Optional<ModelConfigEntity> result = modelConfigMapper.selectByModelName(modelName);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(modelName, result.get().getModelName());
        System.out.println("✓ 根据模型名称查询成功");
    }

    @Test
    @Order(13)
    @DisplayName("13. 更新模型配置")
    void testUpdateModelConfig() {
        // Arrange
        ModelConfigEntity entity = createTestModelConfig("update-test");
        Long id = entity.getId();

        // Act
        entity.setDisplayName("Updated Display Name");
        int result = modelConfigMapper.updateById(entity);

        // Assert
        assertEquals(1, result);

        ModelConfigEntity updated = modelConfigMapper.selectById(id);
        assertEquals("Updated Display Name", updated.getDisplayName());
        System.out.println("✓ 更新模型配置成功");
    }

    @Test
    @Order(14)
    @DisplayName("14. 删除模型配置")
    void testDeleteModelConfig() {
        // Arrange
        ModelConfigEntity entity = createTestModelConfig("delete-test");
        Long id = entity.getId();

        // Act
        int result = modelConfigMapper.deleteById(id);

        // Assert
        assertEquals(1, result);
        ModelConfigEntity deleted = modelConfigMapper.selectById(id);
        assertNull(deleted);
        System.out.println("✓ 删除模型配置成功");
    }

    // ==================== ToolDefinitionMapper 测试 ====================

    @Test
    @Order(20)
    @DisplayName("20. 创建本地工具定义")
    void testCreateLocalToolDefinition() {
        // Arrange
        ToolDefinitionEntity entity = new ToolDefinitionEntity();
        entity.setToolName("test-local-tool");
        entity.setDisplayName("Test Local Tool");
        entity.setDescription("A test local tool");
        entity.setToolType("LOCAL");
        entity.setClassName("cn.ts.web.tool.TestTool");
        entity.setIsActive(true);

        // Act
        int result = toolDefinitionMapper.insert(entity);

        // Assert
        assertEquals(1, result);
        assertNotNull(entity.getId());
        System.out.println("✓ 创建本地工具定义成功，ID: " + entity.getId());
    }

    @Test
    @Order(21)
    @DisplayName("21. 创建 MCP 工具定义")
    void testCreateMcpToolDefinition() {
        // Arrange
        ToolDefinitionEntity entity = new ToolDefinitionEntity();
        entity.setToolName("test-mcp-tool");
        entity.setDisplayName("Test MCP Tool");
        entity.setDescription("A test MCP tool");
        entity.setToolType("MCP");
        entity.setMcpConnectionName("test-connection");
        entity.setMcpToolName("mcp_tool_name");
        entity.setIsActive(true);

        // Act
        int result = toolDefinitionMapper.insert(entity);

        // Assert
        assertEquals(1, result);
        assertNotNull(entity.getId());
        System.out.println("✓ 创建 MCP 工具定义成功，ID: " + entity.getId());
    }

    @Test
    @Order(22)
    @DisplayName("22. 根据工具类型查询")
    void testGetToolsByType() {
        // Arrange
        createTestToolDefinition("local-tool-1", "LOCAL");
        createTestToolDefinition("mcp-tool-1", "MCP");

        // Act
        List<ToolDefinitionEntity> localTools = toolDefinitionMapper.selectByType("LOCAL");
        List<ToolDefinitionEntity> mcpTools = toolDefinitionMapper.selectByType("MCP");

        // Assert
        assertNotNull(localTools);
        assertNotNull(mcpTools);
        assertTrue(localTools.size() >= 1);
        assertTrue(mcpTools.size() >= 1);
        System.out.println("✓ LOCAL 工具: " + localTools.size() + ", MCP 工具: " + mcpTools.size());
    }

    // ==================== AgentConfigMapper 测试 ====================

    @Test
    @Order(30)
    @DisplayName("30. 创建 Agent 配置")
    void testCreateAgentConfig() {
        // Arrange
        ModelConfigEntity model = createTestModelConfig("agent-model-ref");
        AgentConfigEntity entity = new AgentConfigEntity();
        entity.setAgentName("test-agent");
        entity.setDisplayName("Test Agent");
        entity.setDescription("A test agent");
        entity.setModelId(model.getId());
        entity.setSystemPrompt("You are a helpful assistant");
        entity.setMaxIterations(10);
        entity.setTemperature(new BigDecimal("0.7"));
        entity.setEnableStreaming(true);
        entity.setIsActive(true);
        entity.setCreatedBy("test-user");

        // Act
        int result = agentConfigMapper.insert(entity);

        // Assert
        assertEquals(1, result);
        assertNotNull(entity.getId());
        System.out.println("✓ 创建 Agent 配置成功，ID: " + entity.getId());
    }

    @Test
    @Order(31)
    @DisplayName("31. 根据 Agent 名称查询")
    void testGetAgentByName() {
        // Arrange
        String agentName = "get-agent-by-name";
        createTestAgentConfig(agentName);

        // Act
        Optional<AgentConfigEntity> result = agentConfigMapper.selectByAgentName(agentName);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(agentName, result.get().getAgentName());
        System.out.println("✓ 根据 Agent 名称查询成功");
    }

    // ==================== AgentToolMappingMapper 测试 ====================

    @Test
    @Order(40)
    @DisplayName("40. 创建 Agent-工具关联")
    void testCreateAgentToolMapping() {
        // Arrange
        ModelConfigEntity model = createTestModelConfig("mapping-model");
        AgentConfigEntity agent = createTestAgentConfig("mapping-agent", model.getId());
        ToolDefinitionEntity tool = createTestToolDefinition("mapping-tool", "LOCAL");

        AgentToolMappingEntity entity = new AgentToolMappingEntity();
        entity.setAgentConfigId(agent.getId());
        entity.setToolDefinitionId(tool.getId());

        // Act
        int result = agentToolMappingMapper.insert(entity);

        // Assert
        assertEquals(1, result);
        assertNotNull(entity.getId());
        System.out.println("✓ 创建 Agent-工具关联成功，ID: " + entity.getId());
    }

    @Test
    @Order(41)
    @DisplayName("41. 查询 Agent 的工具列表")
    void testGetAgentTools() {
        // Arrange
        ModelConfigEntity model = createTestModelConfig("query-tools-model");
        AgentConfigEntity agent = createTestAgentConfig("query-tools-agent", model.getId());
        ToolDefinitionEntity tool1 = createTestToolDefinition("query-tool-1", "LOCAL");
        ToolDefinitionEntity tool2 = createTestToolDefinition("query-tool-2", "MCP");

        createAgentToolMapping(agent.getId(), tool1.getId());
        createAgentToolMapping(agent.getId(), tool2.getId());

        // Act
        List<Long> toolIds = agentToolMappingMapper.selectToolIdsByAgentId(agent.getId());

        // Assert
        assertNotNull(toolIds);
        assertTrue(toolIds.size() >= 2);
        assertTrue(toolIds.contains(tool1.getId()));
        assertTrue(toolIds.contains(tool2.getId()));
        System.out.println("✓ 查询到 Agent 的工具列表，数量: " + toolIds.size());
    }

    // ==================== McpConnectionConfigMapper 测试 ====================

    @Test
    @Order(50)
    @DisplayName("50. 创建 MCP 连接配置")
    void testCreateMcpConnectionConfig() {
        // Arrange
        McpConnectionConfigEntity entity = new McpConnectionConfigEntity();
        entity.setConnectionName("test-mcp-connection");
        entity.setDescription("Test MCP Connection");
        entity.setConnectionType("STDIO");
        entity.setCommand("npx");
        entity.setArgs("[\"-y\", \"@test/mcp-server\"]");
        entity.setEnv("{\"TEST_KEY\": \"test_value\"}");
        entity.setTimeoutSeconds(30);
        entity.setAutoReconnect(true);
        entity.setMaxRetries(3);
        entity.setRetryIntervalSeconds(5);
        entity.setIsActive(true);

        // Act
        int result = mcpConnectionConfigMapper.insert(entity);

        // Assert
        assertEquals(1, result);
        assertNotNull(entity.getId());
        System.out.println("✓ 创建 MCP 连接配置成功，ID: " + entity.getId());
    }

    @Test
    @Order(51)
    @DisplayName("51. 根据连接类型查询 MCP 配置")
    void testGetMcpConnectionsByType() {
        // Arrange
        createTestMcpConnectionConfig("stdio-conn", "STDIO");
        createTestMcpConnectionConfig("sse-conn", "SSE");

        // Act
        List<McpConnectionConfigEntity> stdioConns = mcpConnectionConfigMapper.selectByType("STDIO");
        List<McpConnectionConfigEntity> sseConns = mcpConnectionConfigMapper.selectByType("SSE");

        // Assert
        assertNotNull(stdioConns);
        assertNotNull(sseConns);
        assertTrue(stdioConns.size() >= 1);
        assertTrue(sseConns.size() >= 1);
        System.out.println("✓ STDIO 连接: " + stdioConns.size() + ", SSE 连接: " + sseConns.size());
    }

    // ==================== 综合测试 ====================

    @Test
    @Order(100)
    @DisplayName("100. 完整流程测试：创建 Agent 并关联工具")
    void testCompleteWorkflow() {
        System.out.println("\n========== 开始完整流程测试 ==========");

        // 1. 创建模型配置
        ModelConfigEntity model = createTestModelConfig("workflow-model");
        assertNotNull(model.getId());
        System.out.println("✓ 步骤 1: 创建模型配置");

        // 2. 创建工具定义
        ToolDefinitionEntity tool1 = createTestToolDefinition("workflow-tool-1", "LOCAL");
        ToolDefinitionEntity tool2 = createTestToolDefinition("workflow-tool-2", "MCP");
        assertNotNull(tool1.getId());
        assertNotNull(tool2.getId());
        System.out.println("✓ 步骤 2: 创建工具定义");

        // 3. 创建 Agent 配置
        AgentConfigEntity agent = createTestAgentConfig("workflow-agent", model.getId());
        assertNotNull(agent.getId());
        System.out.println("✓ 步骤 3: 创建 Agent 配置");

        // 4. 关联工具
        createAgentToolMapping(agent.getId(), tool1.getId());
        createAgentToolMapping(agent.getId(), tool2.getId());
        System.out.println("✓ 步骤 4: 关联工具");

        // 5. 验证查询
        Optional<AgentConfigEntity> agentResult = agentConfigMapper.selectByAgentName("workflow-agent");
        assertTrue(agentResult.isPresent());
        assertEquals(model.getId(), agentResult.get().getModelId());
        System.out.println("✓ 步骤 5: 验证 Agent 配置");

        // 6. 验证工具关联
        List<Long> toolIds = agentToolMappingMapper.selectToolIdsByAgentId(agent.getId());
        assertTrue(toolIds.size() >= 2);
        System.out.println("✓ 步骤 6: 验证工具关联，工具数量: " + toolIds.size());

        // 7. 更新 Agent
        agentResult.get().setDisplayName("Updated Workflow Agent");
        agentConfigMapper.updateById(agentResult.get());
        System.out.println("✓ 步骤 7: 更新 Agent 配置");

        // 8. 验证更新
        Optional<AgentConfigEntity> updatedAgent = agentConfigMapper.selectByAgentName("workflow-agent");
        assertTrue(updatedAgent.isPresent());
        assertEquals("Updated Workflow Agent", updatedAgent.get().getDisplayName());
        System.out.println("✓ 步骤 8: 验证更新结果");

        System.out.println("========== 完整流程测试通过 ==========\n");
    }

    // ==================== 辅助方法 ====================

    private ModelConfigEntity createTestModelConfig(String modelName) {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setModelName(modelName);
        entity.setDisplayName("Test Model - " + modelName);
        entity.setProvider("test-provider");
        entity.setModelId("test-model-id");
        entity.setBaseUrl("https://test.api.com");
        entity.setApiKeyEncrypted("encrypted_key");
        entity.setIsActive(true);
        modelConfigMapper.insert(entity);
        return entity;
    }

    private ToolDefinitionEntity createTestToolDefinition(String toolName, String toolType) {
        ToolDefinitionEntity entity = new ToolDefinitionEntity();
        entity.setToolName(toolName);
        entity.setDisplayName("Test Tool - " + toolName);
        entity.setDescription("Test tool description");
        entity.setToolType(toolType);
        if ("LOCAL".equals(toolType)) {
            entity.setClassName("cn.ts.web.tool.TestTool");
        } else {
            entity.setMcpConnectionName("test-connection");
            entity.setMcpToolName("test_mcp_tool");
        }
        entity.setIsActive(true);
        toolDefinitionMapper.insert(entity);
        return entity;
    }

    private AgentConfigEntity createTestAgentConfig(String agentName) {
        ModelConfigEntity model = createTestModelConfig(agentName + "-model");
        return createTestAgentConfig(agentName, model.getId());
    }

    private AgentConfigEntity createTestAgentConfig(String agentName, Long modelId) {
        AgentConfigEntity entity = new AgentConfigEntity();
        entity.setAgentName(agentName);
        entity.setDisplayName("Test Agent - " + agentName);
        entity.setDescription("Test agent description");
        entity.setModelId(modelId);
        entity.setSystemPrompt("You are a test assistant");
        entity.setMaxIterations(10);
        entity.setTemperature(new BigDecimal("0.7"));
        entity.setEnableStreaming(true);
        entity.setIsActive(true);
        entity.setCreatedBy("test-user");
        agentConfigMapper.insert(entity);
        return entity;
    }

    private AgentToolMappingEntity createAgentToolMapping(Long agentId, Long toolId) {
        AgentToolMappingEntity entity = new AgentToolMappingEntity();
        entity.setAgentConfigId(agentId);
        entity.setToolDefinitionId(toolId);
        agentToolMappingMapper.insert(entity);
        return entity;
    }

    private McpConnectionConfigEntity createTestMcpConnectionConfig(String name, String type) {
        McpConnectionConfigEntity entity = new McpConnectionConfigEntity();
        entity.setConnectionName(name);
        entity.setDescription("Test connection - " + name);
        entity.setConnectionType(type);
        entity.setCommand("npx");
        entity.setArgs("[]");
        entity.setEnv("{}");
        entity.setTimeoutSeconds(30);
        entity.setAutoReconnect(true);
        entity.setMaxRetries(3);
        entity.setRetryIntervalSeconds(5);
        entity.setIsActive(true);
        mcpConnectionConfigMapper.insert(entity);
        return entity;
    }
}
