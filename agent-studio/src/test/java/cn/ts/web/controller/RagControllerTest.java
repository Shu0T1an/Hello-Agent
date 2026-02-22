package cn.ts.web.controller;

import cn.ts.web.service.DocumentLoaderService;
import cn.ts.web.service.KnowledgeBaseService;
import cn.ts.web.service.RagQueryService;
import cn.ts.web.service.rag.RagTestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RagControllerTest {

    @Mock
    private DocumentLoaderService documentLoaderService;

    @Mock
    private RagQueryService ragQueryService;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RagController controller = new RagController(documentLoaderService, ragQueryService, knowledgeBaseService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void uploadDocumentSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        when(documentLoaderService.loadDocument(any(MultipartFile.class), eq("kb1"))).thenReturn(2);

        mockMvc.perform(multipart("/api/rag/documents/upload").file(file).param("knowledgeBaseId", "kb1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileName").value("a.txt"))
                .andExpect(jsonPath("$.data.knowledgeBaseId").value("kb1"))
                .andExpect(jsonPath("$.data.chunkCount").value(2));
    }

    @Test
    void uploadDocumentFailure() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        when(documentLoaderService.loadDocument(any(MultipartFile.class), anyString())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(multipart("/api/rag/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void batchUploadAllFailStillReturnsSuccessEnvelope() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "a.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        when(documentLoaderService.loadDocument(any(MultipartFile.class), anyString())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(multipart("/api/rag/documents/batch-upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileCount").value(1))
                .andExpect(jsonPath("$.data.successCount").value(0));
    }

    @Test
    void querySuccess() throws Exception {
        String requestBody = """
                {
                  "query": "what is ai",
                  "knowledgeBaseId": "kb1",
                  "topK": 5,
                  "similarityThreshold": 0.7
                }
                """;

        RagQueryService.RagQueryResult result = new RagQueryService.RagQueryResult(
                "answer",
                RagTestDataFactory.createDocumentListWithScores(),
                "what is ai"
        );
        when(ragQueryService.query(eq("what is ai"), eq("kb1"), any())).thenReturn(result);

        mockMvc.perform(post("/api/rag/query").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.response").value("answer"))
                .andExpect(jsonPath("$.data.query").value("what is ai"));
    }

    @Test
    void queryFailure() throws Exception {
        when(ragQueryService.query(anyString(), anyString(), any())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void queryStream() throws Exception {
        when(ragQueryService.queryStream(anyString(), anyString(), any())).thenReturn(Flux.just("a", "b"));

        mockMvc.perform(post("/api/rag/query/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"x\",\"knowledgeBaseId\":\"kb1\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    @Test
    void searchSuccess() throws Exception {
        when(ragQueryService.similaritySearch(eq("k"), eq("default"), eq(5))).thenReturn(List.of());

        mockMvc.perform(post("/api/rag/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"k\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(ragQueryService).similaritySearch(eq("k"), eq("default"), eq(5));
    }

    @Test
    void searchFailure() throws Exception {
        when(ragQueryService.similaritySearch(anyString(), anyString(), anyInt())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/rag/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"k\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void deleteKnowledgeBaseDocuments() throws Exception {
        doNothing().when(documentLoaderService).deleteKnowledgeBaseDocuments("kb1");

        mockMvc.perform(delete("/api/rag/knowledge-bases/kb1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void deleteKnowledgeBaseDocumentsFailure() throws Exception {
        doThrow(new RuntimeException("boom")).when(documentLoaderService).deleteKnowledgeBaseDocuments("kb1");

        mockMvc.perform(delete("/api/rag/knowledge-bases/kb1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void resultFactoryMethods() {
        RagController.Result<String> success = RagController.Result.success("x");
        RagController.Result<String> successWithMsg = RagController.Result.success("x", "ok");
        RagController.Result<String> error = RagController.Result.error("err");

        assertEquals(200, success.getCode());
        assertEquals("success", success.getMessage());
        assertEquals("x", success.getData());

        assertEquals(200, successWithMsg.getCode());
        assertEquals("ok", successWithMsg.getMessage());
        assertEquals("x", successWithMsg.getData());

        assertEquals(500, error.getCode());
        assertEquals("err", error.getMessage());
        assertNull(error.getData());
    }
}
