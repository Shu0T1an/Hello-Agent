package cn.ts.web.agent.controller;

import cn.ts.web.shared.response.Result;
import cn.ts.web.agent.dto.ModelConfigDTO;
import cn.ts.web.agent.service.ModelConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型管理 Controller
 */
@RestController
@RequestMapping("/api/models")
public class ModelManagementController {

    private final ModelConfigService modelConfigService;

    public ModelManagementController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    /**
     * 创建模型配置
     */
    @PostMapping
    public Result<ModelConfigDTO> createModel(@Valid @RequestBody ModelConfigDTO dto) {
        ModelConfigDTO created = modelConfigService.createModel(dto);
        return Result.success("创建成功", created);
    }

    /**
     * 更新模型配置
     */
    @PutMapping("/{id}")
    public Result<ModelConfigDTO> updateModel(
            @PathVariable Long id,
            @Valid @RequestBody ModelConfigDTO dto) {
        ModelConfigDTO updated = modelConfigService.updateModel(id, dto);
        return Result.success("更新成功", updated);
    }

    /**
     * 删除模型配置
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteModel(@PathVariable Long id) {
        modelConfigService.deleteModel(id);
        return Result.success();
    }

    /**
     * 获取单个模型配置
     */
    @GetMapping("/{id}")
    public Result<ModelConfigDTO> getModel(@PathVariable Long id) {
        ModelConfigDTO model = modelConfigService.getModelById(id);
        return Result.success(model);
    }

    /**
     * 获取所有模型配置
     */
    @GetMapping
    public Result<List<ModelConfigDTO>> getAllModels() {
        List<ModelConfigDTO> models = modelConfigService.getAllModels();
        return Result.success(models);
    }

    /**
     * 获取激活的模型配置
     */
    @GetMapping("/active")
    public Result<List<ModelConfigDTO>> getActiveModels() {
        List<ModelConfigDTO> models = modelConfigService.getActiveModels();
        return Result.success(models);
    }

    /**
     * 根据提供商获取模型配置
     */
    @GetMapping("/provider/{provider}")
    public Result<List<ModelConfigDTO>> getModelsByProvider(@PathVariable String provider) {
        List<ModelConfigDTO> models = modelConfigService.getModelsByProvider(provider);
        return Result.success(models);
    }

    /**
     * 获取所有提供商
     */
    @GetMapping("/providers")
    public Result<List<String>> getProviders() {
        List<String> providers = modelConfigService.getProviders();
        return Result.success(providers);
    }
}
