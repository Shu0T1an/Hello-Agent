package cn.ts.agent.rag.advisor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RagAdvisorConfig 单元测试
 * <p>
 * 测试 RAG Advisor 配置类的各项功能，包括：
 * - Builder 模式
 * - 静态工厂方法
 * - Lombok 生成方法
 * - 边界条件
 * </p>
 *
 * @author tianshuo
 */
@DisplayName("RagAdvisorConfig 单元测试")
class RagAdvisorConfigTest {

    // ==================== Builder 模式测试 ====================

    @Test
    @DisplayName("使用 Builder 创建默认配置")
    void testBuilder_CreateDefaultConfig() {
        RagAdvisorConfig config = RagAdvisorConfig.builder().build();

        assertNotNull(config);
        assertEquals(5, config.getTopK());
        assertEquals(0.7, config.getSimilarityThreshold());
        assertEquals("你是一个智能助手。请基于以下参考上下文回答用户问题。如果上下文中没有相关信息，请明确告知用户。",
                config.getSystemPrompt());
        assertTrue(config.isIncludeSources());
        assertEquals(4000, config.getMaxContextLength());
    }

    @Test
    @DisplayName("使用 Builder 自定义 topK")
    void testBuilder_CustomTopK() {
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(10)
                .build();

        assertEquals(10, config.getTopK());
    }

    @Test
    @DisplayName("使用 Builder 自定义 similarityThreshold")
    void testBuilder_CustomSimilarityThreshold() {
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .similarityThreshold(0.85)
                .build();

        assertEquals(0.85, config.getSimilarityThreshold(), 0.001);
    }

    @Test
    @DisplayName("使用 Builder 自定义 systemPrompt")
    void testBuilder_CustomSystemPrompt() {
        String customPrompt = "自定义系统提示词";
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .systemPrompt(customPrompt)
                .build();

        assertEquals(customPrompt, config.getSystemPrompt());
    }

    @Test
    @DisplayName("使用 Builder 自定义 includeSources")
    void testBuilder_CustomIncludeSources() {
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .includeSources(false)
                .build();

        assertFalse(config.isIncludeSources());
    }

    @Test
    @DisplayName("使用 Builder 自定义 maxContextLength")
    void testBuilder_CustomMaxContextLength() {
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .maxContextLength(8000)
                .build();

        assertEquals(8000, config.getMaxContextLength());
    }

    @Test
    @DisplayName("使用 Builder 设置所有属性")
    void testBuilder_AllProperties() {
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(7)
                .similarityThreshold(0.75)
                .systemPrompt("Test prompt")
                .includeSources(true)
                .maxContextLength(5000)
                .build();

        assertEquals(7, config.getTopK());
        assertEquals(0.75, config.getSimilarityThreshold(), 0.001);
        assertEquals("Test prompt", config.getSystemPrompt());
        assertTrue(config.isIncludeSources());
        assertEquals(5000, config.getMaxContextLength());
    }

    // ==================== 静态工厂方法测试 ====================

    @Test
    @DisplayName("defaultConfig() 返回默认配置")
    void testDefaultConfig() {
        RagAdvisorConfig config = RagAdvisorConfig.defaultConfig();

        assertEquals(5, config.getTopK());
        assertEquals(0.7, config.getSimilarityThreshold());
        assertEquals("你是一个智能助手。请基于以下参考上下文回答用户问题。如果上下文中没有相关信息，请明确告知用户。",
                config.getSystemPrompt());
        assertTrue(config.isIncludeSources());
        assertEquals(4000, config.getMaxContextLength());
    }

    @Test
    @DisplayName("highPrecisionConfig() 返回高精度配置")
    void testHighPrecisionConfig() {
        RagAdvisorConfig config = RagAdvisorConfig.highPrecisionConfig();

        assertEquals(3, config.getTopK());
        assertEquals(0.85, config.getSimilarityThreshold(), 0.001);
        assertEquals("你是一个智能助手。请基于以下参考上下文回答用户问题。如果上下文中没有相关信息，请明确告知用户。",
                config.getSystemPrompt());
        assertTrue(config.isIncludeSources());
        assertEquals(4000, config.getMaxContextLength());
    }

    @Test
    @DisplayName("highRecallConfig() 返回高召回配置")
    void testHighRecallConfig() {
        RagAdvisorConfig config = RagAdvisorConfig.highRecallConfig();

        assertEquals(10, config.getTopK());
        assertEquals(0.6, config.getSimilarityThreshold(), 0.001);
        assertEquals("你是一个智能助手。请基于以下参考上下文回答用户问题。如果上下文中没有相关信息，请明确告知用户。",
                config.getSystemPrompt());
        assertTrue(config.isIncludeSources());
        assertEquals(4000, config.getMaxContextLength());
    }

    // ==================== Lombok 生成方法测试 ====================

