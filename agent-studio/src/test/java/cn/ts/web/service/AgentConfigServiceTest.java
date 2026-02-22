package cn.ts.web.service;

import cn.ts.agent.core.ReactAgent;
import cn.ts.graph.CompiledGraph;
import cn.ts.web.dto.agent.AgentConfigDTO;
import cn.ts.web.dto.agent.ToolDefinitionDTO;
import cn.ts.web.dto.agent.ToolType;
import cn.ts.web.entity.AgentConfigEntity;
import cn.ts.web.factory.AgentFactory;
import cn.ts.web.mapper.AgentConfigMapper;
import cn.ts.web.mapper.AgentToolMappingMapper;
import cn.ts.web.mapper.SubAgentMappingMapper;
import cn.ts.web.service.impl.AgentConfigServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AgentConfigService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentConfigServiceTest {

    @Mock
    private AgentFactory agentFactory;

    @Mock
    private AgentExecutionService agentExecutionService;

    @Mock
    private AgentConfigMapper agentConfigMapper;

    @Mock
    private AgentToolMappingMapper agentToolMappingMapper;

    @Mock
    private SubAgentMappingMapper subAgentMappingMapper;

    @Mock
    private ToolDefinitionService toolDefinitionService;

    @Mock
    private ReactAgent reactAgent;

    @Mock
    private CompiledGraph compiledGraph;

    @InjectMocks
    private AgentConfigServiceImpl agentConfigService;

    private AgentConfigDTO testDTO;
    private AgentConfigEntity testEntity;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        testDTO = new AgentConfigDTO();
        testDTO.setId(1L);
        testDTO.setAgentName("test-agent");
        testDTO.setDisplayName("Test Agent");
        testDTO.setDescription("A test agent");
        testDTO.setModelId(1L);
        testDTO.setSystemPrompt("You are a helpful assistant");
        testDTO.setMaxIterations(10);
        testDTO.setTemperature(new BigDecimal("0.7"));
        testDTO.setEnableStreaming(true);
        testDTO.setIsActive(true);
        testDTO.setCreatedBy("system");

        testEntity = new AgentConfigEntity();
        testEntity.setId(1L);
        testEntity.setAgentName("test-agent");
        testEntity.setDisplayName("Test Agent");
        testEntity.setDescription("A test agent");
        testEntity.setModelId(1L);
        testEntity.setSystemPrompt("You are a helpful assistant");
        testEntity.setMaxIterations(10);
        testEntity.setTemperature(new BigDecimal("0.7"));
        testEntity.setEnableStreaming(true);
        testEntity.setIsActive(true);
        testEntity.setCreatedBy("system");
        testEntity.setCreatedAt(Instant.now());
        testEntity.setUpdatedAt(Instant.now());

        // 模拟 ReactAgent 的 CompiledGraph
        when(reactAgent.getGraph()).thenReturn(compiledGraph);
    }

    @Test
    void testCreateAgent_ValidData_ReturnsAgentDTO() {
        // Arrange
        when(agentConfigMapper.countByAgentName("test-agent")).thenReturn(0);
        // 使用 Answer 来设置 ID
        when(agentConfigMapper.insert(any(AgentConfigEntity.class))).thenAnswer(invocation -> {
            AgentConfigEntity entity = invocation.getArgument(0);
            entity.setId(1L); // 设置 ID
            return 1;
        });
        when(agentConfigMapper.selectById(1L)).thenReturn(testEntity);
        when(toolDefinitionService.getToolsByAgentId(1L)).thenReturn(Collections.emptyList());
        when(agentFactory.createAgent(any(AgentConfigDTO.class))).thenReturn(reactAgent);
        doNothing().when(agentExecutionService).registerGraph(eq("test-agent"), any(CompiledGraph.class));

        // Act
        AgentConfigDTO result = agentConfigService.createAgent(testDTO);

        // Assert
        assertNotNull(result);
        assertEquals("test-agent", result.getAgentName());
        verify(agentConfigMapper).insert(any(AgentConfigEntity.class));
        verify(agentExecutionService).registerGraph(eq("test-agent"), any(CompiledGraph.class));
    }

    @Test
    void testCreateAgent_DuplicateName_ThrowsException() {
        // Arrange
        when(agentConfigMapper.countByAgentName("test-agent")).thenReturn(1);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            agentConfigService.createAgent(testDTO);
        });

        verify(agentConfigMapper, never()).insert(any(AgentConfigEntity.class));
    }

    @Test
    void testCreateAgent_WithTools_CreatesMappings() {
        // Arrange
        testDTO.setToolIds(Arrays.asList(1L, 2L));
        when(agentConfigMapper.countByAgentName("test-agent")).thenReturn(0);
        // 使用 Answer 来设置 ID
        when(agentConfigMapper.insert(any(AgentConfigEntity.class))).thenAnswer(invocation -> {
            AgentConfigEntity entity = invocation.getArgument(0);
            entity.setId(1L); // 设置 ID
            return 1;
        });
        when(agentConfigMapper.selectById(1L)).thenReturn(testEntity);
        when(toolDefinitionService.getToolsByAgentId(1L)).thenReturn(Collections.emptyList());
        when(agentFactory.createAgent(any(AgentConfigDTO.class))).thenReturn(reactAgent);
        doNothing().when(agentExecutionService).registerGraph(eq("test-agent"), any(CompiledGraph.class));

        // Act
        agentConfigService.createAgent(testDTO);

        // Assert
        verify(agentToolMappingMapper, times(2)).insert(any(cn.ts.web.entity.AgentToolMappingEntity.class));
    }

    @Test
    void testUpdateAgent_ValidData_ReturnsUpdatedDTO() {
        // Arrange
        when(agentConfigMapper.selectById(1L)).thenReturn(testEntity);
        when(agentConfigMapper.countByAgentNameExcludeId("test-agent", 1L)).thenReturn(0);
        when(agentConfigMapper.updateById(any(AgentConfigEntity.class))).thenReturn(1);
        when(agentConfigMapper.selectById(1L)).thenReturn(testEntity);
        when(toolDefinitionService.getToolsByAgentId(1L)).thenReturn(Collections.emptyList());
        when(agentConfigMapper.selectByAgentName("test-agent")).thenReturn(Optional.of(testEntity));
        when(agentFactory.createAgent(any(AgentConfigDTO.class))).thenReturn(reactAgent);
        doNothing().when(agentExecutionService).unregisterAgent("test-agent");
        doNothing().when(agentExecutionService).registerGraph(eq("test-agent"), any(CompiledGraph.class));

        testDTO.setDisplayName("Updated Agent");

        // Act
        AgentConfigDTO result = agentConfigService.updateAgent(1L, testDTO);

        // Assert
        assertNotNull(result);
        verify(agentConfigMapper).updateById(any(AgentConfigEntity.class));
    }

    @Test
    void testUpdateAgent_AgentNotFound_ThrowsException() {
        // Arrange
        when(agentConfigMapper.selectById(1L)).thenReturn(null);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            agentConfigService.updateAgent(1L, testDTO);
        });

        verify(agentConfigMapper, never()).updateById(any(AgentConfigEntity.class));
    }

    @Test
    void testDeleteAgent_ValidId_DeletesAgent() {
        // Arrange
        when(agentConfigMapper.selectById(1L)).thenReturn(testEntity);
        when(agentConfigMapper.deleteById(1L)).thenReturn(1);
        doNothing().when(agentExecutionService).unregisterAgent("test-agent");

        // Act
        agentConfigService.deleteAgent(1L);

        // Assert
        verify(agentExecutionService).unregisterAgent("test-agent");
        verify(agentConfigMapper).deleteById(1L);
    }

    @Test
    void testGetAgentById_ExistingAgent_ReturnsAgent() {
        // Arrange
        when(agentConfigMapper.selectById(1L)).thenReturn(testEntity);
        when(toolDefinitionService.getToolsByAgentId(1L)).thenReturn(Collections.emptyList());

        // Act
        AgentConfigDTO result = agentConfigService.getAgentById(1L);

        // Assert
        assertNotNull(result);
        assertEquals("test-agent", result.getAgentName());
        verify(agentConfigMapper).selectById(1L);
    }

    @Test
    void testGetAgentById_NonExistingAgent_ReturnsNull() {
        // Arrange
        when(agentConfigMapper.selectById(1L)).thenReturn(null);

        // Act
        AgentConfigDTO result = agentConfigService.getAgentById(1L);

        // Assert
        assertNull(result);
    }

    @Test
    void testGetAgentByName_ExistingAgent_ReturnsAgent() {
        // Arrange
        when(agentConfigMapper.selectByAgentName("test-agent")).thenReturn(Optional.of(testEntity));
        when(toolDefinitionService.getToolsByAgentId(1L)).thenReturn(Collections.emptyList());

        // Act
        AgentConfigDTO result = agentConfigService.getAgentByName("test-agent");

        // Assert
        assertNotNull(result);
        assertEquals("test-agent", result.getAgentName());
        verify(agentConfigMapper).selectByAgentName("test-agent");
    }

    @Test
    void testGetAgentByName_NonExistingAgent_ReturnsNull() {
        // Arrange
        when(agentConfigMapper.selectByAgentName("non-existent")).thenReturn(Optional.empty());

        // Act
        AgentConfigDTO result = agentConfigService.getAgentByName("non-existent");

        // Assert
        assertNull(result);
    }

    @Test
    void testGetAllAgents_ReturnsAgentList() {
        // Arrange
        when(agentConfigMapper.selectAll()).thenReturn(Arrays.asList(testEntity));

        // Act
        List<AgentConfigDTO> result = agentConfigService.getAllAgents();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(agentConfigMapper).selectAll();
    }

    @Test
    void testGetActiveAgents_ReturnsActiveAgents() {
        // Arrange
        when(agentConfigMapper.selectActive()).thenReturn(Arrays.asList(testEntity));

        // Act
        List<AgentConfigDTO> result = agentConfigService.getActiveAgents();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(agentConfigMapper).selectActive();
    }

    @Test
    void testAssembleAgent_ReturnsReactAgent() {
        // Arrange
        when(agentFactory.createAgent(testDTO)).thenReturn(reactAgent);

        // Act
        ReactAgent result = agentConfigService.assembleAgent(testDTO);

        // Assert
        assertNotNull(result);
        verify(agentFactory).createAgent(testDTO);
    }

    @Test
    void testRegisterAgentToExecutionService_RegistersAgent() {
        // Arrange
        doNothing().when(agentExecutionService).registerGraph("test-agent", compiledGraph);

        // Act
        agentConfigService.registerAgentToExecutionService("test-agent", reactAgent);

        // Assert
        verify(agentExecutionService).registerGraph("test-agent", compiledGraph);
    }

    @Test
    void testUnregisterAgentFromExecutionService_UnregistersAgent() {
        // Arrange
        doNothing().when(agentExecutionService).unregisterAgent("test-agent");

        // Act
        agentConfigService.unregisterAgentFromExecutionService("test-agent");

        // Assert
        verify(agentExecutionService).unregisterAgent("test-agent");
    }

    @Test
    void testActivateAgent_ValidId_ActivatesAgent() {
        // Arrange
        when(agentConfigMapper.selectById(1L)).thenReturn(testEntity);
        when(agentConfigMapper.updateById(any(AgentConfigEntity.class))).thenReturn(1);
        when(agentConfigMapper.selectById(1L)).thenReturn(testEntity);
        when(toolDefinitionService.getToolsByAgentId(1L)).thenReturn(Collections.emptyList());
        when(agentFactory.createAgent(any(AgentConfigDTO.class))).thenReturn(reactAgent);
        doNothing().when(agentExecutionService).registerGraph(eq("test-agent"), any(CompiledGraph.class));

        // Act
        agentConfigService.activateAgent(1L);

        // Assert
        verify(agentConfigMapper).updateById(argThat(entity ->
            Boolean.TRUE.equals(entity.getIsActive())
        ));
        verify(agentExecutionService).registerGraph(eq("test-agent"), any(CompiledGraph.class));
    }

    @Test
    void testDeactivateAgent_ValidId_DeactivatesAgent() {
        // Arrange
        when(agentConfigMapper.selectById(1L)).thenReturn(testEntity);
        when(agentConfigMapper.updateById(any(AgentConfigEntity.class))).thenReturn(1);
        doNothing().when(agentExecutionService).unregisterAgent("test-agent");

        // Act
        agentConfigService.deactivateAgent(1L);

        // Assert
        verify(agentConfigMapper).updateById(argThat(entity ->
            Boolean.FALSE.equals(entity.getIsActive())
        ));
        verify(agentExecutionService).unregisterAgent("test-agent");
    }

    @Test
    void testReloadAgent_ExistingAgent_ReloadsAgent() {
        // Arrange
        when(agentConfigMapper.selectByAgentName("test-agent")).thenReturn(Optional.of(testEntity));
        when(toolDefinitionService.getToolsByAgentId(1L)).thenReturn(Collections.emptyList());
        when(agentFactory.createAgent(any(AgentConfigDTO.class))).thenReturn(reactAgent);
        doNothing().when(agentExecutionService).registerGraph(eq("test-agent"), any(CompiledGraph.class));

        // Act
        agentConfigService.reloadAgent("test-agent");

        // Assert
        // unregisterAgent 只在 agent 已存在于注册表中时才会被调用
        // 由于我们无法预填充注册表，所以只验证 registerGraph 被调用
        verify(agentExecutionService).registerGraph(eq("test-agent"), any(CompiledGraph.class));
    }

    @Test
    void testGetAgentById_WithTools_ReturningsAgentWithTools() {
        // Arrange
        ToolDefinitionDTO tool1 = new ToolDefinitionDTO();
        tool1.setId(1L);
        tool1.setToolName("tool1");
        tool1.setToolType(ToolType.LOCAL);
        tool1.setIsActive(true);

        when(agentConfigMapper.selectById(1L)).thenReturn(testEntity);
        when(toolDefinitionService.getToolsByAgentId(1L)).thenReturn(Arrays.asList(tool1));

        // Act
        AgentConfigDTO result = agentConfigService.getAgentById(1L);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getToolDefinitions());
        assertEquals(1, result.getToolDefinitions().size());
    }
}
