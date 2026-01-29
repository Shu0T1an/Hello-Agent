package cn.ts.agent.rag.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG Advisor - 基于 Spring AI Advisor 接口实现
 * <p>
 * 功能：
 * 1. 执行向量检索
 * 2. 将检索到的文档上下文注入到消息列表中
 * 3. 支持流式和非流式响应
 * 4. 可配置的检索参数（topK、相似度阈值等）
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 方式1：直接使用 enhanceMessages 方法
 * List<Message> enhancedMessages = ragAdvisor.enhanceMessages(originalMessages, userQuery);
 * ChatClient chatClient = ChatClient.builder(chatModel).build();
 * String response = chatClient.prompt().messages(enhancedMessages).call().content();
 *
 * // 方式2：作为 Advisor 添加到 ChatClient
 * ChatClient chatClient = ChatClient.builder(chatModel)
 *     .defaultAdvisors(new RagAdvisor(vectorStore))
 *     .build();
 * }</pre>
 * </p>
 *
 * @author tianshuo
 */
public class RagAdvisor implements BaseAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(RagAdvisor.class);

    private final VectorStore vectorStore;
    private final RagAdvisorConfig config;
    private final String knowledgeBaseId;

    private final SearchRequest searchRequest;

    /**
     * 使用默认配置创建 RagAdvisor（便捷构造函数）
     *
     * @param vectorStore 向量存储
     */
    public RagAdvisor(VectorStore vectorStore) {
        this(vectorStore, RagAdvisorConfig.defaultConfig());
    }

    /**
     * 使用自定义配置创建 RagAdvisor（便捷构造函数）
     *
     * @param vectorStore 向量存储
     * @param config      RAG 配置
     */
    public RagAdvisor(VectorStore vectorStore, RagAdvisorConfig config) {
        this(vectorStore, config, buildSearchRequest(config));
    }

    /**
     * 使用默认配置创建 RagAdvisor（完整构造函数）
     *
     * @param vectorStore   向量存储
     * @param searchRequest 搜索请求配置
     */
    public RagAdvisor(VectorStore vectorStore, SearchRequest searchRequest) {
        this(vectorStore, RagAdvisorConfig.defaultConfig(), searchRequest);
    }

    /**
     * 使用自定义配置创建 RagAdvisor（完整构造函数）
     *
     * @param vectorStore   向量存储
     * @param config        RAG 配置
     * @param searchRequest 搜索请求配置
     */
    public RagAdvisor(VectorStore vectorStore, RagAdvisorConfig config, SearchRequest searchRequest) {
        this.vectorStore = vectorStore;
        this.config = config;
        this.searchRequest = searchRequest;
        this.knowledgeBaseId = null;
    }

    /**
     * 使用自定义配置和知识库ID创建 RagAdvisor（支持动态知识库）
     *
     * @param vectorStore     向量存储
     * @param config          RAG 配置
     * @param searchRequest   搜索请求配置
     * @param knowledgeBaseId 知识库ID
     */
    public RagAdvisor(VectorStore vectorStore, RagAdvisorConfig config, SearchRequest searchRequest, String knowledgeBaseId) {
        this.vectorStore = vectorStore;
        this.config = config;
        this.searchRequest = searchRequest;
        this.knowledgeBaseId = knowledgeBaseId;
    }

    /**
     * 使用默认配置和知识库ID创建 RagAdvisor（便捷构造函数）
     *
     * @param vectorStore     向量存储
     * @param knowledgeBaseId 知识库ID
     */
    public RagAdvisor(VectorStore vectorStore, String knowledgeBaseId) {
        this(vectorStore, RagAdvisorConfig.defaultConfig(), buildSearchRequest(RagAdvisorConfig.defaultConfig()), knowledgeBaseId);
    }

    /**
     * 使用自定义配置和知识库ID创建 RagAdvisor
     *
     * @param vectorStore     向量存储
     * @param config          RAG 配置
     * @param knowledgeBaseId 知识库ID
     */
    public RagAdvisor(VectorStore vectorStore, RagAdvisorConfig config, String knowledgeBaseId) {
        this(vectorStore, config, buildSearchRequest(config), knowledgeBaseId);
    }

    /**
     * 从 RagAdvisorConfig 构建 SearchRequest
     */
    public static SearchRequest buildSearchRequest(RagAdvisorConfig config) {
        return SearchRequest.builder()
                .topK(config.getTopK())
                .similarityThreshold(config.getSimilarityThreshold())
                .build();
    }

    /**
     * 获取 Advisor 名称
     */
    @Override
    public String getName() {
        return "RagAdvisor";
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        String userText = chatClientRequest.prompt().getUserMessage().getText();

        // 执行向量检索，优先使用knowledgeBaseId
        Filter.Expression filterExpression = buildFilterExpression(chatClientRequest.context());
        SearchRequest searchRequestToUse = SearchRequest.from(this.searchRequest)
                .query(userText)
                .filterExpression(filterExpression)
                .build();

        logger.debug("执行向量检索，knowledgeBaseId: {}, filter: {}",
                knowledgeBaseId, filterExpression);

        List<Document> documents = this.vectorStore.similaritySearch(searchRequestToUse);

        // 构建新的上下文（包含检索到的文档）
        HashMap<String, Object> newContext = new HashMap<>(chatClientRequest.context());
        newContext.put("qa_retrieved_documents", documents);

        // 获取原始消息列表
        List<Message> originalMessages = chatClientRequest.prompt().getInstructions();

        // 使用检索到的文档增强消息
        List<Message> enhancedMessages = enhanceMessages(originalMessages, userText, documents);

        return ChatClientRequest.builder()
                .prompt(Prompt.builder().messages(enhancedMessages).build())
                .context(newContext)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        ChatResponse.Builder chatResponseBuilder = ChatResponse.builder().from(chatClientResponse.chatResponse());
        chatResponseBuilder.metadata("qa_retrieved_documents", chatClientResponse.context().get("qa_retrieved_documents"));
        ChatResponse chatResponse = chatResponseBuilder.build();

        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(chatClientResponse.context())
                .build();
    }

    protected Filter.Expression doGetFilterExpression(Map<String, Object> context) {
        return context.containsKey("qa_filter_expression") && StringUtils.hasText(context.get("qa_filter_expression").toString()) ? (new FilterExpressionTextParser()).parse(context.get("qa_filter_expression").toString()) : this.searchRequest.getFilterExpression();
    }

    /**
     * 构建过滤表达式，优先级：knowledgeBaseId > context中的qa_filter_expression > 默认配置
     */
    private Filter.Expression buildFilterExpression(Map<String, Object> context) {
        // 1. 优先使用实例的knowledgeBaseId
        if (knowledgeBaseId != null && !knowledgeBaseId.isEmpty()) {
            String filterExpr = "knowledge_base_id == '" + knowledgeBaseId + "'";
            logger.debug("使用knowledgeBaseId过滤: {}", filterExpr);
            return new FilterExpressionTextParser().parse(filterExpr);
        }

        // 2. 使用context中的qa_filter_expression
        if (context.containsKey("qa_filter_expression") && StringUtils.hasText(context.get("qa_filter_expression").toString())) {
            String filterExpr = context.get("qa_filter_expression").toString();
            logger.debug("使用context中的qa_filter_expression过滤: {}", filterExpr);
            return new FilterExpressionTextParser().parse(filterExpr);
        }

        // 3. 使用searchRequest中的默认过滤表达式
        return this.searchRequest.getFilterExpression();
    }
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * 增强消息列表，添加检索到的文档上下文（使用已检索的文档）
     * <p>
     * 这是推荐的方法，避免重复检索。当已有检索结果时优先使用此方法。
     *
     * @param messages  原始消息列表
     * @param userQuery 用户查询
     * @param documents 已检索到的文档列表
     * @return 增强后的消息列表
     */
    public List<Message> enhanceMessages(List<Message> messages, String userQuery, List<Document> documents) {
        if (userQuery == null || userQuery.isEmpty()) {
            logger.debug("没有用户查询，跳过 RAG 检索");
            return messages;
        }
        // 使用已检索的文档
        if (documents == null || documents.isEmpty()) {
            logger.debug("没有检索到相关文档，使用原始消息");
            return messages;
        }
        logger.debug("使用已检索的 {} 个文档进行增强", documents.size());
        return enhanceMessagesWithContext(messages, documents);
    }
    /**
     * 使用检索到的文档上下文增强消息
     */
    private List<Message> enhanceMessagesWithContext(
            List<Message> originalMessages,
            List<Document> relevantDocs) {

        if (relevantDocs == null || relevantDocs.isEmpty()) {
            logger.debug("没有检索到相关文档，使用原始消息");
            return originalMessages;
        }

        // 构建上下文字符串
        String context = buildContextString(relevantDocs);

        // 创建新的消息列表
        List<Message> enhancedMessages = new ArrayList<>();

        // 添加系统消息（包含 RAG 系统提示 + 上下文）
        String ragSystemPrompt = config.getSystemPrompt() + "\n\n参考上下文：\n" + context;
        enhancedMessages.add(new SystemMessage(ragSystemPrompt));
        logger.debug("已添加 RAG 系统消息，上下文长度: {}", context.length());

        // 添加原始消息
        enhancedMessages.addAll(originalMessages);

        return enhancedMessages;
    }

    /**
     * 构建上下文字符串
     */
    private String buildContextString(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "（未找到相关文档）";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            sb.append(String.format("[文档 %d]", i + 1));

            // 添加元数据（如果有）
            if (doc.getMetadata() != null && !doc.getMetadata().isEmpty()) {
                String fileName = (String) doc.getMetadata().get("file_name");
                if (fileName != null) {
                    sb.append(" 来源: ").append(fileName);
                }
            }

            sb.append("\n");
            sb.append(doc.getText());

            // 检查上下文长度限制（为"..."预留3个字符）
            if (sb.length() >= config.getMaxContextLength() - 3) {
                sb.setLength(config.getMaxContextLength() - 3);
                sb.append("...");
                break;
            }

            sb.append("\n\n");
        }
        return sb.toString();
    }
}