    @Test
    @DisplayName("Getter 方法正常工作")
    void testGetters() {
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(8)
                .similarityThreshold(0.8)
                .systemPrompt("Getter test")
                .includeSources(false)
                .maxContextLength(6000)
                .build();

        assertEquals(8, config.getTopK());
        assertEquals(0.8, config.getSimilarityThreshold(), 0.001);
        assertEquals("Getter test", config.getSystemPrompt());
        assertFalse(config.isIncludeSources());
        assertEquals(6000, config.getMaxContextLength());
    }

    @Test
    @DisplayName("Setter 方法正常工作")
    void testSetters() {
        RagAdvisorConfig config = RagAdvisorConfig.builder().build();

        config.setTopK(15);
        config.setSimilarityThreshold(0.9);
        config.setSystemPrompt("Setter test");
        config.setIncludeSources(true);
        config.setMaxContextLength(10000);

        assertEquals(15, config.getTopK());
        assertEquals(0.9, config.getSimilarityThreshold(), 0.001);
        assertEquals("Setter test", config.getSystemPrompt());
        assertTrue(config.isIncludeSources());
        assertEquals(10000, config.getMaxContextLength());
    }

    @Test
    @DisplayName("equals() 方法正常工作")
    void testEquals() {
        RagAdvisorConfig config1 = RagAdvisorConfig.builder()
                .topK(5)
                .similarityThreshold(0.7)
                .build();

        RagAdvisorConfig config2 = RagAdvisorConfig.builder()
                .topK(5)
                .similarityThreshold(0.7)
                .build();

        RagAdvisorConfig config3 = RagAdvisorConfig.builder()
                .topK(10)
                .similarityThreshold(0.8)
                .build();

        assertEquals(config1, config2);
        assertNotEquals(config1, config3);
    }

    @Test
    @DisplayName("hashCode() 方法正常工作")
    void testHashCode() {
        RagAdvisorConfig config1 = RagAdvisorConfig.builder()
                .topK(5)
                .similarityThreshold(0.7)
                .build();

        RagAdvisorConfig config2 = RagAdvisorConfig.builder()
                .topK(5)
                .similarityThreshold(0.7)
                .build();

        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    @DisplayName("toString() 方法包含关键信息")
    void testToString() {
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(5)
                .similarityThreshold(0.7)
                .build();

        String str = config.toString();

        assertTrue(str.contains("5") || str.contains("topK"));
        assertTrue(str.contains("0.7") || str.contains("similarityThreshold"));
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("topK 边界值：最小值 1")
    void testTopK_MinimumValue() {
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(1)
                .build();

        assertEquals(1, config.getTopK());
    }

    @Test
    @DisplayName("topK 边界值：较大值")
    void testTopK_LargeValue() {
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(1000)
                .build();

        assertEquals(1000, config.getTopK());
    }

    @Test
    @DisplayName("similarityThreshold 边界值：0.0")
    void testSimilarityThreshold_Zero() {
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .similarityThreshold(0.0)
                .build();

        assertEquals(0.0, config.getSimilarityThreshold(), 0.001);
    }

    @Test
    @DisplayName("similarityThreshold 边界值：1.0")
    void testSimilarityThreshold_One() {
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .similarityThreshold(1.0)
                .build();

        assertEquals(1.0, config.getSimilarityThreshold(), 0.001);
    }

    @Test
    @DisplayName("maxContextLength 边界值：较小值")
    void testMaxContextLength_SmallValue() {
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .maxContextLength(100)
                .build();

        assertEquals(100, config.getMaxContextLength());
    }

    @Test
    @DisplayName("maxContextLength 边界值：较大值")
    void testMaxContextLength_LargeValue() {
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .maxContextLength(100000)
                .build();

        assertEquals(100000, config.getMaxContextLength());
    }

    @Test
    @DisplayName("systemPrompt 空字符串")
    void testSystemPrompt_EmptyString() {
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .systemPrompt("")
                .build();

        assertEquals("", config.getSystemPrompt());
    }

    @Test
    @DisplayName("systemPrompt 包含特殊字符")
    void testSystemPrompt_SpecialChars() {
        String specialPrompt = "Test prompt with special chars: \n\t\"'`!@#$%^&*()";
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .systemPrompt(specialPrompt)
                .build();

        assertEquals(specialPrompt, config.getSystemPrompt());
    }

    // ==================== 不可变性测试 ====================

    @Test
    @DisplayName("Builder 返回新实例")
    void testBuilder_Immutable() {
        RagAdvisorConfig config1 = RagAdvisorConfig.builder()
                .topK(5)
                .build();

        RagAdvisorConfig config2 = RagAdvisorConfig.builder()
                .topK(10)
                .build();

        assertNotSame(config1, config2);
        assertEquals(5, config1.getTopK());
        assertEquals(10, config2.getTopK());
    }

    @Test
    @DisplayName("默认配置方法返回独立实例")
    void testDefaultConfig_IndependentInstances() {
        RagAdvisorConfig config1 = RagAdvisorConfig.defaultConfig();
        RagAdvisorConfig config2 = RagAdvisorConfig.defaultConfig();

        assertNotSame(config1, config2);
        assertEquals(config1, config2);
    }
}
