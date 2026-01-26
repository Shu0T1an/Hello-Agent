package cn.ts.web.service;

import cn.ts.web.dto.agent.ModelConfigDTO;
import cn.ts.web.entity.ModelConfigEntity;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

/**
 * 模型配置服务接口
 */
public interface ModelConfigService {

    /**
     * 创建模型配置
     *
     * @param dto 模型配置 DTO
     * @return 创建后的模型配置
     */
    ModelConfigDTO createModel(ModelConfigDTO dto);

    /**
     * 更新模型配置
     *
     * @param id  模型配置 ID
     * @param dto 模型配置 DTO
     * @return 更新后的模型配置
     */
    ModelConfigDTO updateModel(Long id, ModelConfigDTO dto);

    /**
     * 删除模型配置
     *
     * @param id 模型配置 ID
     */
    void deleteModel(Long id);

    /**
     * 根据ID获取模型配置
     *
     * @param id 模型配置 ID
     * @return 模型配置
     */
    ModelConfigDTO getModelById(Long id);

    /**
     * 根据模型名称获取模型配置
     *
     * @param modelName 模型名称
     * @return 模型配置
     */
    ModelConfigDTO getModelByName(String modelName);

    /**
     * 获取所有模型配置
     *
     * @return 模型配置列表
     */
    List<ModelConfigDTO> getAllModels();

    /**
     * 获取激活的模型配置
     *
     * @return 模型配置列表
     */
    List<ModelConfigDTO> getActiveModels();

    /**
     * 根据提供商获取模型配置
     *
     * @param provider 提供商
     * @return 模型配置列表
     */
    List<ModelConfigDTO> getModelsByProvider(String provider);

    /**
     * 获取所有提供商
     *
     * @return 提供商列表
     */
    List<String> getProviders();

    /**
     * 根据配置创建 ChatModel 实例
     *
     * @param config 模型配置
     * @return ChatModel 实例
     */
    ChatModel createChatModel(ModelConfigDTO config);
}
