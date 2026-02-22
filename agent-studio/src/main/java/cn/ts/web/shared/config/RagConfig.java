package cn.ts.web.shared.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * RAG 配置类
 * <p>
 * PgVectorStore 在 Spring AI 1.0.0 中没有提供自动配置的 starter，
 * 因此需要手动创建 Bean
 * </p>
 *
 * @author: ts
 * @description
 * @create: 2026/1/26 22:52
 */
@Configuration
public class RagConfig {

    /**
     * PgVector 专用的 JdbcTemplate Bean
     * <p>
     * 使用 Spring Boot 自动配置的 DataSource 创建
     * </p>
     */
    @Bean("pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * PgVectorStore Bean
     * <p>
     * 依赖 pgVectorJdbcTemplate，该 Bean 由上面定义的方法创建
     * </p>
     */
    @Bean("vectorStore")
    @ConditionalOnBean(name = "pgVectorJdbcTemplate")
    public PgVectorStore pgVectorStore(
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String tableName,
            @Value("${spring.ai.vectorstore.pgvector.dimension:1536}") int dimension,
            JdbcTemplate jdbcTemplate) {

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey("sk-1c7794d42765440fa4913b1cbb097fb2")
                .completionsPath("/chat/completions")
                .embeddingsPath("/embeddings")
                .build();

        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model("text-embedding-v4")
                        .dimensions(dimension)
                        .build());
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName(tableName)
                .build();
    }

    /**
     * 文本分块器 Bean
     * <p>
     * 配置参数来自 application.yml:
     * - rag.text-splitter.chunk-size: 分块大小
     * - rag.text-splitter.chunk-overlap: 分块重叠
     * </p>
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter(
            @Value("${rag.text-splitter.chunk-size:1000}") int chunkSize,
            @Value("${rag.text-splitter.chunk-overlap:200}") int chunkOverlap,
            @Value("${rag.text-splitter.min-chunk-size:100}") int minChunkSize) {

        // TokenTextSplitter 构造函数: (chunkSize, chunkOverlap, minChunkSize, keepSeparator, bulletedList)
        return new TokenTextSplitter(chunkSize, chunkOverlap, minChunkSize, 5000, true);
    }
}
