package cn.ts.web.service;

import cn.ts.web.dto.agent.ModelConfigDTO;
import cn.ts.web.entity.ModelConfigEntity;
import cn.ts.web.mapper.ModelConfigMapper;
import cn.ts.web.service.impl.ModelConfigServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ModelConfigService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ModelConfigServiceTest {

    @Mock
    private ModelConfigMapper mapper;

    @Mock
    private ApiKeyEncryptionService encryptionService;

    @InjectMocks
    private ModelConfigServiceImpl modelConfigService;

    private ModelConfigDTO testDTO;
    private ModelConfigEntity testEntity;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        testDTO = new ModelConfigDTO();
        testDTO.setModelName("test-model");
        testDTO.setDisplayName("Test Model");
        testDTO.setProvider("openai");
        testDTO.setModelId("gpt-4");
        testDTO.setBaseUrl("https://api.openai.com");
        testDTO.setApiKey("test-api-key");
        testDTO.setIsActive(true);

        testEntity = new ModelConfigEntity();
        testEntity.setId(1L);
        testEntity.setModelName("test-model");
        testEntity.setDisplayName("Test Model");
        testEntity.setProvider("openai");
        testEntity.setModelId("gpt-4");
        testEntity.setBaseUrl("https://api.openai.com");
        testEntity.setIsActive(true);
        testEntity.setCreatedAt(Instant.now());
        testEntity.setUpdatedAt(Instant.now());
    }

    @Test
    void testCreateModel_ValidData_ReturnsModelDTO() {
        // Arrange
        when(mapper.countByModelName("test-model")).thenReturn(0);
        when(mapper.insert(any(ModelConfigEntity.class))).thenReturn(1);
        when(encryptionService.encrypt("test-api-key")).thenReturn("encrypted-key");

        // Act
        ModelConfigDTO result = modelConfigService.createModel(testDTO);

        // Assert
        assertNotNull(result);
        assertEquals("test-model", result.getModelName());
        assertEquals("Test Model", result.getDisplayName());
        verify(mapper).insert(any(ModelConfigEntity.class));
        verify(encryptionService).encrypt("test-api-key");
    }

    @Test
    void testCreateModel_DuplicateName_ThrowsException() {
        // Arrange
        when(mapper.countByModelName("test-model")).thenReturn(1);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            modelConfigService.createModel(testDTO);
        });

        verify(mapper, never()).insert(any(ModelConfigEntity.class));
    }

    @Test
    void testUpdateModel_ValidData_ReturnsUpdatedDTO() {
        // Arrange
        when(mapper.selectById(1L)).thenReturn(testEntity);
        when(mapper.countByModelNameExcludeId("test-model", 1L)).thenReturn(0);
        when(mapper.updateById(any(ModelConfigEntity.class))).thenReturn(1);

        testDTO.setDisplayName("Updated Model");

        // Act
        ModelConfigDTO result = modelConfigService.updateModel(1L, testDTO);

        // Assert
        assertNotNull(result);
        verify(mapper).updateById(any(ModelConfigEntity.class));
    }

    @Test
    void testUpdateModel_ModelNotFound_ThrowsException() {
        // Arrange
        when(mapper.selectById(1L)).thenReturn(null);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            modelConfigService.updateModel(1L, testDTO);
        });

        verify(mapper, never()).updateById(any(ModelConfigEntity.class));
    }

    @Test
    void testDeleteModel_ValidId_DeletesModel() {
        // Arrange
        when(mapper.deleteById(1L)).thenReturn(1);

        // Act
        modelConfigService.deleteModel(1L);

        // Assert
        verify(mapper).deleteById(1L);
    }

    @Test
    void testGetModelById_ExistingModel_ReturnsModel() {
        // Arrange
        when(mapper.selectById(1L)).thenReturn(testEntity);

        // Act
        ModelConfigDTO result = modelConfigService.getModelById(1L);

        // Assert
        assertNotNull(result);
        assertEquals("test-model", result.getModelName());
        verify(mapper).selectById(1L);
    }

    @Test
    void testGetModelById_NonExistingModel_ReturnsNull() {
        // Arrange
        when(mapper.selectById(1L)).thenReturn(null);

        // Act
        ModelConfigDTO result = modelConfigService.getModelById(1L);

        // Assert
        assertNull(result);
        verify(mapper).selectById(1L);
    }

    @Test
    void testGetModelByName_ExistingModel_ReturnsModel() {
        // Arrange
        when(mapper.selectByModelName("test-model")).thenReturn(java.util.Optional.of(testEntity));

        // Act
        ModelConfigDTO result = modelConfigService.getModelByName("test-model");

        // Assert
        assertNotNull(result);
        assertEquals("test-model", result.getModelName());
        verify(mapper).selectByModelName("test-model");
    }

    @Test
    void testGetAllModels_ReturnsModelList() {
        // Arrange
        when(mapper.selectAll()).thenReturn(Arrays.asList(testEntity));

        // Act
        List<ModelConfigDTO> result = modelConfigService.getAllModels();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(mapper).selectAll();
    }

    @Test
    void testGetActiveModels_ReturnsActiveModels() {
        // Arrange
        when(mapper.selectActive()).thenReturn(Arrays.asList(testEntity));

        // Act
        List<ModelConfigDTO> result = modelConfigService.getActiveModels();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(mapper).selectActive();
    }

    @Test
    void testGetModelsByProvider_ReturnsModels() {
        // Arrange
        when(mapper.selectByProvider("openai")).thenReturn(Arrays.asList(testEntity));

        // Act
        List<ModelConfigDTO> result = modelConfigService.getModelsByProvider("openai");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(mapper).selectByProvider("openai");
    }

    @Test
    void testGetProviders_ReturnsUniqueProviders() {
        // Arrange
        ModelConfigEntity entity2 = new ModelConfigEntity();
        entity2.setProvider("anthropic");
        when(mapper.selectAll()).thenReturn(Arrays.asList(testEntity, entity2));

        // Act
        List<String> result = modelConfigService.getProviders();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("openai"));
        assertTrue(result.contains("anthropic"));
        verify(mapper).selectAll();
    }

    @Test
    void testCreateModel_WithApiKey_EncryptsKey() {
        // Arrange
        when(mapper.countByModelName("test-model")).thenReturn(0);
        when(mapper.insert(any(ModelConfigEntity.class))).thenReturn(1);
        when(encryptionService.encrypt("test-api-key")).thenReturn("encrypted-key");

        // Act
        ModelConfigDTO result = modelConfigService.createModel(testDTO);

        // Assert
        verify(encryptionService).encrypt("test-api-key");
        verify(mapper).insert(argThat(entity ->
            "encrypted-key".equals(entity.getApiKeyEncrypted())
        ));
    }

    @Test
    void testUpdateModel_WithNewApiKey_EncryptsNewKey() {
        // Arrange
        when(mapper.selectById(1L)).thenReturn(testEntity);
        when(mapper.countByModelNameExcludeId("test-model", 1L)).thenReturn(0);
        when(mapper.updateById(any(ModelConfigEntity.class))).thenReturn(1);
        when(encryptionService.encrypt("new-api-key")).thenReturn("new-encrypted-key");

        testDTO.setApiKey("new-api-key");

        // Act
        modelConfigService.updateModel(1L, testDTO);

        // Assert
        verify(encryptionService).encrypt("new-api-key");
        verify(mapper).updateById(argThat(entity ->
            "new-encrypted-key".equals(entity.getApiKeyEncrypted())
        ));
    }

    @Test
    void testUpdateModel_WithoutApiKey_DoesNotEncrypt() {
        // Arrange
        when(mapper.selectById(1L)).thenReturn(testEntity);
        when(mapper.countByModelNameExcludeId("test-model", 1L)).thenReturn(0);
        when(mapper.updateById(any(ModelConfigEntity.class))).thenReturn(1);

        testDTO.setApiKey(null);

        // Act
        modelConfigService.updateModel(1L, testDTO);

        // Assert
        verify(encryptionService, never()).encrypt(anyString());
    }
}
