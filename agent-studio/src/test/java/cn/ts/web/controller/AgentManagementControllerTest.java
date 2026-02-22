package cn.ts.web.controller;

import cn.ts.web.dto.agent.AgentConfigDTO;
import cn.ts.web.dto.agent.CreateAgentDTO;
import cn.ts.web.dto.agent.UpdateAgentDTO;
import cn.ts.web.service.AgentConfigService;
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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * AgentManagementController 单元测试
 */
@WebMvcTest(AgentManagementController.class)
class AgentManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(name = "agentConfigService")
    private AgentConfigService agentConfigService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testCreateAgent_ValidData_ReturnsCreated() throws Exception {
        // Arrange
        CreateAgentDTO request = new CreateAgentDTO();
        request.setAgentName("test-agent");
        request.setDisplayName("Test Agent");
        request.setDescription("A test agent");
        request.setModelId(1L);
        request.setSystemPrompt("You are a helpful assistant");
        request.setMaxIterations(10);
        request.setTemperature(new BigDecimal("0.7"));
        request.setEnableStreaming(true);
        request.setToolIds(Arrays.asList(1L, 2L));

        AgentConfigDTO responseDTO = new AgentConfigDTO();
        responseDTO.setId(1L);
        responseDTO.setAgentName("test-agent");
        responseDTO.setDisplayName("Test Agent");

        when(agentConfigService.createAgent(any(AgentConfigDTO.class))).thenReturn(responseDTO);

        // Act
        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentName").value("test-agent"))
                .andExpect(jsonPath("$.data.displayName").value("Test Agent"));

        verify(agentConfigService).createAgent(any(AgentConfigDTO.class));
    }

    @Test
    void testCreateAgent_InvalidData_ReturnsBadRequest() throws Exception {
        // Arrange - 名称太短
        CreateAgentDTO request = new CreateAgentDTO();
        request.setAgentName("ab");  // 太短
        request.setDisplayName("Test");

        // Act & Assert
        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(agentConfigService, never()).createAgent(any(AgentConfigDTO.class));
    }

    @Test
    void testUpdateAgent_ValidData_ReturnsOk() throws Exception {
        // Arrange
        UpdateAgentDTO request = new UpdateAgentDTO();
        request.setDisplayName("Updated Agent");

        AgentConfigDTO responseDTO = new AgentConfigDTO();
        responseDTO.setId(1L);
        responseDTO.setDisplayName("Updated Agent");

        when(agentConfigService.updateAgent(eq(1L), any(AgentConfigDTO.class))).thenReturn(responseDTO);

        // Act
        mockMvc.perform(put("/api/agents/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated Agent"));

        verify(agentConfigService).updateAgent(eq(1L), any(AgentConfigDTO.class));
    }

    @Test
    void testDeleteAgent_ValidId_ReturnsNoContent() throws Exception {
        // Arrange
        doNothing().when(agentConfigService).deleteAgent(1L);

        // Act
        mockMvc.perform(delete("/api/agents/1"))
                .andExpect(status().isOk());

        verify(agentConfigService).deleteAgent(1L);
    }

    @Test
    void testGetAgent_ExistingAgent_ReturnsAgent() throws Exception {
        // Arrange
        AgentConfigDTO agent = new AgentConfigDTO();
        agent.setId(1L);
        agent.setAgentName("test-agent");

        when(agentConfigService.getAgentById(1L)).thenReturn(agent);

        // Act
        mockMvc.perform(get("/api/agents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentName").value("test-agent"));

        verify(agentConfigService).getAgentById(1L);
    }

    @Test
    void testGetAllAgents_ReturnsAgentList() throws Exception {
        // Arrange
        AgentConfigDTO agent1 = new AgentConfigDTO();
        agent1.setId(1L);
        agent1.setAgentName("agent1");

        AgentConfigDTO agent2 = new AgentConfigDTO();
        agent2.setId(2L);
        agent2.setAgentName("agent2");

        when(agentConfigService.getAllAgents()).thenReturn(Arrays.asList(agent1, agent2));

        // Act
        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].agentName", containsInAnyOrder("agent1", "agent2")));

        verify(agentConfigService).getAllAgents();
    }

    @Test
    void testActivateAgent_ValidId_ReturnsOk() throws Exception {
        // Arrange
        doNothing().when(agentConfigService).activateAgent(1L);

        // Act
        mockMvc.perform(post("/api/agents/1/activate"))
                .andExpect(status().isOk());

        verify(agentConfigService).activateAgent(1L);
    }

    @Test
    void testDeactivateAgent_ValidId_ReturnsOk() throws Exception {
        // Arrange
        doNothing().when(agentConfigService).deactivateAgent(1L);

        // Act
        mockMvc.perform(post("/api/agents/1/deactivate"))
                .andExpect(status().isOk());

        verify(agentConfigService).deactivateAgent(1L);
    }

    @Test
    void testReloadAgent_ValidId_ReturnsOk() throws Exception {
        // Arrange
        AgentConfigDTO agent = new AgentConfigDTO();
        agent.setId(1L);
        agent.setAgentName("test-agent");

        when(agentConfigService.getAgentById(1L)).thenReturn(agent);
        doNothing().when(agentConfigService).reloadAgent("test-agent");

        // Act
        mockMvc.perform(post("/api/agents/1/reload"))
                .andExpect(status().isOk());

        verify(agentConfigService).reloadAgent("test-agent");
    }

    @Test
    void testReloadAllAgents_ReturnsOk() throws Exception {
        // Arrange
        doNothing().when(agentConfigService).reloadAllAgents();

        // Act
        mockMvc.perform(post("/api/agents/reload-all"))
                .andExpect(status().isOk());

        verify(agentConfigService).reloadAllAgents();
    }

    @Test
    void testCreateAgent_MissingRequiredField_ReturnsBadRequest() throws Exception {
        // Arrange - 缺少必填字段
        CreateAgentDTO request = new CreateAgentDTO();
        request.setDisplayName("Test"); // 缺少 agentName、modelId

        // Act & Assert
        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(agentConfigService, never()).createAgent(any(AgentConfigDTO.class));
    }
}
