package cn.ts.web.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * 知识库实体类
 * <p>
 * 对应数据库表 knowledge_base
 * </p>
 *
 * @author tianshuo
 */
@Data
public class KnowledgeBaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 知识库ID（业务唯一标识）
     */
    private String kbId;

    /**
     * 知识库名称
     */
    private String kbName;

    /**
     * 描述
     */
    private String description;

    /**
     * 嵌入模型
     */
    private String embeddingModel;

    /**
     * 向量维度
     */
    private Integer dimension;

    /**
     * 状态（ACTIVE/INACTIVE）
     */
    private String status;

    /**
     * 文档数量
     */
    private Integer documentCount;

    /**
     * 总块数
     */
    private Integer totalChunks;

    /**
     * 创建者
     */
    private String createdBy;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;
}
