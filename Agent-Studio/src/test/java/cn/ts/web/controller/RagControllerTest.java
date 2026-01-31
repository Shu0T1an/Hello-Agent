package cn.ts.web.controller;

import cn.ts.web.service.DocumentLoaderService;
import cn.ts.web.service.KnowledgeBaseService;
import cn.ts.web.service.RagQueryService;
import cn.ts.web.service.rag.RagTestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RagController 单元测试
 * <p>
 * 测试 RAG 控制器的各项 API 端点，包括：
 * - 单文档上传 API
 * - 批量上传 API
 * - RAG 查询 API
 * - 流式查询（SSE）API
 * - 文档搜索 API
 * - 删除文档 API
 * - 参数验证
 * </p>
 *
 * @author tianshuo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RagController 单元测试")
class RagControllerTest {

    @Mock
    private DocumentLoaderService mockDocumentLoaderService;

    @Mock
    private RagQueryService mockRagQueryService;

    @Mock
    private KnowledgeBaseService mockKnowledgeBaseService;

    private RagController ragController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ragController = new RagController(mockDocumentLoaderService, mockRagQueryService, mockKnowledgeBaseService);
        mockMvc = MockMvcBuilders.standaloneSetup(ragController).build();
    }

    // ==================== 单文档上传 API 测试 ====================

    @Test
    @DisplayName("POST /api/rag/documents/upload - 上传文档成功")
    void testUploadDocument_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Test content".getBytes(StandardCharsets.UTF_8)
        );

        when(mockDocumentLoaderService.loadDocument(any(MultipartFile.class), eq("kb1")))
                .thenReturn(5);

        mockMvc.perform(multipart("/api/rag/documents/upload")
                        .file(file)
                        .param("knowledgeBaseId", "kb1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.fileName").value("test.txt"))
                .andExpect(jsonPath("$.data.knowledgeBaseId").value("kb1"))
                .andExpect(jsonPath("$.data.chunkCount").value(5))
                .andExpect(jsonPath("$.data.message").value("文档上传成功"));

        verify(mockDocumentLoaderService).loadDocument(any(MultipartFile.class), eq("kb1"));
    }

    @Test
    @DisplayName("POST /api/rag/documents/upload - 使用默认知识库 ID")
    void testUploadDocument_DefaultKnowledgeBaseId() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Test content".getBytes(StandardCharsets.UTF_8)
        );

        when(mockDocumentLoaderService.loadDocument(any(MultipartFile.class), eq("default")))
                .thenReturn(3);

        mockMvc.perform(multipart("/api/rag/documents/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.knowledgeBaseId").value("default"));

        verify(mockDocumentLoaderService).loadDocument(any(MultipartFile.class), eq("default"));
    }

    @Test
    @DisplayName("POST /api/rag/documents/upload - 上传失败返回错误")
    void testUploadDocument_Failure() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Test content".getBytes(StandardCharsets.UTF_8)
        );

        when(mockDocumentLoaderService.loadDocument(any(MultipartFile.class), anyString()))
                .thenThrow(new RuntimeException("文件处理失败"));

        mockMvc.perform(multipart("/api/rag/documents/upload")
                        .file(file)
                        .param("knowledgeBaseId", "kb1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("文档上传失败")));
    }

    // ==================== 批量上传 API 测试 ====================

    @Test
    @DisplayName("POST /api/rag/documents/batch-upload - 批量上传成功")
    void testBatchUploadDocuments_Success() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "files",
                "file1.txt",
                "text/plain",
                "Content 1".getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile file2 = new MockMultipartFile(
                "files",
                "file2.txt",
                "text/plain",
                "Content 2".getBytes(StandardCharsets.UTF_8)
        );

        when(mockDocumentLoaderService.loadDocument(any(MultipartFile.class), eq("kb1")))
                .thenReturn(3)
                .thenReturn(4);

        mockMvc.perform(multipart("/api/rag/documents/batch-upload")
                        .file(file1)
                        .file(file2)
                        .param("knowledgeBaseId", "kb1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.knowledgeBaseId").value("kb1"))
                .andExpect(jsonPath("$.data.totalChunks").value(7))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("批量上传完成")));

        verify(mockDocumentLoaderService, times(2)).loadDocument(any(MultipartFile.class), eq("kb1"));
    }

    @Test
    @DisplayName("POST /api/rag/documents/batch-upload - 部分文件失败")
    void testBatchUploadDocuments_PartialFailure() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "files",
                "file1.txt",
                "text/plain",
                "Content 1".getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile file2 = new MockMultipartFile(
                "files",
                "file2.txt",
                "text/plain",
                "Content 2".getBytes(StandardCharsets.UTF_8)
        );

        when(mockDocumentLoaderService.loadDocument(any(MultipartFile.class), eq("kb1")))
                .thenReturn(3)
                .thenThrow(new RuntimeException("处理失败"));

        mockMvc.perform(multipart("/api/rag/documents/batch-upload")
                        .file(file1)
                        .file(file2)
                        .param("knowledgeBaseId", "kb1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.fileCount").value(2))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("1/2")));
    }

    @Test
    @DisplayName("POST /api/rag/documents/batch-upload - 批量上传失败")
    void testBatchUploadDocuments_Failure() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "files",
                "file1.txt",
                "text/plain",
                "Content 1".getBytes(StandardCharsets.UTF_8)
        );

        when(mockDocumentLoaderService.loadDocument(any(MultipartFile.class), anyString()))
                .thenThrow(new RuntimeException("服务错误"));

        mockMvc.perform(multipart("/api/rag/documents/batch-upload")
                        .file(file1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("批量上传失败")));
    }

    // ==================== RAG 查询 API 测试 ====================

    @Test
    @DisplayName("POST /api/rag/query - 查询成功")
    void testQuery_Success() throws Exception {
        String requestBody = """
                {
                    "query": "什么是人工智能？",
                    "knowledgeBaseId": "kb1",
                    "topK": 5,
                    "similarityThreshold": 0.7
                }
                """;

        RagQueryService.RagQueryResult mockResult = new RagQueryService.RagQueryResult(
                "这是关于人工智能的回答。",
                RagTestDataFactory.createDocumentListWithScores(),
                "什么是人工智能？"
        );

        when(mockRagQueryService.query(eq("什么是人工智能？"), eq("kb1"), any()))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.response").value("这是关于人工智能的回答"))
                .andExpect(jsonPath("$.data.query").value("什么是人工智能？"));

        verify(mockRagQueryService).query(eq("什么是人工智能？"), eq("kb1"), any());
    }

    @Test
    @DisplayName("POST /api/rag/query - 查询失败")
    void testQuery_Failure() throws Exception {
        String requestBody = """
                {
                    "query": "测试查询"
                }
                """;

        when(mockRagQueryService.query(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("查询服务错误"));

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("查询失败")));
    }

    @Test
    @DisplayName("POST /api/rag/query - 使用默认参数")
    void testQuery_DefaultParameters() throws Exception {
        String requestBody = """
                {
                    "query": "测试查询"
                }
                """;

        RagQueryService.RagQueryResult mockResult = new RagQueryService.RagQueryResult(
                "响应",
                List.of(),
                "测试查询"
        );

        when(mockRagQueryService.query(anyString(), eq("default"), any()))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(mockRagQueryService).query(anyString(), eq("default"), any());
    }

    // ==================== 流式查询 API 测试 ====================

    @Test
    @DisplayName("POST /api/rag/query/stream - 流式查询返回 Flux")
    void testQueryStream_ReturnsFlux() throws Exception {
        String requestBody = """
                {
                    "query": "流式查询测试",
                    "knowledgeBaseId": "kb1"
                }
                """;

        Flux<String> mockFlux = Flux.just("这是", "流式", "响应");

        when(mockRagQueryService.queryStream(anyString(), anyString(), any()))
                .thenReturn(mockFlux);

        mockMvc.perform(post("/api/rag/query/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));

        verify(mockRagQueryService).queryStream(anyString(), anyString(), any());
    }

    // ==================== 文档搜索 API 测试 ====================

    @Test
    @DisplayName("POST /api/rag/search - 搜索成功")
    void testSearchDocuments_Success() throws Exception {
        String requestBody = """
                {
                    "query": "搜索测试",
                    "knowledgeBaseId": "kb1",
                    "topK": 5
                }
                """;

        List<org.springframework.ai.document.Document> mockDocs =
                RagTestDataFactory.createDocumentListWithScores();

        when(mockRagQueryService.similaritySearch(eq("搜索测试"), eq("kb1"), eq(5)))
                .thenReturn(mockDocs);

        mockMvc.perform(post("/api/rag/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        verify(mockRagQueryService).similaritySearch(eq("搜索测试"), eq("kb1"), eq(5));
    }

    @Test
    @DisplayName("POST /api/rag/search - 搜索失败")
    void testSearchDocuments_Failure() throws Exception {
        String requestBody = """
                {
                    "query": "搜索测试"
                }
                """;

        when(mockRagQueryService.similaritySearch(anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("搜索服务错误"));

        mockMvc.perform(post("/api/rag/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("搜索失败")));
    }

    @Test
    @DisplayName("POST /api/rag/search - 使用默认 topK")
    void testSearchDocuments_DefaultTopK() throws Exception {
        String requestBody = """
                {
                    "query": "搜索测试"
                }
                """;

        when(mockRagQueryService.similaritySearch(anyString(), eq("default"), eq(5)))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/rag/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(mockRagQueryService).similaritySearch(anyString(), eq("default"), eq(5));
    }

    // ==================== 删除文档 API 测试 ====================

    @Test
    @DisplayName("DELETE /api/rag/knowledge-bases/{kbId}/documents - 删除成功")
    void testDeleteKnowledgeBaseDocuments_Success() throws Exception {
        doNothing().when(mockDocumentLoaderService).deleteKnowledgeBaseDocuments("kb1");

        mockMvc.perform(delete("/api/rag/knowledge-bases/kb1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("知识库文档已删除"));

        verify(mockDocumentLoaderService).deleteKnowledgeBaseDocuments("kb1");
    }

    @Test
    @DisplayName("DELETE /api/rag/knowledge-bases/{kbId}/documents - 删除失败")
    void testDeleteKnowledgeBaseDocuments_Failure() throws Exception {
        doThrow(new RuntimeException("删除服务错误"))
                .when(mockDocumentLoaderService).deleteKnowledgeBaseDocuments("kb1");

        mockMvc.perform(delete("/api/rag/knowledge-bases/kb1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("删除失败")));
    }

    // ==================== 参数验证测试 ====================

    @Test
    @DisplayName("查询参数验证 - 空 topK 使用默认值")
    void testQueryValidation_NullTopK() throws Exception {
        String requestBody = """
                {
                    "query": "测试",
                    "topK": null
                }
                """;

        RagQueryService.RagQueryResult mockResult = new RagQueryService.RagQueryResult(
                "响应",
                List.of(),
                "测试"
        );

        when(mockRagQueryService.query(anyString(), anyString(), any()))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("查询参数验证 - 自定义 topK 和相似度阈值")
    void testQueryValidation_CustomParameters() throws Exception {
        String requestBody = """
                {
                    "query": "测试",
                    "topK": 10,
                    "similarityThreshold": 0.85
                }
                """;

        RagQueryService.RagQueryResult mockResult = new RagQueryService.RagQueryResult(
                "响应",
                List.of(),
                "测试"
        );

        when(mockRagQueryService.query(anyString(), anyString(), any()))
                .thenReturn(mockResult);

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        // 验证配置被正确构建
        verify(mockRagQueryService).query(anyString(), anyString(), any());
    }

    // ==================== DTO 类测试 ====================

    @Test
    @DisplayName("RagQueryRequest DTO - 设置和获取属性")
    void testRagQueryRequest_GettersAndSetters() {
        RagController.RagQueryRequest request = new RagController.RagQueryRequest();

        request.setQuery("测试查询");
        request.setKnowledgeBaseId("kb1");
        request.setTopK(10);
        request.setSimilarityThreshold(0.8);

        assertEquals("测试查询", request.getQuery());
        assertEquals("kb1", request.getKnowledgeBaseId());
        assertEquals(10, request.getTopK());
        assertEquals(0.8, request.getSimilarityThreshold(), 0.001);
    }

    @Test
    @DisplayName("RagQueryRequest DTO - 默认值")
    void testRagQueryRequest_DefaultValues() {
        RagController.RagQueryRequest request = new RagController.RagQueryRequest();

        assertEquals("default", request.getKnowledgeBaseId());
        assertEquals(5, request.getTopK());
        assertEquals(0.7, request.getSimilarityThreshold(), 0.001);
    }

    @Test
    @DisplayName("RagSearchRequest DTO - 设置和获取属性")
    void testRagSearchRequest_GettersAndSetters() {
        RagController.RagSearchRequest request = new RagController.RagSearchRequest();

        request.setQuery("搜索测试");
        request.setKnowledgeBaseId("kb1");
        request.setTopK(10);

        assertEquals("搜索测试", request.getQuery());
        assertEquals("kb1", request.getKnowledgeBaseId());
        assertEquals(10, request.getTopK());
    }

    @Test
    @DisplayName("RagSearchRequest DTO - 默认值")
    void testRagSearchRequest_DefaultValues() {
        RagController.RagSearchRequest request = new RagController.RagSearchRequest();

        assertEquals("default", request.getKnowledgeBaseId());
        assertEquals(5, request.getTopK());
    }

    // ==================== Result 类测试 ====================

    @Test
    @DisplayName("Result 类 - success 方法")
    void testResult_Success() {
        RagController.Result<String> result = RagController.Result.success("data");

        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals("data", result.getData());
    }

    @Test
    @DisplayName("Result 类 - success 带自定义消息")
    void testResult_SuccessWithMessage() {
        RagController.Result<String> result = RagController.Result.success("data", "自定义消息");

        assertEquals(200, result.getCode());
        assertEquals("自定义消息", result.getMessage());
        assertEquals("data", result.getData());
    }

    @Test
    @DisplayName("Result 类 - error 方法")
    void testResult_Error() {
        RagController.Result<String> result = RagController.Result.error("错误消息");

        assertEquals(500, result.getCode());
        assertEquals("错误消息", result.getMessage());
        assertNull(result.getData());
    }

    // ==================== UploadResult 类测试 ====================

    @Test
    @DisplayName("UploadResult 类 - Builder 模式")
    void testUploadResult_Builder() {
        RagController.UploadResult result = RagController.UploadResult.builder()
                .fileName("test.txt")
                .knowledgeBaseId("kb1")
                .chunkCount(5)
                .message("上传成功")
                .build();

        assertEquals("test.txt", result.getFileName());
        assertEquals("kb1", result.getKnowledgeBaseId());
        assertEquals(5, result.getChunkCount());
        assertEquals("上传成功", result.getMessage());
    }

    // ==================== BatchUploadResult 类测试 ====================

    @Test
    @DisplayName("BatchUploadResult 类 - Builder 模式")
    void testBatchUploadResult_Builder() {
        RagController.BatchUploadResult result = RagController.BatchUploadResult.builder()
                .fileCount(3)
                .successCount(2)
                .knowledgeBaseId("kb1")
                .totalChunks(15)
                .message("批量上传完成")
                .build();

        assertEquals(3, result.getFileCount());
        assertEquals(2, result.getSuccessCount());
        assertEquals("kb1", result.getKnowledgeBaseId());
        assertEquals(15, result.getTotalChunks());
        assertEquals("批量上传完成", result.getMessage());
    }
}
