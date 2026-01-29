package cn.ts.agent.rag.config;

import lombok.Builder;
import lombok.Data;

/**
 * RAG 运行时配置
 * <p>
 * 从 State 中传递，用于动态启用 RAG
 * </p>
 *
 * @author tianshuo
 */
@Data
@Builder
public class RagConfig {
    /**
     * 知识库 ID
     */
    private String knowledgeBaseId;

    /**
     * 检索文档数量
     */
    @Builder.Default
    private Integer topK = 5;

    /**
     * 相似度阈值
     */
    @Builder.Default
    private Double similarityThreshold = 0.7;

    /**
     * 是否显示来源
     */
    @Builder.Default
    private Boolean includeSources = true;
}
