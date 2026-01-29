package cn.ts.web.service;

import cn.ts.agent.rag.advisor.RagAdvisor;
import cn.ts.agent.rag.advisor.RagAdvisorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static cn.ts.agent.rag.advisor.RagAdvisor.buildSearchRequest;

/**
 * RAG 查询服务
 * <p>
 * 功能：
 * 1. 使用 RagAdvisor 增强 ChatClient
 * 2. 支持流式和非流式查询
 * 3. 返回检索到的文档来源
 * </p>
 *
 * @author tianshuo
 */
@Service
public class RagQueryService {

    private static final Logger logger = LoggerFactory.getLogger(RagQueryService.class);

    private final ChatModel chatModel;
    private final VectorStore vectorStore;

    public RagQueryService(ChatModel chatModel, VectorStore vectorStore) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
    }

    /**
     * 执行 RAG 查询（非流式）
     *
     * @param query           用户查询
     * @param knowledgeBaseId 知识库 ID（可选，暂未使用）
     * @param config          RAG 配置
     * @return 查询结果（包含响应和来源文档）
     */
    public RagQueryResult query(String query, String knowledgeBaseId, RagAdvisorConfig config) {
        logger.info("执行 RAG 查询: {}, 知识库: {}", query, knowledgeBaseId);

        // 1. 先执行向量检索获取来源文档
        SearchRequest searchRequest = buildSearchRequest(config);
        List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);

        logger.info("检索到 {} 个相关文档", relevantDocs.size());

        // 2. 构建带 RagAdvisor 的 ChatClient
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new RagAdvisor(vectorStore, config))
                .build();

        // 3. 执行查询
        String response = chatClient.prompt()
                .user(query)
                .call()
                .content();

        // 4. 构建结果
        return new RagQueryResult(response, relevantDocs, query);
    }

    /**
     * 执行 RAG 流式查询
     *
     * @param query           用户查询
     * @param knowledgeBaseId 知识库 ID
     * @param config          RAG 配置
     * @return 流式响应
     */
    public Flux<String> queryStream(String query, String knowledgeBaseId, RagAdvisorConfig config) {
        logger.info("执行 RAG 流式查询: {}, 知识库: {}", query, knowledgeBaseId);

        SearchRequest searchRequest = buildSearchRequest(config);
        List<Document> relevantDocs1 = vectorStore.similaritySearch(query);
        // 1. 先执行向量检索
        List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);


        logger.info("流式检索到 {} 个相关文档", relevantDocs.size());

        // 2. 构建带 RagAdvisor 的 ChatClient
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new RagAdvisor(vectorStore, config))
                .build();

        // 3. 流式查询
        return chatClient.prompt()
                .user(query)
                .stream()
                .content();
    }

    /**
     * 仅执行向量检索（不调用 LLM）
     */
    public List<Document> similaritySearch(String query, String knowledgeBaseId, int topK) {
        List<Document> allDocs = vectorStore.similaritySearch(query);

        // 限制返回数量
        List<Document> limitedDocs = allDocs.stream()
                .limit(topK)
                .toList();

        if (knowledgeBaseId != null && !knowledgeBaseId.isEmpty()) {
            return limitedDocs.stream()
                    .filter(doc -> {
                        Object kbId = doc.getMetadata().get("knowledge_base_id");
                        return knowledgeBaseId.equals(kbId);
                    })
                    .toList();
        }

        return limitedDocs;
    }

    /**
     * RAG 查询结果
     */
    public record RagQueryResult(
            String response,
            List<Document> sourceDocuments,
            String query
    ) {
        /**
         * 获取来源文档摘要
         */
        public List<SourceSummary> getSourceSummaries() {
            if (sourceDocuments == null) {
                return List.of();
            }
            return sourceDocuments.stream()
                    .map(doc -> new SourceSummary(
                            (String) doc.getMetadata().get("file_name"),
                            (String) doc.getMetadata().get("knowledge_base_id"),
                            (Double) doc.getMetadata().get("distance")
                    ))
                    .toList();
        }
    }

    /**
     * 来源文档摘要
     */
    public record SourceSummary(
            String fileName,
            String knowledgeBaseId,
            Double score
    ) {
    }
}
