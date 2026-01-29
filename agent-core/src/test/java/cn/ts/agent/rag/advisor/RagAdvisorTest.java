package cn.ts.agent.rag.advisor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RagAdvisor 单元测试
 * <p>
 * 测试 RAG Advisor 的各项功能，包括：
 * - 构造函数
 * - Advisor 接口实现
 * - before/after 方法
 * - enhanceMessages 方法
 * - 上下文构建
 * - 过滤器表达式
 * </p>
 *
 * @author tianshuo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RagAdvisor 单元测试")
class RagAdvisorTest {

    @Mock
    private VectorStore mockVectorStore;

    @Mock
    private AdvisorChain mockAdvisorChain;

    private RagAdvisorConfig defaultConfig;
    private RagAdvisor ragAdvisor;

    @BeforeEach
    void setUp() {
        defaultConfig = RagAdvisorConfig.defaultConfig();
        ragAdvisor = new RagAdvisor(mockVectorStore, defaultConfig);
    }

    // ==================== 构造函数测试 ====================

    @Test
    @DisplayName("使用 VectorStore 创建（默认配置）")
    void testConstructor_WithVectorStore() {
        RagAdvisor advisor = new RagAdvisor(mockVectorStore);

        assertNotNull(advisor);
        assertEquals("RagAdvisor", advisor.getName());
    }

    @Test
    @DisplayName("使用 VectorStore 和 Config 创建")
    void testConstructor_WithVectorStoreAndConfig() {
        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(10)
                .build();

        RagAdvisor advisor = new RagAdvisor(mockVectorStore, config);

        assertNotNull(advisor);
        assertEquals("RagAdvisor", advisor.getName());
    }

    @Test
    @DisplayName("使用 VectorStore、Config 和 SearchRequest 创建")
    void testConstructor_WithAllParameters() {
        SearchRequest searchRequest = SearchRequest.builder()
                .topK(3)
                .similarityThreshold(0.8)
                .build();

        RagAdvisor advisor = new RagAdvisor(mockVectorStore, defaultConfig, searchRequest);

        assertNotNull(advisor);
        assertEquals("RagAdvisor", advisor.getName());
    }

    @Test
    @DisplayName("使用 SearchRequest 创建（默认配置）")
    void testConstructor_WithSearchRequest() {
        SearchRequest searchRequest = SearchRequest.builder()
                .topK(5)
                .build();

        RagAdvisor advisor = new RagAdvisor(mockVectorStore, searchRequest);

        assertNotNull(advisor);
        assertEquals("RagAdvisor", advisor.getName());
    }

    // ==================== Advisor 接口测试 ====================

    @Test
    @DisplayName("getName() 返回正确的名称")
    void testGetName() {
        assertEquals("RagAdvisor", ragAdvisor.getName());
    }

    @Test
    @DisplayName("getOrder() 返回 0")
    void testGetOrder() {
        assertEquals(0, ragAdvisor.getOrder());
    }

    // ==================== before 方法测试 ====================

    @Test
    @DisplayName("before() 方法执行向量检索并增强请求")
    void testBefore_PerformsVectorRetrieval() {
        List<Document> documents = List.of(
                new Document("Test document 1"),
                new Document("Test document 2")
        );

        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(documents);

        UserMessage userMessage = new UserMessage("What is AI?");
        Prompt prompt = new Prompt(List.of(userMessage));
        Map<String, Object> context = new HashMap<>();

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .context(context)
                .build();

        ChatClientRequest result = ragAdvisor.before(request, mockAdvisorChain);

        assertNotNull(result);
        verify(mockVectorStore, times(1)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("before() 方法将检索到的文档添加到上下文")
    void testBefore_AddsDocumentsToContext() {
        List<Document> documents = List.of(
                new Document("Context document")
        );

        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(documents);

        UserMessage userMessage = new UserMessage("Test query");
        Prompt prompt = new Prompt(List.of(userMessage));
        Map<String, Object> context = new HashMap<>();

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .context(context)
                .build();

        ChatClientRequest result = ragAdvisor.before(request, mockAdvisorChain);

        assertTrue(result.context().containsKey("qa_retrieved_documents"));
        assertEquals(documents, result.context().get("qa_retrieved_documents"));
    }

    @Test
    @DisplayName("before() 方法增强消息列表")
    void testBefore_EnhancesMessages() {
        List<Document> documents = List.of(
                new Document("Relevant context")
        );

        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(documents);

        UserMessage userMessage = new UserMessage("Test query");
        Prompt prompt = new Prompt(List.of(userMessage));
        Map<String, Object> context = new HashMap<>();

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .context(context)
                .build();

        ChatClientRequest result = ragAdvisor.before(request, mockAdvisorChain);

        List<Message> enhancedMessages = result.prompt().getInstructions();

        // 应该包含系统消息（RAG 上下文）和原始用户消息
        assertTrue(enhancedMessages.size() >= 2);
        assertTrue(enhancedMessages.get(0) instanceof SystemMessage);
        assertTrue(enhancedMessages.get(0).getText().contains("Relevant context"));
    }

    @Test
    @DisplayName("before() 方法没有检索到文档时保留原始消息")
    void testBefore_NoDocumentsFound() {
        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        UserMessage userMessage = new UserMessage("Test query");
        Prompt prompt = new Prompt(List.of(userMessage));
        Map<String, Object> context = new HashMap<>();

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .context(context)
                .build();

        ChatClientRequest result = ragAdvisor.before(request, mockAdvisorChain);

        // 验证仍然调用了向量检索
        verify(mockVectorStore, times(1)).similaritySearch(any(SearchRequest.class));
    }

    // ==================== after 方法测试 ====================

    @Test
    @DisplayName("after() 方法保留检索到的文档元数据")
    void testAfter_PreservesDocumentMetadata() {
        List<Document> documents = List.of(
                new Document("Test document")
        );

        Map<String, Object> context = new HashMap<>();
        context.put("qa_retrieved_documents", documents);

        AssistantMessage assistantMessage = new AssistantMessage("Response");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));

        ChatClientResponse response = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(context)
                .build();

        ChatClientResponse result = ragAdvisor.after(response, mockAdvisorChain);

        assertNotNull(result);
        assertTrue(result.context().containsKey("qa_retrieved_documents"));
    }

