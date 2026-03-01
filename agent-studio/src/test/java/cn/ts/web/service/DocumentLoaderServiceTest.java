package cn.ts.web.service;

import cn.ts.web.rag.service.DocumentLoaderService;
import cn.ts.web.service.rag.FileSystemTestUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DocumentLoaderService 单元测试
 * <p>
 * 测试文档加载服务的各项功能，包括：
 * - PDF/TXT/Markdown 文件加载
 * - 文本分块验证
 * - 元数据添加验证
 * - 错误处理
 * - 文件保存和目录创建
 * - 知识库文档删除
 * - 边界条件
 * </p>
 *
 * @author tianshuo
 */
@DisplayName("DocumentLoaderService 单元测试")
class DocumentLoaderServiceTest {

    @TempDir
    Path tempDir;

    private VectorStore mockVectorStore;
    private TokenTextSplitter mockTextSplitter;
    private DocumentLoaderService documentLoaderService;
    private String uploadDirectory;

    @BeforeEach
    void setUp() throws Exception {
        // 创建 Mock 对象
        mockVectorStore = mock(VectorStore.class);
        mockTextSplitter = mock(TokenTextSplitter.class);

        // 设置临时目录作为上传目录
        uploadDirectory = tempDir.toString();

        // 配置默认的 Mock 行为
        when(mockTextSplitter.apply(any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 创建服务实例
        documentLoaderService = new DocumentLoaderService(
                mockVectorStore,
                mockTextSplitter,
                uploadDirectory
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        // 清理临时文件
        if (Files.exists(Paths.get(uploadDirectory))) {
            FileSystemTestUtils.deleteDirectory(uploadDirectory);
        }
    }

    // ==================== TXT 文件加载测试 ====================

    @Test
    @DisplayName("加载 TXT 文件成功")
    void testLoadDocument_TextFile_Success() throws Exception {
        String content = "This is a test document for text file loading.";
        String filePath = FileSystemTestUtils.createTextFile(
                uploadDirectory,
                "test.txt",
                content
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes()
        );

        List<Document> mockSplitDocuments = List.of(
                new Document("Split chunk 1"),
                new Document("Split chunk 2")
        );
        when(mockTextSplitter.apply(any(List.class))).thenReturn(mockSplitDocuments);

        int result = documentLoaderService.loadDocument(file, "test-kb");

        assertEquals(2, result);
        verify(mockVectorStore).add(mockSplitDocuments);

        // 验证文件被保存
        assertTrue(Files.exists(Paths.get(uploadDirectory, "test-kb")));
    }

    @Test
    @DisplayName("加载 Markdown 文件成功")
    void testLoadDocument_MarkdownFile_Success() throws Exception {
        String content = "# Test Document\n\nThis is a markdown file.";
        String filePath = FileSystemTestUtils.createMarkdownFile(
                uploadDirectory,
                "test.md",
                content
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.md",
                "text/markdown",
                content.getBytes()
        );

        List<Document> mockSplitDocuments = List.of(new Document("Split chunk"));
        when(mockTextSplitter.apply(any(List.class))).thenReturn(mockSplitDocuments);

        int result = documentLoaderService.loadDocument(file, "test-kb");

        assertEquals(1, result);
        verify(mockVectorStore).add(mockSplitDocuments);
    }

    // ==================== PDF 文件加载测试 ====================

    @Test
    @DisplayName("使用真实 PDFBox 加载 PDF 文件")
    void testLoadDocument_PdfFile_WithRealPDFBox() throws Exception {
        // 使用真实 PDFBox 创建测试 PDF
        String pdfContent = "This is a test PDF document content.";
        String filePath = FileSystemTestUtils.createPdfFile(
                uploadDirectory,
                "test.pdf",
                pdfContent
        );

        // 验证 PDF 文件创建成功
        assertTrue(Files.exists(Paths.get(filePath)));

        // 验证 PDF 可以被 PDFBox 读取
        try (PDDocument document = PDDocument.load(Paths.get(filePath).toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String extractedText = stripper.getText(document);
            assertTrue(extractedText.contains("test PDF"));
        }

        // 创建 MockMultipartFile（从实际文件读取）
        byte[] pdfBytes = Files.readAllBytes(Paths.get(filePath));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                pdfBytes
        );

        List<Document> mockSplitDocuments = List.of(
                new Document("Split chunk 1"),
                new Document("Split chunk 2")
        );
        when(mockTextSplitter.apply(any(List.class))).thenReturn(mockSplitDocuments);

        int result = documentLoaderService.loadDocument(file, "test-kb");

        assertEquals(2, result);
        verify(mockVectorStore).add(mockSplitDocuments);
    }

    @Test
    @DisplayName("加载多页 PDF 文件")
    void testLoadDocument_MultiPagePdf() throws Exception {
        String filePath = FileSystemTestUtils.createMultiPagePdfFile(
                uploadDirectory,
                "multipage.pdf",
                3,
                "Test content"
        );

        byte[] pdfBytes = Files.readAllBytes(Paths.get(filePath));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "multipage.pdf",
                "application/pdf",
                pdfBytes
        );

        List<Document> mockSplitDocuments = List.of(
                new Document("Page 1 content"),
                new Document("Page 2 content"),
                new Document("Page 3 content")
        );
        when(mockTextSplitter.apply(any(List.class))).thenReturn(mockSplitDocuments);

        int result = documentLoaderService.loadDocument(file, "test-kb");

        assertEquals(3, result);
        verify(mockVectorStore).add(mockSplitDocuments);
    }

    // ==================== 元数据添加验证测试 ====================

    @Test
    @DisplayName("文档包含正确的元数据")
    void testLoadDocument_AddsCorrectMetadata() throws Exception {
        String content = "Test document content";
        String filePath = FileSystemTestUtils.createTextFile(
                uploadDirectory,
                "test.txt",
                content
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes()
        );

        List<Document> mockSplitDocuments = List.of(new Document("Split chunk"));
        when(mockTextSplitter.apply(any(List.class))).thenReturn(mockSplitDocuments);

        documentLoaderService.loadDocument(file, "test-kb");

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockTextSplitter).apply(captor.capture());

        List<Document> storedDocuments = captor.getValue();
        Document storedDoc = storedDocuments.get(0);

        assertEquals("test.txt", storedDoc.getMetadata().get("file_name"));
        assertEquals("test-kb", storedDoc.getMetadata().get("knowledge_base_id"));
        assertNotNull(storedDoc.getMetadata().get("document_id"));
        assertNotNull(storedDoc.getMetadata().get("upload_time"));
        assertNotNull(storedDoc.getMetadata().get("file_path"));
    }

    // ==================== 错误处理测试 ====================

    @Test
    @DisplayName("不支持的文件类型抛出异常")
    void testLoadDocument_UnsupportedFileType_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[0]
        );

