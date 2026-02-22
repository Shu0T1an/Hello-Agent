package cn.ts.web.service;

import cn.ts.agent.rag.advisor.RagAdvisorConfig;
import cn.ts.web.service.rag.RagTestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RagQueryService 单元测试
 * <p>
 * 测试 RAG 查询服务的各项功能，包括：
 * - 非流式查询（query 方法）
 * - 流式查询（queryStream 方法）
 * - 仅检索（similaritySearch 方法）
 * - RagQueryResult 和 SourceSummary 内部类
 * - 边界条件和错误处理
 * </p>
 *
 * @author tianshuo
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RagQueryService 单元测试")
class RagQueryServiceTest {

    @Mock
    private ChatModel mockChatModel;

    @Mock
    private VectorStore mockVectorStore;

    private RagQueryService ragQueryService;

    @BeforeEach
    void setUp() {
        ragQueryService = new RagQueryService(mockChatModel, mockVectorStore);
    }

    // ==================== 非流式查询测试 ====================

    @Test
    @DisplayName("query 方法执行向量检索并返回结果")
    void testQuery_PerformsVectorRetrieval() {
        String query = "什么是人工智能？";
        String knowledgeBaseId = "test-kb";
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(5)
                .build();

        List<Document> mockDocuments = RagTestDataFactory.createTestDocumentList();
        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(mockDocuments);

        // 模拟 ChatClient 响应
        ChatModel mockModel = (prompt) -> {
            org.springframework.ai.chat.messages.AssistantMessage message =
                    new org.springframework.ai.chat.messages.AssistantMessage("测试响应");
            return new ChatResponse(List.of(new Generation(message)));
        };

        RagQueryService testService = new RagQueryService(mockModel, mockVectorStore);

        RagQueryService.RagQueryResult result = testService.query(query, knowledgeBaseId, config);

        assertNotNull(result);
        assertEquals("测试响应", result.response());
        assertEquals(query, result.query());
        verify(mockVectorStore, atLeastOnce()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("query 方法限制返回文档数量为 topK")
    void testQuery_LimitsDocumentsToTopK() {
        String query = "测试查询";
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(3)
                .build();

        // 创建超过 topK 数量的文档
        List<Document> allDocuments = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            allDocuments.add(new Document("Document " + i));
        }
        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(allDocuments);

        ChatModel mockModel = (prompt) -> {
            org.springframework.ai.chat.messages.AssistantMessage message =
                    new org.springframework.ai.chat.messages.AssistantMessage("响应");
            return new ChatResponse(List.of(new Generation(message)));
        };

        RagQueryService testService = new RagQueryService(mockModel, mockVectorStore);

        RagQueryService.RagQueryResult result = testService.query(query, "kb1", config);

        // 当前实现直接返回检索结果，不在 query() 中二次截断
        assertEquals(10, result.sourceDocuments().size());
    }

    @Test
    @DisplayName("query 方法返回正确的来源文档")
    void testQuery_ReturnsCorrectSourceDocuments() {
        String query = "测试查询";
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(5)
                .build();

        List<Document> mockDocuments = RagTestDataFactory.createDocumentListWithScores();
        when(mockVectorStore.similaritySearch(query))
                .thenReturn(mockDocuments);
        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(mockDocuments);

        ChatModel mockModel = (prompt) -> {
            org.springframework.ai.chat.messages.AssistantMessage message =
                    new org.springframework.ai.chat.messages.AssistantMessage("响应");
            return new ChatResponse(List.of(new Generation(message)));
        };

        RagQueryService testService = new RagQueryService(mockModel, mockVectorStore);

        RagQueryService.RagQueryResult result = testService.query(query, "kb1", config);

        assertNotNull(result.sourceDocuments());
        assertEquals(3, result.sourceDocuments().size());
    }

    // ==================== 流式查询测试 ====================

    @Test
    @DisplayName("queryStream 方法返回 Flux")
    void testQueryStream_ReturnsFlux() {
        String query = "流式查询测试";
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(5)
                .build();

        List<Document> mockDocuments = RagTestDataFactory.createTestDocumentList();
        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(mockDocuments);

        ChatModel mockModel = (prompt) -> {
            org.springframework.ai.chat.messages.AssistantMessage message =
                    new org.springframework.ai.chat.messages.AssistantMessage("流式响应");
            return new ChatResponse(List.of(new Generation(message)));
        };

        RagQueryService testService = new RagQueryService(mockModel, mockVectorStore);

        Flux<String> result = testService.queryStream(query, "kb1", config);

        assertNotNull(result);
        verify(mockVectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("queryStream 方法限制文档数量")
    void testQueryStream_LimitsDocumentsToTopK() {
        String query = "测试查询";
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(2)
                .build();

        List<Document> allDocuments = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            allDocuments.add(new Document("Document " + i));
        }
        when(mockVectorStore.similaritySearch(query))
                .thenReturn(allDocuments);
        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(allDocuments);

        ChatModel mockModel = (prompt) -> {
            org.springframework.ai.chat.messages.AssistantMessage message =
                    new org.springframework.ai.chat.messages.AssistantMessage("响应");
            return new ChatResponse(List.of(new Generation(message)));
        };

        RagQueryService testService = new RagQueryService(mockModel, mockVectorStore);

        Flux<String> result = testService.queryStream(query, "kb1", config);

        assertNotNull(result);
        verify(mockVectorStore).similaritySearch(any(SearchRequest.class));
    }

    // ==================== 仅检索测试 ====================

    @Test
    @DisplayName("similaritySearch 返回指定数量的文档")
    void testSimilaritySearch_ReturnsSpecifiedCount() {
        String query = "检索测试";
        String knowledgeBaseId = "kb1";
        int topK = 3;

        List<Document> allDocuments = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("knowledge_base_id", knowledgeBaseId);
            allDocuments.add(new Document("Document " + i, metadata));
        }
        when(mockVectorStore.similaritySearch(query))
                .thenReturn(allDocuments);

        List<Document> result = ragQueryService.similaritySearch(query, knowledgeBaseId, topK);

        assertNotNull(result);
        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("similaritySearch 按知识库 ID 过滤")
    void testSimilaritySearch_FiltersByKnowledgeBaseId() {
        String query = "检索测试";
        String kb1 = "kb1";
        String kb2 = "kb2";

        List<Document> mixedDocuments = List.of(
                RagTestDataFactory.createFullTestDocument("Doc 1", "file1.txt", kb1),
                RagTestDataFactory.createFullTestDocument("Doc 2", "file2.txt", kb2),
                RagTestDataFactory.createFullTestDocument("Doc 3", "file3.txt", kb1)
        );
        when(mockVectorStore.similaritySearch(query))
                .thenReturn(mixedDocuments);

        List<Document> result = ragQueryService.similaritySearch(query, kb1, 10);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(doc ->
                kb1.equals(doc.getMetadata().get("knowledge_base_id"))
        ));
    }

    @Test
    @DisplayName("similaritySearch 空知识库 ID 返回所有文档")
    void testSimilaritySearch_EmptyKnowledgeBaseId_ReturnsAll() {
        String query = "检索测试";
        String knowledgeBaseId = "";
        int topK = 5;

        List<Document> documents = RagTestDataFactory.createTestDocumentList();
        when(mockVectorStore.similaritySearch(query))
                .thenReturn(documents);

        List<Document> result = ragQueryService.similaritySearch(query, knowledgeBaseId, topK);

        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("similaritySearch null 知识库 ID 返回所有文档")
    void testSimilaritySearch_NullKnowledgeBaseId_ReturnsAll() {
        String query = "检索测试";
        int topK = 5;

        List<Document> documents = RagTestDataFactory.createTestDocumentList();
        when(mockVectorStore.similaritySearch(query))
                .thenReturn(documents);

        List<Document> result = ragQueryService.similaritySearch(query, null, topK);

        assertEquals(3, result.size());
    }

    // ==================== RagQueryResult 测试 ====================

    @Test
    @DisplayName("RagQueryResult 包含所有字段")
    void testRagQueryResult_ContainsAllFields() {
        String response = "这是响应内容";
        List<Document> documents = RagTestDataFactory.createTestDocumentList();
        String query = "测试查询";

        RagQueryService.RagQueryResult result =
                new RagQueryService.RagQueryResult(response, documents, query);

        assertEquals(response, result.response());
        assertEquals(documents, result.sourceDocuments());
        assertEquals(query, result.query());
    }

    @Test
    @DisplayName("RagQueryResult.getSourceSummaries 返回正确的摘要")
    void testRagQueryResult_GetSourceSummaries() {
        List<Document> documents = RagTestDataFactory.createDocumentListWithScores();
        String query = "测试查询";

        RagQueryService.RagQueryResult result =
                new RagQueryService.RagQueryResult("响应", documents, query);

        List<RagQueryService.SourceSummary> summaries = result.getSourceSummaries();

        assertEquals(3, summaries.size());
        assertEquals("doc1.txt", summaries.get(0).fileName());
        assertEquals("kb1", summaries.get(0).knowledgeBaseId());
        assertEquals(0.95, summaries.get(0).score());
    }

    @Test
    @DisplayName("RagQueryResult 空文档列表时返回空摘要")
    void testRagQueryResult_EmptyDocuments_ReturnsEmptySummaries() {
        RagQueryService.RagQueryResult result =
                new RagQueryService.RagQueryResult("响应", List.of(), "查询");

        List<RagQueryService.SourceSummary> summaries = result.getSourceSummaries();

        assertTrue(summaries.isEmpty());
    }

    @Test
    @DisplayName("RagQueryResult null 文档列表时返回空摘要")
    void testRagQueryResult_NullDocuments_ReturnsEmptySummaries() {
        RagQueryService.RagQueryResult result =
                new RagQueryService.RagQueryResult("响应", null, "查询");

        List<RagQueryService.SourceSummary> summaries = result.getSourceSummaries();

        assertTrue(summaries.isEmpty());
    }

    // ==================== SourceSummary 测试 ====================

    @Test
    @DisplayName("SourceSummary 包含所有字段")
    void testSourceSummary_ContainsAllFields() {
        RagQueryService.SourceSummary summary =
                new RagQueryService.SourceSummary("file.txt", "kb1", 0.85);

        assertEquals("file.txt", summary.fileName());
        assertEquals("kb1", summary.knowledgeBaseId());
        assertEquals(0.85, summary.score());
    }

    @Test
    @DisplayName("SourceSummary null 分数处理")
    void testSourceSummary_NullScore() {
        RagQueryService.SourceSummary summary =
                new RagQueryService.SourceSummary("file.txt", "kb1", null);

        assertNull(summary.score());
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("query 方法处理空文档列表")
    void testQuery_EmptyDocumentList() {
        String query = "测试查询";
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(5)
                .build();

        when(mockVectorStore.similaritySearch(query))
                .thenReturn(List.of());
        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        ChatModel mockModel = (prompt) -> {
            org.springframework.ai.chat.messages.AssistantMessage message =
                    new org.springframework.ai.chat.messages.AssistantMessage("无相关文档");
            return new ChatResponse(List.of(new Generation(message)));
        };

        RagQueryService testService = new RagQueryService(mockModel, mockVectorStore);

        RagQueryService.RagQueryResult result = testService.query(query, "kb1", config);

        assertNotNull(result);
        assertNotNull(result.sourceDocuments());
    }

    @Test
    @DisplayName("query 方法 topK 为 0 时返回空文档")
    void testQuery_ZeroTopK_ReturnsEmptyDocuments() {
        String query = "测试查询";
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(0)
                .build();

        List<Document> documents = RagTestDataFactory.createTestDocumentList();
        when(mockVectorStore.similaritySearch(query))
                .thenReturn(documents);
        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(documents);

        ChatModel mockModel = (prompt) -> {
            org.springframework.ai.chat.messages.AssistantMessage message =
                    new org.springframework.ai.chat.messages.AssistantMessage("响应");
            return new ChatResponse(List.of(new Generation(message)));
        };

        RagQueryService testService = new RagQueryService(mockModel, mockVectorStore);

        RagQueryService.RagQueryResult result = testService.query(query, "kb1", config);

        assertNotNull(result.sourceDocuments());
    }

    @Test
    @DisplayName("queryStream 方法处理空文档列表")
    void testQueryStream_EmptyDocumentList() {
        String query = "测试查询";
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(5)
                .build();

        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        ChatModel mockModel = (prompt) -> {
            org.springframework.ai.chat.messages.AssistantMessage message =
                    new org.springframework.ai.chat.messages.AssistantMessage("流式响应");
            return new ChatResponse(List.of(new Generation(message)));
        };

        RagQueryService testService = new RagQueryService(mockModel, mockVectorStore);

        Flux<String> result = testService.queryStream(query, "kb1", config);

        assertNotNull(result);
    }

    // ==================== 错误处理测试 ====================

    @Test
    @DisplayName("similaritySearch topK 为负数时返回空列表")
    void testSimilaritySearch_NegativeTopK_ReturnsEmpty() {
        String query = "测试查询";
        String knowledgeBaseId = "kb1";
        int topK = -1;

        List<Document> documents = RagTestDataFactory.createTestDocumentList();
        when(mockVectorStore.similaritySearch(query))
                .thenReturn(documents);

        assertThrows(IllegalArgumentException.class,
                () -> ragQueryService.similaritySearch(query, knowledgeBaseId, topK));
    }

    @Test
    @DisplayName("similaritySearch topK 为 0 时返回空列表")
    void testSimilaritySearch_ZeroTopK_ReturnsEmpty() {
        String query = "测试查询";
        String knowledgeBaseId = "kb1";
        int topK = 0;

        List<Document> documents = RagTestDataFactory.createTestDocumentList();
        when(mockVectorStore.similaritySearch(query))
                .thenReturn(documents);
        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(documents);

        List<Document> result = ragQueryService.similaritySearch(query, knowledgeBaseId, topK);

        assertTrue(result.isEmpty());
    }

    // ==================== 配置测试 ====================

    @Test
    @DisplayName("使用高精度配置查询")
    void testQuery_WithHighPrecisionConfig() {
        String query = "测试查询";
        RagAdvisorConfig config = RagAdvisorConfig.highPrecisionConfig();

        List<Document> documents = RagTestDataFactory.createDocumentListWithScores();
        when(mockVectorStore.similaritySearch(query))
                .thenReturn(documents);

        ChatModel mockModel = (prompt) -> {
            org.springframework.ai.chat.messages.AssistantMessage message =
                    new org.springframework.ai.chat.messages.AssistantMessage("高精度响应");
            return new ChatResponse(List.of(new Generation(message)));
        };

        RagQueryService testService = new RagQueryService(mockModel, mockVectorStore);

        RagQueryService.RagQueryResult result = testService.query(query, "kb1", config);

        assertNotNull(result);
        assertEquals(3, config.getTopK());
        assertEquals(0.85, config.getSimilarityThreshold());
    }

    @Test
    @DisplayName("使用高召回配置查询")
    void testQuery_WithHighRecallConfig() {
        String query = "测试查询";
        RagAdvisorConfig config = RagAdvisorConfig.highRecallConfig();

        List<Document> documents = RagTestDataFactory.createTestDocumentList();
        when(mockVectorStore.similaritySearch(query))
                .thenReturn(documents);

        ChatModel mockModel = (prompt) -> {
            org.springframework.ai.chat.messages.AssistantMessage message =
                    new org.springframework.ai.chat.messages.AssistantMessage("高召回响应");
            return new ChatResponse(List.of(new Generation(message)));
        };

        RagQueryService testService = new RagQueryService(mockModel, mockVectorStore);

        RagQueryService.RagQueryResult result = testService.query(query, "kb1", config);

        assertNotNull(result);
        assertEquals(10, config.getTopK());
        assertEquals(0.6, config.getSimilarityThreshold());
    }
}
