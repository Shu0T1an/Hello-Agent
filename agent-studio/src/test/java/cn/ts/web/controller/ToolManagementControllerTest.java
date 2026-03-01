package cn.ts.web.controller;

import cn.ts.web.agent.dto.ToolDefinitionDTO;
import cn.ts.web.agent.dto.ToolType;
import cn.ts.web.infra.mcp.service.McpToolSyncService;
import cn.ts.web.shared.component.LocalToolScanner;
import cn.ts.web.tool.controller.ToolManagementController;
import cn.ts.web.tool.service.ToolDefinitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * ToolManagementController 单元测试
 */
@WebMvcTest(ToolManagementController.class)
class ToolManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(name = "toolDefinitionService")
    private ToolDefinitionService toolDefinitionService;

    @MockBean
    private LocalToolScanner localToolScanner;

    @MockBean
    private McpToolSyncService mcpToolSyncService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testCreateTool_ValidData_ReturnsCreated() throws Exception {
        // Arrange
        ToolDefinitionDTO request = new ToolDefinitionDTO();
        request.setToolName("test-tool");
        request.setDisplayName("Test Tool");
        request.setDescription("A test tool");
        request.setToolType(ToolType.LOCAL);
        request.setClassName("java.lang.String");
        request.setIsActive(true);

        ToolDefinitionDTO responseDTO = new ToolDefinitionDTO();
        responseDTO.setId(1L);
        responseDTO.setToolName("test-tool");
        responseDTO.setDisplayName("Test Tool");

        when(toolDefinitionService.createTool(any(ToolDefinitionDTO.class))).thenReturn(responseDTO);

        // Act
        mockMvc.perform(post("/api/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toolName").value("test-tool"))
                .andExpect(jsonPath("$.data.displayName").value("Test Tool"));

        verify(toolDefinitionService).createTool(any(ToolDefinitionDTO.class));
    }

    @Test
    void testCreateTool_ServiceLayerValidation_ReturnsCreated() throws Exception {
        // Arrange - Service layer validation will be tested in service tests
        ToolDefinitionDTO request = new ToolDefinitionDTO();
        request.setToolName("test-tool");
        request.setDisplayName("Test Tool");
        request.setToolType(ToolType.LOCAL);

        ToolDefinitionDTO responseDTO = new ToolDefinitionDTO();
        responseDTO.setId(1L);
        responseDTO.setToolName("test-tool");

        when(toolDefinitionService.createTool(any(ToolDefinitionDTO.class))).thenReturn(responseDTO);

        // Act & Assert
        mockMvc.perform(post("/api/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(toolDefinitionService).createTool(any(ToolDefinitionDTO.class));
    }

    @Test
    void testUpdateTool_ValidData_ReturnsOk() throws Exception {
        // Arrange
        ToolDefinitionDTO request = new ToolDefinitionDTO();
        request.setDisplayName("Updated Tool");

        ToolDefinitionDTO responseDTO = new ToolDefinitionDTO();
        responseDTO.setId(1L);
        responseDTO.setDisplayName("Updated Tool");

        when(toolDefinitionService.updateTool(eq(1L), any(ToolDefinitionDTO.class))).thenReturn(responseDTO);

        // Act
        mockMvc.perform(put("/api/tools/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated Tool"));

        verify(toolDefinitionService).updateTool(eq(1L), any(ToolDefinitionDTO.class));
    }

    @Test
    void testDeleteTool_ValidId_ReturnsNoContent() throws Exception {
        // Arrange
        doNothing().when(toolDefinitionService).deleteTool(1L);

        // Act
        mockMvc.perform(delete("/api/tools/1"))
                .andExpect(status().isOk());

        verify(toolDefinitionService).deleteTool(1L);
    }

    @Test
    void testGetTool_ExistingTool_ReturnsTool() throws Exception {
        // Arrange
        ToolDefinitionDTO tool = new ToolDefinitionDTO();
        tool.setId(1L);
        tool.setToolName("test-tool");

        when(toolDefinitionService.getToolById(1L)).thenReturn(tool);

        // Act
        mockMvc.perform(get("/api/tools/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toolName").value("test-tool"));

        verify(toolDefinitionService).getToolById(1L);
    }

    @Test
    void testGetAllTools_ReturnsToolList() throws Exception {
        // Arrange
        ToolDefinitionDTO tool1 = new ToolDefinitionDTO();
        tool1.setId(1L);
        tool1.setToolName("tool1");
        tool1.setToolType(ToolType.LOCAL);

        ToolDefinitionDTO tool2 = new ToolDefinitionDTO();
        tool2.setId(2L);
        tool2.setToolName("tool2");
        tool2.setToolType(ToolType.MCP);

        when(toolDefinitionService.getAllTools()).thenReturn(Arrays.asList(tool1, tool2));

        // Act
        mockMvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].toolName", containsInAnyOrder("tool1", "tool2")));

        verify(toolDefinitionService).getAllTools();
    }

    @Test
    void testGetAllTools_WithTypeFilter_ReturnsFilteredTools() throws Exception {
        // Arrange
        ToolDefinitionDTO tool1 = new ToolDefinitionDTO();
        tool1.setId(1L);
        tool1.setToolName("local-tool");
        tool1.setToolType(ToolType.LOCAL);

        when(toolDefinitionService.getToolsByType(ToolType.LOCAL)).thenReturn(Arrays.asList(tool1));

        // Act
        mockMvc.perform(get("/api/tools")
                        .param("type", "LOCAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].toolName").value("local-tool"));

        verify(toolDefinitionService).getToolsByType(ToolType.LOCAL);
    }

    @Test
    void testGetActiveTools_ReturnsActiveTools() throws Exception {
        // Arrange
        ToolDefinitionDTO tool = new ToolDefinitionDTO();
        tool.setId(1L);
        tool.setToolName("active-tool");
        tool.setIsActive(true);

        when(toolDefinitionService.getActiveTools()).thenReturn(Arrays.asList(tool));

        // Act
        mockMvc.perform(get("/api/tools/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].toolName").value("active-tool"));

        verify(toolDefinitionService).getActiveTools();
    }

    @Test
    void testGetActiveTools_EmptyList_ReturnsEmptyList() throws Exception {
        // Arrange
        when(toolDefinitionService.getActiveTools()).thenReturn(Collections.emptyList());

        // Act
        mockMvc.perform(get("/api/tools/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        verify(toolDefinitionService).getActiveTools();
    }

    @Test
    void testScanLocalTools_ReturnsOk() throws Exception {
        // Arrange
        ToolDefinitionDTO localTool1 = new ToolDefinitionDTO();
        localTool1.setId(1L);
        localTool1.setToolName("local-tool-1");
        localTool1.setToolType(ToolType.LOCAL);

        ToolDefinitionDTO localTool2 = new ToolDefinitionDTO();
        localTool2.setId(2L);
        localTool2.setToolName("local-tool-2");
        localTool2.setToolType(ToolType.LOCAL);

        when(toolDefinitionService.getToolsByType(ToolType.LOCAL)).thenReturn(Arrays.asList(localTool1, localTool2));

        // Act
        mockMvc.perform(post("/api/tools/scan-local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2));

        verify(localToolScanner).rescan();
        verify(toolDefinitionService).getToolsByType(ToolType.LOCAL);
    }

    @Test
    void testSyncMcpTools_ReturnsOk() throws Exception {
        // Arrange
        when(mcpToolSyncService.syncTools("test-connection")).thenReturn(3);

        // Act
        mockMvc.perform(post("/api/tools/sync-mcp/test-connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3));

        verify(mcpToolSyncService).syncTools("test-connection");
    }

    @Test
    void testCreateTool_McpTool_ReturnsCreated() throws Exception {
        // Arrange
        ToolDefinitionDTO request = new ToolDefinitionDTO();
        request.setToolName("connection:test-tool");
        request.setDisplayName("Test MCP Tool");
        request.setDescription("A test MCP tool");
        request.setToolType(ToolType.MCP);
        request.setMcpConnectionName("connection");
        request.setMcpToolName("test-tool");
        request.setIsActive(true);

        ToolDefinitionDTO responseDTO = new ToolDefinitionDTO();
        responseDTO.setId(1L);
        responseDTO.setToolName("connection:test-tool");
        responseDTO.setToolType(ToolType.MCP);

        when(toolDefinitionService.createTool(any(ToolDefinitionDTO.class))).thenReturn(responseDTO);

        // Act
        mockMvc.perform(post("/api/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toolName").value("connection:test-tool"))
                .andExpect(jsonPath("$.data.toolType").value("MCP"));

        verify(toolDefinitionService).createTool(any(ToolDefinitionDTO.class));
    }

    @Test
    void testGetToolsByType_Mcp_ReturnsMcpTools() throws Exception {
        // Arrange
        ToolDefinitionDTO tool = new ToolDefinitionDTO();
        tool.setId(1L);
        tool.setToolName("mcp-tool");
        tool.setToolType(ToolType.MCP);

        when(toolDefinitionService.getToolsByType(ToolType.MCP)).thenReturn(Arrays.asList(tool));

        // Act
        mockMvc.perform(get("/api/tools")
                        .param("type", "MCP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].toolType").value("MCP"));

        verify(toolDefinitionService).getToolsByType(ToolType.MCP);
    }
}