        assertThrows(RuntimeException.class, () -> {
            documentLoaderService.loadDocument(file, "test-kb");
        });
    }

    @Test
    @DisplayName("空文件名处理")
    void testLoadDocument_EmptyFileName_HandlesGracefully() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "",
                "text/plain",
                "content".getBytes()
        );

        // 空文件名应该抛出异常
        assertThrows(Exception.class, () -> {
            documentLoaderService.loadDocument(file, "test-kb");
        });
    }

    @Test
    @DisplayName("VectorStore 失败时抛出异常")
    void testLoadDocument_VectorStoreFailure_ThrowsException() throws Exception {
        String content = "Test content";
        FileSystemTestUtils.createTextFile(uploadDirectory, "test.txt", content);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes()
        );

        doThrow(new RuntimeException("Vector store error"))
                .when(mockVectorStore).add(any(List.class));

        assertThrows(RuntimeException.class, () -> {
            documentLoaderService.loadDocument(file, "test-kb");
        });
    }

    // ==================== 文本分块验证测试 ====================

    @Test
    @DisplayName("文本分块被正确调用")
    void testLoadDocument_TextSplitting_CalledCorrectly() throws Exception {
        String content = "Test content for splitting";
        FileSystemTestUtils.createTextFile(uploadDirectory, "test.txt", content);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes()
        );

        List<Document> mockSplitDocuments = List.of(
                new Document("Chunk 1"),
                new Document("Chunk 2"),
                new Document("Chunk 3")
        );
        when(mockTextSplitter.apply(any(List.class))).thenReturn(mockSplitDocuments);

        documentLoaderService.loadDocument(file, "test-kb");

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockTextSplitter).apply(captor.capture());

        // 验证分块前的文档列表
        List<Document> inputDocuments = captor.getValue();
        assertEquals(1, inputDocuments.size());
        assertTrue(inputDocuments.get(0).getText().contains(content));

        // 验证分块后的文档被存储
        verify(mockVectorStore).add(mockSplitDocuments);
    }

    // ==================== 文件保存和目录创建测试 ====================

    @Test
    @DisplayName("自动创建知识库目录")
    void testLoadDocument_CreatesKnowledgeBaseDirectory() throws Exception {
        String content = "Test content";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes()
        );

        List<Document> mockSplitDocuments = List.of(new Document("Chunk"));
        when(mockTextSplitter.apply(any(List.class))).thenReturn(mockSplitDocuments);

        documentLoaderService.loadDocument(file, "new-kb");

        Path kbDir = Paths.get(uploadDirectory, "new-kb");
        assertTrue(Files.exists(kbDir));
    }

    @Test
    @DisplayName("文件被正确保存")
    void testLoadDocument_FileSavedCorrectly() throws Exception {
        String content = "Test content";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes()
        );

        List<Document> mockSplitDocuments = List.of(new Document("Chunk"));
        when(mockTextSplitter.apply(any(List.class))).thenReturn(mockSplitDocuments);

        documentLoaderService.loadDocument(file, "test-kb");

        // 验证文件被保存到知识库目录
        Path kbDir = Paths.get(uploadDirectory, "test-kb");
        assertTrue(Files.exists(kbDir));
        assertTrue(Files.list(kbDir).count() > 0);
    }

    // ==================== 知识库文档删除测试 ====================

    @Test
    @DisplayName("删除知识库文档 - 从向量存储删除")
    void testDeleteKnowledgeBaseDocuments_RemovesFromVectorStore() throws Exception {
        // 创建测试文档
        String content = "Test content";
        FileSystemTestUtils.createTextFile(uploadDirectory, "test.txt", content);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes()
        );

        List<Document> mockSplitDocuments = List.of(new Document("Chunk"));
        when(mockTextSplitter.apply(any(List.class))).thenReturn(mockSplitDocuments);

        documentLoaderService.loadDocument(file, "test-kb");

        // 删除知识库文档
        documentLoaderService.deleteKnowledgeBaseDocuments("test-kb");

        // 验证删除操作被调用
        verify(mockVectorStore, atLeastOnce()).similaritySearch(any(String.class));
    }

    @Test
    @DisplayName("删除知识库文档 - 删除本地文件")
    void testDeleteKnowledgeBaseDocuments_RemovesLocalFiles() throws Exception {
        // 创建测试文档
        String content = "Test content";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes()
        );

        List<Document> mockSplitDocuments = List.of(new Document("Chunk"));
        when(mockTextSplitter.apply(any(List.class))).thenReturn(mockSplitDocuments);

        documentLoaderService.loadDocument(file, "test-kb");

        // 验证目录存在
        Path kbDir = Paths.get(uploadDirectory, "test-kb");
        assertTrue(Files.exists(kbDir));

        // 删除知识库文档
        documentLoaderService.deleteKnowledgeBaseDocuments("test-kb");

        // 验证目录被删除
        assertFalse(Files.exists(kbDir));
    }

    @Test
    @DisplayName("删除不存在的知识库不抛出异常")
    void testDeleteKnowledgeBaseDocuments_NonExistentKB_NoException() {
        assertDoesNotThrow(() -> {
            documentLoaderService.deleteKnowledgeBaseDocuments("non-existent-kb");
        });
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("处理大文件")
    void testLoadDocument_LargeFile() throws Exception {
        // 创建较大的文本内容（10KB）
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("This is line ").append(i).append(" of a large file.\n");
        }

        String filePath = FileSystemTestUtils.createTextFile(
                uploadDirectory,
                "large.txt",
                largeContent.toString()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.txt",
                "text/plain",
                largeContent.toString().getBytes()
        );

        List<Document> mockSplitDocuments = List.of(
                new Document("Chunk 1"),
                new Document("Chunk 2")
        );
        when(mockTextSplitter.apply(any(List.class))).thenReturn(mockSplitDocuments);

        int result = documentLoaderService.loadDocument(file, "test-kb");

        assertEquals(2, result);
        verify(mockVectorStore).add(mockSplitDocuments);
    }

    @Test
    @DisplayName("处理包含特殊字符的文件")
    void testLoadDocument_SpecialCharacters() throws Exception {
        String content = "Test with special chars: \n\t\"'`!@#$%^&*()";
        String filePath = FileSystemTestUtils.createTextFile(
                uploadDirectory,
                "special.txt",
                content
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "special.txt",
                "text/plain",
                content.getBytes()
        );

        List<Document> mockSplitDocuments = List.of(new Document("Chunk"));
        when(mockTextSplitter.apply(any(List.class))).thenReturn(mockSplitDocuments);

        int result = documentLoaderService.loadDocument(file, "test-kb");

        assertEquals(1, result);
        verify(mockVectorStore).add(mockSplitDocuments);
    }

    @Test
    @DisplayName("处理空文件")
    void testLoadDocument_EmptyFile() throws Exception {
        String filePath = FileSystemTestUtils.createTextFile(
                uploadDirectory,
                "empty.txt",
                ""
        );

        byte[] emptyContent = new byte[0];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                emptyContent
        );

        List<Document> mockSplitDocuments = List.of(new Document(""));
        when(mockTextSplitter.apply(any(List.class))).thenReturn(mockSplitDocuments);

        int result = documentLoaderService.loadDocument(file, "test-kb");

        assertEquals(1, result);
        verify(mockVectorStore).add(mockSplitDocuments);
    }

    @Test
    @DisplayName("处理多个知识库")
    void testLoadDocument_MultipleKnowledgeBases() throws Exception {
        String content = "Test content";
        List<Document> mockSplitDocuments = List.of(new Document("Chunk"));
        when(mockTextSplitter.apply(any(List.class))).thenReturn(mockSplitDocuments);

        // 上传到第一个知识库
        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes()
        );
        documentLoaderService.loadDocument(file1, "kb1");

        // 上传到第二个知识库
        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes()
        );
        documentLoaderService.loadDocument(file2, "kb2");

        // 验证两个目录都存在
        assertTrue(Files.exists(Paths.get(uploadDirectory, "kb1")));
        assertTrue(Files.exists(Paths.get(uploadDirectory, "kb2")));
    }

    // ==================== 文件扩展名测试 ====================

    @Test
    @DisplayName("正确识别文件扩展名")
    void testGetFileExtension_CorrectExtraction() throws Exception {
        String content = "Test content";

        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                content.getBytes()
        );

        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "document.txt",
                "text/plain",
                content.getBytes()
        );

        MockMultipartFile file3 = new MockMultipartFile(
                "file",
                "document.md",
                "text/markdown",
                content.getBytes()
        );

        List<Document> mockSplitDocuments = List.of(new Document("Chunk"));
        when(mockTextSplitter.apply(any(List.class))).thenReturn(mockSplitDocuments);

        // 创建 PDF 文件
        FileSystemTestUtils.createPdfFile(uploadDirectory, "document.pdf", content);
        byte[] pdfBytes = Files.readAllBytes(Paths.get(uploadDirectory, "document.pdf"));
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                pdfBytes
        );

        // 所有文件都应该能成功加载
        assertEquals(1, documentLoaderService.loadDocument(pdfFile, "test-kb"));
        assertEquals(1, documentLoaderService.loadDocument(file2, "test-kb"));
        assertEquals(1, documentLoaderService.loadDocument(file3, "test-kb"));
    }
}
