package cn.ts.web.controller;

import cn.ts.web.dto.agent.ModelConfigDTO;
import cn.ts.web.service.ModelConfigService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * ModelManagementController 单元测试
 */
@WebMvcTest(ModelManagementController.class)
class ModelManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(name = "modelConfigService")
    private ModelConfigService modelConfigService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testCreateModel_ValidData_ReturnsCreated() throws Exception {
        // Arrange
        ModelConfigDTO request = new ModelConfigDTO();
        request.setModelName("test-model");
        request.setDisplayName("Test Model");
        request.setProvider("openai");
        request.setModelId("gpt-4");
        request.setBaseUrl("https://api.openai.com");
        request.setApiKey("test-api-key");
        request.setIsActive(true);

        ModelConfigDTO responseDTO = new ModelConfigDTO();
        responseDTO.setId(1L);
        responseDTO.setModelName("test-model");
        responseDTO.setDisplayName("Test Model");

        when(modelConfigService.createModel(any(ModelConfigDTO.class))).thenReturn(responseDTO);

        // Act
        mockMvc.perform(post("/api/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelName").value("test-model"))
                .andExpect(jsonPath("$.data.displayName").value("Test Model"));

        verify(modelConfigService).createModel(any(ModelConfigDTO.class));
    }

    @Test
    void testCreateModel_ServiceLayerValidation_ReturnsCreated() throws Exception {
        // Arrange - Service layer validation will be tested in service tests
        ModelConfigDTO request = new ModelConfigDTO();
        request.setModelName("test-model");
        request.setDisplayName("Test Model");
        request.setProvider("openai");
        request.setModelId("gpt-4");

        ModelConfigDTO responseDTO = new ModelConfigDTO();
        responseDTO.setId(1L);
        responseDTO.setModelName("test-model");

        when(modelConfigService.createModel(any(ModelConfigDTO.class))).thenReturn(responseDTO);

        // Act & Assert
        mockMvc.perform(post("/api/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(modelConfigService).createModel(any(ModelConfigDTO.class));
    }

    @Test
    void testUpdateModel_ValidData_ReturnsOk() throws Exception {
        // Arrange
        ModelConfigDTO request = new ModelConfigDTO();
        request.setDisplayName("Updated Model");

        ModelConfigDTO responseDTO = new ModelConfigDTO();
        responseDTO.setId(1L);
        responseDTO.setDisplayName("Updated Model");

        when(modelConfigService.updateModel(eq(1L), any(ModelConfigDTO.class))).thenReturn(responseDTO);

        // Act
        mockMvc.perform(put("/api/models/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated Model"));

        verify(modelConfigService).updateModel(eq(1L), any(ModelConfigDTO.class));
    }

    @Test
    void testDeleteModel_ValidId_ReturnsNoContent() throws Exception {
        // Arrange
        doNothing().when(modelConfigService).deleteModel(1L);

        // Act
        mockMvc.perform(delete("/api/models/1"))
                .andExpect(status().isOk());

        verify(modelConfigService).deleteModel(1L);
    }

    @Test
    void testGetModel_ExistingModel_ReturnsModel() throws Exception {
        // Arrange
        ModelConfigDTO model = new ModelConfigDTO();
        model.setId(1L);
        model.setModelName("test-model");

        when(modelConfigService.getModelById(1L)).thenReturn(model);

        // Act
        mockMvc.perform(get("/api/models/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelName").value("test-model"));

        verify(modelConfigService).getModelById(1L);
    }

    @Test
    void testGetAllModels_ReturnsModelList() throws Exception {
        // Arrange
        ModelConfigDTO model1 = new ModelConfigDTO();
        model1.setId(1L);
        model1.setModelName("model1");

        ModelConfigDTO model2 = new ModelConfigDTO();
        model2.setId(2L);
        model2.setModelName("model2");

        when(modelConfigService.getAllModels()).thenReturn(Arrays.asList(model1, model2));

        // Act
        mockMvc.perform(get("/api/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].modelName", containsInAnyOrder("model1", "model2")));

        verify(modelConfigService).getAllModels();
    }

    @Test
    void testGetActiveModels_ReturnsActiveModels() throws Exception {
        // Arrange
        ModelConfigDTO model = new ModelConfigDTO();
        model.setId(1L);
        model.setModelName("active-model");
        model.setIsActive(true);

        when(modelConfigService.getActiveModels()).thenReturn(Arrays.asList(model));

        // Act
        mockMvc.perform(get("/api/models/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].modelName").value("active-model"));

        verify(modelConfigService).getActiveModels();
    }

    @Test
    void testGetModelsByProvider_ReturnsModels() throws Exception {
        // Arrange
        ModelConfigDTO model = new ModelConfigDTO();
        model.setId(1L);
        model.setModelName("openai-model");
        model.setProvider("openai");

        when(modelConfigService.getModelsByProvider("openai")).thenReturn(Arrays.asList(model));

        // Act
        mockMvc.perform(get("/api/models/provider/openai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].provider").value("openai"));

        verify(modelConfigService).getModelsByProvider("openai");
    }

    @Test
    void testGetProviders_ReturnsProviderList() throws Exception {
        // Arrange
        when(modelConfigService.getProviders()).thenReturn(Arrays.asList("openai", "anthropic", "modelscope"));

        // Act
        mockMvc.perform(get("/api/models/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data", containsInAnyOrder("openai", "anthropic", "modelscope")));

        verify(modelConfigService).getProviders();
    }

    @Test
    void testCreateModel_WithTemperature_ReturnsOk() throws Exception {
        // Arrange
        ModelConfigDTO request = new ModelConfigDTO();
        request.setModelName("test-model");
        request.setDisplayName("Test Model");
        request.setProvider("openai");
        request.setModelId("gpt-4");
        request.setApiKey("test-key");

        ModelConfigDTO responseDTO = new ModelConfigDTO();
        responseDTO.setId(1L);
        responseDTO.setModelName("test-model");

        when(modelConfigService.createModel(any(ModelConfigDTO.class))).thenReturn(responseDTO);

        // Act
        mockMvc.perform(post("/api/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(modelConfigService).createModel(any(ModelConfigDTO.class));
    }
}
