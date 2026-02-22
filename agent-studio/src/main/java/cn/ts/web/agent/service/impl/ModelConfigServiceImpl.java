package cn.ts.web.agent.service.impl;

import cn.ts.web.agent.dto.ModelConfigDTO;
import cn.ts.web.agent.entity.ModelConfigEntity;
import cn.ts.web.agent.mapper.ModelConfigMapper;
import cn.ts.web.service.ApiKeyEncryptionService;
import cn.ts.web.agent.service.ModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型配置服务实现
 */
@Service
public class ModelConfigServiceImpl implements ModelConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ModelConfigServiceImpl.class);

    private final ModelConfigMapper mapper;
    private final ApiKeyEncryptionService encryptionService;

    public ModelConfigServiceImpl(ModelConfigMapper mapper, ApiKeyEncryptionService encryptionService) {
        this.mapper = mapper;
        this.encryptionService = encryptionService;
    }

    @Override
    @Transactional
    public ModelConfigDTO createModel(ModelConfigDTO dto) {
        // 检查模型名称是否已存在
        if (mapper.countByModelName(dto.getModelName()) > 0) {
            throw new IllegalArgumentException("Model name already exists: " + dto.getModelName());
        }

        // 转换为实体并加密密钥
        ModelConfigEntity entity = toEntity(dto);
        entity.setApiKeyEncrypted(encryptionService.encrypt(dto.getApiKey()));

        // 插入数据库
        mapper.insert(entity);

        // 返回 DTO（不包含加密后的密钥）
        return toDTO(entity);
    }

    @Override
    @Transactional
    public ModelConfigDTO updateModel(Long id, ModelConfigDTO dto) {
        // 检查是否存在
        ModelConfigEntity existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Model not found with id: " + id);
        }

        // 检查模型名称是否已被其他记录使用
        if (mapper.countByModelNameExcludeId(dto.getModelName(), id) > 0) {
            throw new IllegalArgumentException("Model name already exists: " + dto.getModelName());
        }

        // 更新实体（如果提供了新密钥则加密）
        existing.setDisplayName(dto.getDisplayName());
        existing.setProvider(dto.getProvider());
        existing.setModelId(dto.getModelId());
        existing.setBaseUrl(dto.getBaseUrl());
        if (dto.getApiKey() != null && !dto.getApiKey().isEmpty()) {
            existing.setApiKeyEncrypted(encryptionService.encrypt(dto.getApiKey()));
        }
        existing.setIsActive(dto.getIsActive());

        mapper.updateById(existing);

        return toDTO(existing);
    }

    @Override
    @Transactional
    public void deleteModel(Long id) {
        // 检查是否被 Agent 引用
        // TODO: 添加外键检查逻辑

        mapper.deleteById(id);
    }

    @Override
    public ModelConfigDTO getModelById(Long id) {
        ModelConfigEntity entity = mapper.selectById(id);
        if (entity != null) {
            ModelConfigDTO dto = toDTO(entity);
            // 从数据库加载的配置包含加密后的密钥，用于创建 ChatModel
            dto.setApiKey(entity.getApiKeyEncrypted());
            return dto;
        }
        return null;
    }

    @Override
    public ModelConfigDTO getModelByName(String modelName) {
        return mapper.selectByModelName(modelName)
                .map(entity -> {
                    ModelConfigDTO dto = toDTO(entity);
                    dto.setApiKey(entity.getApiKeyEncrypted());
                    return dto;
                })
                .orElse(null);
    }

    @Override
    public List<ModelConfigDTO> getAllModels() {
        return mapper.selectAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ModelConfigDTO> getActiveModels() {
        return mapper.selectActive().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ModelConfigDTO> getModelsByProvider(String provider) {
        return mapper.selectByProvider(provider).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getProviders() {
        return mapper.selectAll().stream()
                .map(ModelConfigEntity::getProvider)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public ChatModel createChatModel(ModelConfigDTO config) {
        if (config == null) {
            throw new IllegalArgumentException("Model config cannot be null");
        }

        logger.info("Creating ChatModel: provider={}, modelId={}", config.getProvider(), config.getModelId());

        // 解密 API 密钥
        String apiKey = encryptionService.decrypt(config.getApiKey());
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API key is required for model: " + config.getModelName());
        }

        // 根据提供商创建对应的 ChatModel
        return switch (config.getProvider().toUpperCase()) {
            case "OPENAI", "DEEPSEEK", "GROQ", "PERPLEXITY", "OPENAI_COMPATIBLE" ->
                createOpenAiCompatibleChatModel(config, apiKey);
            default -> throw new IllegalArgumentException(
                "Unsupported provider: " + config.getProvider() + ". " +
                "Supported providers: OPENAI, DEEPSEEK, GROQ, PERPLEXITY, OPENAI_COMPATIBLE"
            );
        };
    }

    /**
     * 创建 OpenAI 兼容的 ChatModel
     * <p>
     * 支持所有使用 OpenAI API 格式的提供商，包括：
     * - OpenAI
     * - DeepSeek
     * - Groq
     * - Perplexity
     * - 其他 OpenAI 兼容的 API
     * </p>
     *
     * @param config  模型配置
     * @param apiKey  API 密钥（已解密）
     * @return ChatModel 实例
     */
    private ChatModel createOpenAiCompatibleChatModel(ModelConfigDTO config, String apiKey) {


        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(apiKey)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(config.getModelName())
                        .build())
                .build();

        logger.info("Created OpenAI-compatible ChatModel: baseUrl={}, model={}", config.getBaseUrl(), config.getModelId());
        return chatModel;
    }

    /**
     * 实体转 DTO
     */
    private ModelConfigDTO toDTO(ModelConfigEntity entity) {
        ModelConfigDTO dto = new ModelConfigDTO();
        dto.setId(entity.getId());
        dto.setModelName(entity.getModelName());
        dto.setDisplayName(entity.getDisplayName());
        dto.setProvider(entity.getProvider());
        dto.setModelId(entity.getModelId());
        dto.setBaseUrl(entity.getBaseUrl());
        // DTO 中默认不返回密钥
        dto.setIsActive(entity.getIsActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    /**
     * DTO 转实体
     */
    private ModelConfigEntity toEntity(ModelConfigDTO dto) {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setModelName(dto.getModelName());
        entity.setDisplayName(dto.getDisplayName());
        entity.setProvider(dto.getProvider());
        entity.setModelId(dto.getModelId());
        entity.setBaseUrl(dto.getBaseUrl());
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        return entity;
    }
}