    @Test
    @DisplayName("after() 方法保留原始响应内容")
    void testAfter_PreservesOriginalResponse() {
        Map<String, Object> context = new HashMap<>();

        AssistantMessage assistantMessage = new AssistantMessage("Original response");
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));

        ChatClientResponse response = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(context)
                .build();

        ChatClientResponse result = ragAdvisor.after(response, mockAdvisorChain);

        assertEquals("Original response", result.chatResponse().getResult().getOutput().getText());
    }

    // ==================== enhanceMessages 方法测试 ====================

    @Test
    @DisplayName("enhanceMessages() 添加系统消息包含上下文")
    void testEnhanceMessages_AddsSystemMessageWithContext() {
        List<Document> documents = List.of(
                new Document("Context document 1"),
                new Document("Context document 2")
        );

        List<Message> originalMessages = List.of(
                new UserMessage("User question")
        );

        List<Message> enhanced = ragAdvisor.enhanceMessages(
                originalMessages,
                "User question",
                documents
        );

        assertEquals(2, enhanced.size());
        assertTrue(enhanced.get(0) instanceof SystemMessage);
        assertTrue(enhanced.get(0).getText().contains("Context document"));
        assertEquals(enhanced.get(1), originalMessages.get(0));
    }

    @Test
    @DisplayName("enhanceMessages() 空查询时返回原始消息")
    void testEnhanceMessages_EmptyQuery() {
        List<Document> documents = List.of(new Document("Test"));

        List<Message> originalMessages = List.of(
                new UserMessage("Test")
        );

        List<Message> enhanced = ragAdvisor.enhanceMessages(
                originalMessages,
                "",
                documents
        );

        assertEquals(originalMessages, enhanced);
    }

    @Test
    @DisplayName("enhanceMessages() null 查询时返回原始消息")
    void testEnhanceMessages_NullQuery() {
        List<Document> documents = List.of(new Document("Test"));

        List<Message> originalMessages = List.of(
                new UserMessage("Test")
        );

        List<Message> enhanced = ragAdvisor.enhanceMessages(
                originalMessages,
                null,
                documents
        );

        assertEquals(originalMessages, enhanced);
    }

    @Test
    @DisplayName("enhanceMessages() 空文档列表时返回原始消息")
    void testEnhanceMessages_EmptyDocumentList() {
        List<Message> originalMessages = List.of(
                new UserMessage("Test question")
        );

        List<Message> enhanced = ragAdvisor.enhanceMessages(
                originalMessages,
                "Test question",
                List.of()
        );

        assertEquals(originalMessages, enhanced);
    }

    @Test
    @DisplayName("enhanceMessages() null 文档列表时返回原始消息")
    void testEnhanceMessages_NullDocumentList() {
        List<Message> originalMessages = List.of(
                new UserMessage("Test question")
        );

        List<Message> enhanced = ragAdvisor.enhanceMessages(
                originalMessages,
                "Test question",
                null
        );

        assertEquals(originalMessages, enhanced);
    }

    @Test
    @DisplayName("enhanceMessages() 上下文超过最大长度时截断")
    void testEnhanceMessages_TruncatesLongContext() {
        // 创建一个很长的文档
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longText.append("This is a very long document content. ");
        }

        List<Document> documents = List.of(
                new Document(longText.toString())
        );

        RagAdvisorConfig shortConfig = RagAdvisorConfig.builder()
                .maxContextLength(100)
                .build();

        RagAdvisor advisor = new RagAdvisor(mockVectorStore, shortConfig);

        List<Message> originalMessages = List.of(
                new UserMessage("Test question")
        );

        List<Message> enhanced = advisor.enhanceMessages(
                originalMessages,
                "Test question",
                documents
        );

        SystemMessage systemMessage = (SystemMessage) enhanced.get(0);
        // 系统提示词(约58) + 前缀(8) + 上下文(最多100) = 约166字符
        assertTrue(systemMessage.getText().length() <= 170); // 允许一些额外字符
    }

    // ==================== 上下文构建测试 ====================

    @Test
    @DisplayName("构建的上下文包含文档元数据")
    void testEnhanceMessages_IncludesDocumentMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("file_name", "test.txt");
        metadata.put("knowledge_base_id", "kb1");

        List<Document> documents = List.of(
                new Document("Content", metadata)
        );

        List<Message> originalMessages = List.of(
                new UserMessage("Question")
        );

        List<Message> enhanced = ragAdvisor.enhanceMessages(
                originalMessages,
                "Question",
                documents
        );

        SystemMessage systemMessage = (SystemMessage) enhanced.get(0);
        assertTrue(systemMessage.getText().contains("test.txt"));
    }

    @Test
    @DisplayName("构建的上下文格式化多个文档")
    void testEnhanceMessages_FormatsMultipleDocuments() {
        List<Document> documents = List.of(
                new Document("First document"),
                new Document("Second document"),
                new Document("Third document")
        );

        List<Message> originalMessages = List.of(
                new UserMessage("Question")
        );

        List<Message> enhanced = ragAdvisor.enhanceMessages(
                originalMessages,
                "Question",
                documents
        );

        SystemMessage systemMessage = (SystemMessage) enhanced.get(0);
        String content = systemMessage.getText();

        assertTrue(content.contains("[文档 1]"));
        assertTrue(content.contains("[文档 2]"));
        assertTrue(content.contains("[文档 3]"));
        assertTrue(content.contains("First document"));
        assertTrue(content.contains("Second document"));
        assertTrue(content.contains("Third document"));
    }

    // ==================== 过滤器表达式测试 ====================

    @Test
    @DisplayName("before() 方法使用上下文中的过滤器表达式")
    void testBefore_UsesFilterExpressionFromContext() {
        List<Document> documents = List.of(new Document("Test"));

        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(documents);

        UserMessage userMessage = new UserMessage("Test query");
        Prompt prompt = new Prompt(List.of(userMessage));

        Map<String, Object> context = new HashMap<>();
        context.put("qa_filter_expression", "knowledge_base_id == 'kb1'");

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .context(context)
                .build();

        ragAdvisor.before(request, mockAdvisorChain);

        verify(mockVectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("before() 方法上下文中无过滤器时使用默认值")
    void testBefore_NoFilterExpressionInContext() {
        List<Document> documents = List.of(new Document("Test"));

        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(documents);

        UserMessage userMessage = new UserMessage("Test query");
        Prompt prompt = new Prompt(List.of(userMessage));
        Map<String, Object> context = new HashMap<>();

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .context(context)
                .build();

        ragAdvisor.before(request, mockAdvisorChain);

        verify(mockVectorStore).similaritySearch(any(SearchRequest.class));
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("处理空消息列表")
    void testEnhanceMessages_EmptyMessageList() {
        List<Document> documents = List.of(new Document("Test"));

        List<Message> enhanced = ragAdvisor.enhanceMessages(
                List.of(),
                "Question",
                documents
        );

        // 应该仍然添加系统消息
        assertEquals(1, enhanced.size());
        assertTrue(enhanced.get(0) instanceof SystemMessage);
    }

    @Test
    @DisplayName("处理文档无元数据")
    void testEnhanceMessages_DocumentWithoutMetadata() {
        List<Document> documents = List.of(
                new Document("Content without metadata")
        );

        List<Message> originalMessages = List.of(
                new UserMessage("Question")
        );

        List<Message> enhanced = ragAdvisor.enhanceMessages(
                originalMessages,
                "Question",
                documents
        );

        SystemMessage systemMessage = (SystemMessage) enhanced.get(0);
        assertTrue(systemMessage.getText().contains("Content without metadata"));
    }

    @Test
    @DisplayName("配置不同的系统提示词")
    void testDifferentSystemPrompt() {
        String customPrompt = "Custom system prompt for testing";

        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .systemPrompt(customPrompt)
                .build();

        RagAdvisor advisor = new RagAdvisor(mockVectorStore, config);

        List<Document> documents = List.of(new Document("Test"));

        List<Message> originalMessages = List.of(
                new UserMessage("Question")
        );

        List<Message> enhanced = advisor.enhanceMessages(
                originalMessages,
                "Question",
                documents
        );

        SystemMessage systemMessage = (SystemMessage) enhanced.get(0);
        assertTrue(systemMessage.getText().contains(customPrompt));
    }
}
