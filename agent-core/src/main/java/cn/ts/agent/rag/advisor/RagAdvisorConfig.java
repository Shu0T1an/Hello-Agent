package cn.ts.agent.rag.advisor;

import lombok.Builder;
import lombok.Data;

/**
 * RAG Advisor 配置类
 * <p>
 * 用于配置 RAG Advisor 的行为参数，包括：
 * - 检索文档数量
 * - 相似度阈值
 * - 系统提示词
 * </p>
 *
 * @author tianshuo
 */
@Data
@Builder
public class RagAdvisorConfig {

    /**
     * 检索的文档数量（top-K）
     */
    @Builder.Default
    private int topK = 5;

    /**
     * 相似度阈值（0-1），低于此值的文档将被过滤
     */
    @Builder.Default
    private double similarityThreshold = 0.7;

    /**
     * RAG 系统提示词
     */
    @Builder.Default
    private String systemPrompt = "你是一个智能助手。请基于以下参考上下文回答用户问题。如果上下文中没有相关信息，请明确告知用户。";

    /**
     * 是否在响应中包含来源信息
     */
    @Builder.Default
    private boolean includeSources = true;

    /**
     * 上下文窗口最大字符数
     */
    @Builder.Default
    private int maxContextLength = 4000;

    /**
     * 默认配置
     */
    public static RagAdvisorConfig defaultConfig() {
        return RagAdvisorConfig.builder().build();
    }

    /**
     * 高精度配置（更严格的相似度阈值，更少的文档）
     */
    public static RagAdvisorConfig highPrecisionConfig() {
        return RagAdvisorConfig.builder()
                .topK(3)
                .similarityThreshold(0.85)
                .build();
    }

    /**
     * 高召回配置（更宽松的相似度阈值，更多的文档）
     */
    public static RagAdvisorConfig highRecallConfig() {
        return RagAdvisorConfig.builder()
                .topK(10)
                .similarityThreshold(0.6)
                .build();
    }
}
