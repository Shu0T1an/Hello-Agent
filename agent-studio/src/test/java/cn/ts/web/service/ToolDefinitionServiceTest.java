package cn.ts.web.service;

import cn.ts.web.agent.dto.ToolDefinitionDTO;
import cn.ts.web.agent.dto.ToolType;
import cn.ts.web.tool.entity.ToolDefinitionEntity;
import cn.ts.web.tool.mapper.ToolDefinitionMapper;
import cn.ts.web.tool.service.impl.ToolDefinitionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ToolDefinitionService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ToolDefinitionServiceTest {

    @Mock
    private ToolDefinitionMapper mapper;

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private ToolDefinitionServiceImpl toolDefinitionService;

    private ToolDefinitionDTO testDTO;
    private ToolDefinitionEntity testEntity;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        testDTO = new ToolDefinitionDTO();
        testDTO.setToolName("test-tool");
        testDTO.setDisplayName("Test Tool");
        testDTO.setDescription("A test tool");
        testDTO.setToolType(ToolType.LOCAL);
        testDTO.setClassName("java.lang.String");
        testDTO.setIsActive(true);

        testEntity = new ToolDefinitionEntity();
        testEntity.setId(1L);
        testEntity.setToolName("test-tool");
        testEntity.setDisplayName("Test Tool");
        testEntity.setDescription("A test tool");
        testEntity.setToolType(ToolType.LOCAL.name());
        testEntity.setClassName("java.lang.String");
        testEntity.setIsActive(true);
        testEntity.setCreatedAt(Instant.now());
        testEntity.setUpdatedAt(Instant.now());
    }

    @Test
    void testCreateTool_ValidData_ReturnsToolDTO() {
        // Arrange
        when(mapper.countByToolName("test-tool")).thenReturn(0);
        when(mapper.insert(any(ToolDefinitionEntity.class))).thenReturn(1);

        // Act
        ToolDefinitionDTO result = toolDefinitionService.createTool(testDTO);

        // Assert
        assertNotNull(result);
        assertEquals("test-tool", result.getToolName());
        verify(mapper).insert(any(ToolDefinitionEntity.class));
    }

    @Test
    void testCreateTool_DuplicateName_ThrowsException() {
        // Arrange
        when(mapper.countByToolName("test-tool")).thenReturn(1);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            toolDefinitionService.createTool(testDTO);
        });

        verify(mapper, never()).insert(any(ToolDefinitionEntity.class));
    }

    @Test
    void testCreateOrUpdateTool_NewTool_CreatesTool() {
        // Arrange
        when(mapper.selectByToolName("new-tool")).thenReturn(Optional.empty());
        when(mapper.insert(any(ToolDefinitionEntity.class))).thenReturn(1);

        testDTO.setToolName("new-tool");

        // Act
        ToolDefinitionDTO result = toolDefinitionService.createOrUpdateTool(testDTO);

        // Assert
        assertNotNull(result);
        verify(mapper).insert(any(ToolDefinitionEntity.class));
        verify(mapper, never()).updateById(any(ToolDefinitionEntity.class));
    }

    @Test
    void testCreateOrUpdateTool_ExistingTool_UpdatesTool() {
        // Arrange
        when(mapper.selectByToolName("test-tool")).thenReturn(Optional.of(testEntity));
        when(mapper.updateById(any(ToolDefinitionEntity.class))).thenReturn(1);

        // Act
        ToolDefinitionDTO result = toolDefinitionService.createOrUpdateTool(testDTO);

        // Assert
        assertNotNull(result);
        verify(mapper).updateById(any(ToolDefinitionEntity.class));
        verify(mapper, never()).insert(any(ToolDefinitionEntity.class));
    }

    @Test
    void testUpdateTool_ValidData_ReturnsUpdatedDTO() {
        // Arrange
        when(mapper.selectById(1L)).thenReturn(testEntity);
        when(mapper.countByToolNameExcludeId("test-tool", 1L)).thenReturn(0);
        when(mapper.updateById(any(ToolDefinitionEntity.class))).thenReturn(1);

        testDTO.setDisplayName("Updated Tool");

        // Act
        ToolDefinitionDTO result = toolDefinitionService.updateTool(1L, testDTO);

        // Assert
        assertNotNull(result);
        verify(mapper).updateById(any(ToolDefinitionEntity.class));
    }

    @Test
    void testDeleteTool_ValidId_DeletesTool() {
        // Arrange
        when(mapper.deleteById(1L)).thenReturn(1);

        // Act
        toolDefinitionService.deleteTool(1L);

        // Assert
        verify(mapper).deleteById(1L);
    }

    @Test
    void testGetToolById_ExistingTool_ReturnsTool() {
        // Arrange
        when(mapper.selectById(1L)).thenReturn(testEntity);

        // Act
        ToolDefinitionDTO result = toolDefinitionService.getToolById(1L);

        // Assert
        assertNotNull(result);
        assertEquals("test-tool", result.getToolName());
        verify(mapper).selectById(1L);
    }

    @Test
    void testGetToolByName_ExistingTool_ReturnsTool() {
        // Arrange
        when(mapper.selectByToolName("test-tool")).thenReturn(Optional.of(testEntity));

        // Act
        ToolDefinitionDTO result = toolDefinitionService.getToolByName("test-tool");

        // Assert
        assertNotNull(result);
        assertEquals("test-tool", result.getToolName());
        verify(mapper).selectByToolName("test-tool");
    }

    @Test
    void testGetAllTools_ReturnsToolList() {
        // Arrange
        when(mapper.selectAll()).thenReturn(Arrays.asList(testEntity));

        // Act
        List<ToolDefinitionDTO> result = toolDefinitionService.getAllTools();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(mapper).selectAll();
    }

    @Test
    void testGetActiveTools_ReturnsActiveTools() {
        // Arrange
        when(mapper.selectActive()).thenReturn(Arrays.asList(testEntity));

        // Act
        List<ToolDefinitionDTO> result = toolDefinitionService.getActiveTools();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(mapper).selectActive();
    }

    @Test
    void testGetToolsByType_ReturnsFilteredTools() {
        // Arrange
        when(mapper.selectByType("LOCAL")).thenReturn(Arrays.asList(testEntity));

        // Act
        List<ToolDefinitionDTO> result = toolDefinitionService.getToolsByType(ToolType.LOCAL);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(mapper).selectByType("LOCAL");
    }

    @Test
    void testInstantiateTools_LocalTool_ReturnsToolInstance() {
        // Arrange
        ToolDefinitionDTO localTool = new ToolDefinitionDTO();
        localTool.setToolName("local-tool");
        localTool.setToolType(ToolType.LOCAL);
        localTool.setClassName("java.lang.String");
        localTool.setIsActive(true);

        String mockBean = "test-bean";
        when(applicationContext.getBean(any(Class.class))).thenReturn(mockBean);

        // Act
        Object[] tools = toolDefinitionService.instantiateTools(Arrays.asList(localTool));

        // Assert
        assertNotNull(tools);
        assertEquals(1, tools.length);
        assertEquals(mockBean, tools[0]);
    }

    @Test
    void testInstantiateTools_InactiveTool_SkipsTool() {
        // Arrange
        ToolDefinitionDTO inactiveTool = new ToolDefinitionDTO();
        inactiveTool.setToolName("inactive-tool");
        inactiveTool.setToolType(ToolType.LOCAL);
        inactiveTool.setIsActive(false);

        // Act
        Object[] tools = toolDefinitionService.instantiateTools(Arrays.asList(inactiveTool));

        // Assert
        assertNotNull(tools);
        assertEquals(0, tools.length);
    }

    @Test
    void testDisableToolsByConnection_DisablesTools() {
        // Arrange
        when(mapper.disableByMcpConnection("test-connection")).thenReturn(2);

        // Act
        toolDefinitionService.disableToolsByConnection("test-connection");

        // Assert
        verify(mapper).disableByMcpConnection("test-connection");
    }

    @Test
    void testSyncMcpTools_SyncsToolsToDatabase() {
        // Arrange
        when(mapper.countByToolName("connection:tool1")).thenReturn(0);
        when(mapper.countByToolName("connection:tool2")).thenReturn(0);
        when(mapper.insert(any(ToolDefinitionEntity.class))).thenReturn(1).thenReturn(1);

        List<ToolDefinitionService.McpToolInfo> mcpTools = Arrays.asList(
                new ToolDefinitionService.McpToolInfo("tool1", "Tool 1"),
                new ToolDefinitionService.McpToolInfo("tool2", "Tool 2")
        );

        // Act
        toolDefinitionService.syncMcpTools("connection", mcpTools);

        // Assert
        verify(mapper, times(2)).insert(any(ToolDefinitionEntity.class));
    }

    @Test
    void testGetToolsByAgentId_ReturnsToolsForAgent() {
        // Arrange
        when(mapper.selectByAgentId(1L)).thenReturn(Arrays.asList(testEntity));

        // Act
        List<ToolDefinitionDTO> result = toolDefinitionService.getToolsByAgentId(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(mapper).selectByAgentId(1L);
    }

    @Test
    void testInstantiateTools_LocalToolNotFound_LogsError() {
        // Arrange
        ToolDefinitionDTO localTool = new ToolDefinitionDTO();
        localTool.setToolName("local-tool");
        localTool.setToolType(ToolType.LOCAL);
        localTool.setClassName("cn.ts.NonExistentClass");
        localTool.setIsActive(true);

        when(applicationContext.getBean(any(Class.class))).thenThrow(new RuntimeException("Bean not found"));

        // Act
        Object[] tools = toolDefinitionService.instantiateTools(Arrays.asList(localTool));

        // Assert
        assertNotNull(tools);
        assertEquals(0, tools.length); // 错误的工具被跳过
    }

    @Test
    void testUpdateTool_ChangeIsActive_UpdatesSuccessfully() {
        // Arrange
        when(mapper.selectById(1L)).thenReturn(testEntity);
        when(mapper.countByToolNameExcludeId("test-tool", 1L)).thenReturn(0);
        when(mapper.updateById(any(ToolDefinitionEntity.class))).thenReturn(1);

        testDTO.setIsActive(false);

        // Act
        ToolDefinitionDTO result = toolDefinitionService.updateTool(1L, testDTO);

        // Assert
        assertNotNull(result);
        verify(mapper).updateById(argThat(entity ->
            Boolean.FALSE.equals(entity.getIsActive())
        ));
    }

    @Test
    void testGetActiveTools_EmptyList_ReturnsEmptyList() {
        // Arrange
        when(mapper.selectActive()).thenReturn(Arrays.asList());

        // Act
        List<ToolDefinitionDTO> result = toolDefinitionService.getActiveTools();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testInstantiateTools_EmptyList_ReturnsEmptyArray() {
        // Act
        Object[] tools = toolDefinitionService.instantiateTools(Arrays.asList());

        // Assert
        assertNotNull(tools);
        assertEquals(0, tools.length);
    }
}
