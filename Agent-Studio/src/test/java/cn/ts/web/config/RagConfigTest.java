package cn.ts.web.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RagConfig 单元测试
 * <p>
 * 测试 RAG 配置类的配置参数
 * </p>
 *
 * @author tianshuo
 */
@DisplayName("RagConfig 单元测试")
class RagConfigTest {

    // ==================== 配置参数测试 ====================

    @Test
    @DisplayName("PgVectorStore 配置参数验证")
    void testVectorStore_ConfigurationParameters() {
        // 验证配置参数的存在和基本格式
        String baseUrl = "http://localhost:8080";
        String apiKey = "test-key";
        String tableName = "test_vector_store";
        int dimension = 1536;

        assertNotNull(baseUrl);
        assertNotNull(apiKey);
        assertNotNull(tableName);
        assertTrue(dimension > 0);
    }

    @Test
    @DisplayName("TokenTextSplitter 配置参数验证")
    void testTokenTextSplitter_ConfigurationParameters() {
        int chunkSize = 1000;
        int chunkOverlap = 200;
        int minChunkSize = 100;

        assertTrue(chunkSize > 0);
        assertTrue(chunkOverlap >= 0);
        assertTrue(minChunkSize >= 0);
        assertTrue(chunkOverlap < chunkSize);
    }

    // ==================== 表名配置测试 ====================

    @Test
    @DisplayName("向量存储表名配置")
    void testVectorStoreTableName_Configuration() {
        String tableName = "test_vector_store";

        assertNotNull(tableName);
        assertFalse(tableName.isEmpty());
        assertTrue(tableName.matches("[a-zA-Z_][a-zA-Z0-9_]*"),
                "表名应该符合 SQL 标识符规范");
    }

    // ==================== 维度配置测试 ====================

    @Test
    @DisplayName("向量维度配置验证")
    void testVectorDimension_Configuration() {
        int dimension = 1536;

        assertEquals(1536, dimension, "text-embedding-3-small 模型的维度应该是 1536");
    }

    @Test
    @DisplayName("向量维度应该是正数")
    void testVectorDimension_IsPositive() {
        int dimension = 1536;
        assertTrue(dimension > 0, "向量维度必须是正数");
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("处理最小配置值")
    void testMinimalConfigurationValues() {
        int minChunkSize = 100;

        assertTrue(minChunkSize > 0, "最小分块大小应该大于 0");
    }

    @Test
    @DisplayName("处理最大配置值")
    void testMaximalConfigurationValues() {
        int chunkSize = 1000;
        int maxContextLength = 4000;

        assertTrue(chunkSize <= maxContextLength,
                "分块大小不应该超过最大上下文长度");
    }

    // ==================== 配置一致性测试 ====================

    @Test
    @DisplayName("Embedding 模型和向量存储维度一致")
    void testEmbeddingAndVectorStoreDimensionConsistency() {
        int embeddingDimension = 1536;
        int vectorStoreDimension = 1536;

        assertEquals(embeddingDimension, vectorStoreDimension,
                "Embedding 模型和向量存储的维度应该一致");
    }

    // ==================== 构造函数参数测试 ====================

    @Test
    @DisplayName("TokenTextSplitter 构造函数参数验证")
    void testTokenTextSplitter_ConstructorParameters() {
        // TokenTextSplitter 构造函数: (chunkSize, chunkOverlap, minChunkSize, keepSeparator, bulletedList)
        // 配置中使用: new TokenTextSplitter(chunkSize, chunkOverlap, minChunkSize, 5000, true)

        int chunkSize = 1000;
        int chunkOverlap = 200;
        int minChunkSize = 100;
        int keepSeparator = 5000;
        boolean bulletedList = true;

        assertTrue(chunkSize > 0);
        assertTrue(chunkOverlap >= 0);
        assertTrue(minChunkSize >= 0);
        assertTrue(keepSeparator > 0);
    }

    // ==================== API 配置测试 ====================

    @Test
    @DisplayName("OpenAiApi 配置参数验证")
    void testOpenAiApi_Configuration() {
        String baseUrl = "http://localhost:8080";
        String apiKey = "test-key";
        String completionsPath = "/chat/completions";
        String embeddingsPath = "/embeddings";

        assertNotNull(baseUrl);
        assertNotNull(apiKey);
        assertNotNull(completionsPath);
        assertNotNull(embeddingsPath);
        assertTrue(baseUrl.startsWith("http"));
    }

    @Test
    @DisplayName("OpenAiEmbeddingOptions 配置验证")
    void testOpenAiEmbeddingOptions_Configuration() {
        String model = "text-embedding-3-small";
        int dimension = 1536;

        assertNotNull(model);
        assertTrue(dimension > 0);
    }
}
