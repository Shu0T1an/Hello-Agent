package cn.ts.web.agent.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * 模型配置实体类
 */
@Data
public class ModelConfigEntity implements Serializable {

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
     * 加密后的API密钥
     */
    private String apiKeyEncrypted;

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
