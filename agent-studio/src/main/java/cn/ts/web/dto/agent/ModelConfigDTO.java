package cn.ts.web.dto.agent;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * 模型配置 DTO
 */
@Data
public class ModelConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 模型名称（唯一标识）
     */
    private String modelName;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 提供商（openai/anthropic/modelscope等）
     */
    private String provider;

    /**
     * 模型ID（如gpt-4-turbo）
     */
    private String modelId;

    /**
     * API基础URL
     */
    private String baseUrl;

    /**
     * API密钥（加密后的值）
     */
    private String apiKey;

    /**
     * 是否可用
     */
    private Boolean isActive;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;
}
