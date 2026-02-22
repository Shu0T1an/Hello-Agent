package cn.ts.web.service.rag;

import cn.ts.agent.rag.advisor.RagAdvisorConfig;
import org.springframework.ai.document.Document;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 测试数据工厂
 * <p>
 * 提供测试所需的各种数据对象
 * </p>
 *
 * @author tianshuo
 */
public class RagTestDataFactory {

    private RagTestDataFactory() {
        // 工具类，禁止实例化
    }

    // ==================== Document 相关 ====================

    /**
     * 创建简单的测试文档
     */
    public static Document createTestDocument(String text) {
        return new Document(text);
    }

    /**
     * 创建带元数据的测试文档
     */
    public static Document createTestDocument(String text, Map<String, Object> metadata) {
        return new Document(text, metadata);
    }

    /**
     * 创建完整的测试文档（包含所有标准元数据）
     */
    public static Document createFullTestDocument(String text, String fileName, String knowledgeBaseId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("file_name", fileName);
        metadata.put("knowledge_base_id", knowledgeBaseId);
        metadata.put("file_path", "/test/path/" + fileName);
        metadata.put("document_id", "test-doc-" + System.currentTimeMillis());
        metadata.put("upload_time", System.currentTimeMillis());
        metadata.put("distance", 0.85);
        return new Document(text, metadata);
    }

    /**
     * 创建测试文档列表
     */
    public static List<Document> createTestDocumentList() {
        return List.of(
                createTestDocument("这是第一段测试文本。", createMetadata("doc1.txt", "kb1")),
                createTestDocument("这是第二段测试文本。", createMetadata("doc2.txt", "kb1")),
                createTestDocument("这是第三段测试文本。", createMetadata("doc3.txt", "kb1"))
        );
    }

    /**
     * 创建带相似度的文档列表
     */
    public static List<Document> createDocumentListWithScores() {
        return List.of(
                createDocumentWithScore("高相似度文档", 0.95, "doc1.txt"),
                createDocumentWithScore("中等相似度文档", 0.80, "doc2.txt"),
                createDocumentWithScore("低相似度文档", 0.65, "doc3.txt")
        );
    }

    private static Document createDocumentWithScore(String text, double score, String fileName) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("file_name", fileName);
        metadata.put("knowledge_base_id", "kb1");
        metadata.put("distance", score);
        return new Document(text, metadata);
    }

    private static Map<String, Object> createMetadata(String fileName, String kbId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("file_name", fileName);
        metadata.put("knowledge_base_id", kbId);
        return metadata;
    }

    // ==================== MultipartFile 相关 ====================

    /**
     * 创建测试用的 TXT 文件
     */
    public static MultipartFile createTextFile(String filename, String content) {
        return new MockMultipartFile(
                "file",
                filename,
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 创建测试用的 Markdown 文件
     */
    public static MultipartFile createMarkdownFile(String filename, String content) {
        return new MockMultipartFile(
                "file",
                filename,
                "text/markdown",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 创建空的 MultipartFile
     */
    public static MultipartFile createEmptyFile(String filename) {
        return new MockMultipartFile(
                "file",
                filename,
                "text/plain",
                new byte[0]
        );
    }

    /**
     * 创建大文件用于测试边界条件
     */
    public static MultipartFile createLargeFile(String filename, int sizeInBytes) {
        byte[] content = new byte[sizeInBytes];
        // 填充测试数据
        for (int i = 0; i < sizeInBytes; i++) {
            content[i] = (byte) ('a' + (i % 26));
        }
        return new MockMultipartFile(
                "file",
                filename,
                "text/plain",
                content
        );
    }

    /**
     * 创建包含特殊字符的文件
     */
    public static MultipartFile createFileWithSpecialChars(String filename) {
        String content = "测试内容\n" +
                "Special chars: !@#$%^&*()\n" +
                "Unicode: 中文 日本語 한국어\n" +
                "Quotes: \"'`\n" +
                "Tabs:\t\tnewlines:\n\n";
        return new MockMultipartFile(
                "file",
                filename,
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    // ==================== RagAdvisorConfig 相关 ====================

    /**
     * 创建默认配置
     */
    public static RagAdvisorConfig createDefaultConfig() {
        return RagAdvisorConfig.defaultConfig();
    }

    /**
     * 创建自定义配置
     */
    public static RagAdvisorConfig createCustomConfig(int topK, double similarityThreshold) {
        return RagAdvisorConfig.builder()
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
    }

    /**
     * 创建高精度配置
     */
    public static RagAdvisorConfig createHighPrecisionConfig() {
        return RagAdvisorConfig.highPrecisionConfig();
    }

    /**
     * 创建高召回配置
     */
    public static RagAdvisorConfig createHighRecallConfig() {
        return RagAdvisorConfig.highRecallConfig();
    }

    // ==================== 测试字符串 ====================

    /**
     * 获取测试查询字符串
     */
    public static String getTestQuery() {
        return "什么是人工智能？";
    }

    /**
     * 获取长文本查询（用于测试上下文限制）
     */
    public static String getLongQuery() {
        return "这是一个很长的查询。" + "重复内容 ".repeat(100);
    }

    /**
     * 获取测试响应内容
     */
    public static String getTestResponse() {
        return "这是一个测试响应。";
    }

    /**
     * 获取空字符串
     */
    public static String getEmptyString() {
        return "";
    }

    /**
     * 获取空白字符串
     */
    public static String getBlankString() {
        return "   \n\t  ";
    }

    // ==================== 测试常量 ====================

    public static final String TEST_KNOWLEDGE_BASE_ID = "test-kb-1";
    public static final String DEFAULT_KNOWLEDGE_BASE_ID = "default";
    public static final String TEST_FILE_NAME_TXT = "test.txt";
    public static final String TEST_FILE_NAME_MD = "test.md";
    public static final String TEST_FILE_NAME_PDF = "test.pdf";
    public static final String UNSUPPORTED_FILE_NAME = "test.docx";
}
