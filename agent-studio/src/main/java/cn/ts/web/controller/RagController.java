package cn.ts.web.controller;

import cn.ts.agent.rag.advisor.RagAdvisorConfig;
import cn.ts.web.entity.KnowledgeBaseEntity;
import cn.ts.web.service.DocumentLoaderService;
import cn.ts.web.service.KnowledgeBaseService;
import cn.ts.web.service.RagQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RAG 功能控制器
 * <p>
 * 提供 RAG 相关的 API 端点：
 * - 文档上传
 * - RAG 查询（支持流式）
 * - 知识库管理
 * </p>
 *
 * @author tianshuo
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "*")
public class RagController {

    private final DocumentLoaderService documentLoaderService;
    private final RagQueryService ragQueryService;
    private final KnowledgeBaseService knowledgeBaseService;

    public RagController(DocumentLoaderService documentLoaderService,
                        RagQueryService ragQueryService,
                        KnowledgeBaseService knowledgeBaseService) {
        this.documentLoaderService = documentLoaderService;
        this.ragQueryService = ragQueryService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 上传文档到知识库
     *
     * @param file            上传的文件
     * @param knowledgeBaseId 知识库 ID（默认为 "default"）
     * @return 上传结果
     */
    @PostMapping("/documents/upload")
    public Result<UploadResult> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "knowledgeBaseId", defaultValue = "default") String knowledgeBaseId) {

        try {
            int chunkCount = documentLoaderService.loadDocument(file, knowledgeBaseId);

            return Result.success(UploadResult.builder()
                    .fileName(file.getOriginalFilename())
                    .knowledgeBaseId(knowledgeBaseId)
                    .chunkCount(chunkCount)
                    .message("文档上传成功")
                    .build());
        } catch (Exception e) {
            log.error("文档上传失败: {}", file.getOriginalFilename(), e);
            return Result.error("文档上传失败: " + e.getMessage());
        }
    }

    /**
     * 批量上传文档
     *
     * @param files           上传的文件列表
     * @param knowledgeBaseId 知识库 ID
     * @return 批量上传结果
     */
    @PostMapping("/documents/batch-upload")
    public Result<BatchUploadResult> batchUploadDocuments(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "knowledgeBaseId", defaultValue = "default") String knowledgeBaseId) {

        try {
            int totalChunks = 0;
            int successCount = 0;

            for (MultipartFile file : files) {
                try {
                    totalChunks += documentLoaderService.loadDocument(file, knowledgeBaseId);
                    successCount++;
                } catch (Exception e) {
                    log.error("批量上传中处理文件失败: {}, 继续处理其他文件",
                            file.getOriginalFilename(), e);
                }
            }

            return Result.success(BatchUploadResult.builder()
                    .fileCount(files.size())
                    .successCount(successCount)
                    .knowledgeBaseId(knowledgeBaseId)
                    .totalChunks(totalChunks)
                    .message(String.format("批量上传完成，成功 %d/%d", successCount, files.size()))
                    .build());
        } catch (Exception e) {
            log.error("批量上传失败", e);
            return Result.error("批量上传失败: " + e.getMessage());
        }
    }

    /**
     * RAG 查询（非流式）
     *
     * @param request 查询请求
     * @return 查询结果
     */
    @PostMapping("/query")
    public Result<RagQueryService.RagQueryResult> query(@RequestBody RagQueryRequest request) {

        try {
            RagAdvisorConfig config = buildRagConfig(request);
            RagQueryService.RagQueryResult result = ragQueryService.query(
                    request.getQuery(),
                    request.getKnowledgeBaseId(),
                    config
            );

            return Result.success(result);
        } catch (Exception e) {
            log.error("RAG 查询失败: {}", request.getQuery(), e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * RAG 流式查询（GET 方式，供 EventSource 使用）
     *
     * @param query                  查询内容
     * @param knowledgeBaseId        知识库 ID
     * @param topK                   返回文档数
     * @param similarityThreshold    相似度阈值
     * @return SSE 流式响应
     */
    @GetMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> queryStreamGet(
            @RequestParam String query,
            @RequestParam(defaultValue = "default") String knowledgeBaseId,
            @RequestParam(defaultValue = "5") Integer topK,
            @RequestParam(defaultValue = "0.7") Double similarityThreshold) {

        RagAdvisorConfig config = RagAdvisorConfig.builder()
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();

        return ragQueryService.queryStream(query, knowledgeBaseId, config)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build())
                .doOnComplete(() -> log.info("RAG 流式查询完成: {}", query))
                .doOnError(e -> log.error("RAG 流式查询错误: {}", query, e));
    }

    /**
     * RAG 流式查询（POST 方式）
     *
     * @param request 查询请求
     * @return SSE 流式响应
     */
    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> queryStreamPost(@RequestBody RagQueryRequest request) {

        RagAdvisorConfig config = buildRagConfig(request);

        return ragQueryService.queryStream(
                request.getQuery(),
                request.getKnowledgeBaseId(),
                config
        )
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build())
                .doOnComplete(() -> log.info("RAG 流式查询完成: {}", request.getQuery()))
                .doOnError(e -> log.error("RAG 流式查询错误: {}", request.getQuery(), e));
    }

    /**
     * 仅检索相似文档（不调用 LLM）
     *
     * @param request 搜索请求
     * @return 相似文档列表
     */
    @PostMapping("/search")
    public Result<List<org.springframework.ai.document.Document>> searchDocuments(@RequestBody RagSearchRequest request) {

        try {
            List<org.springframework.ai.document.Document> docs = ragQueryService.similaritySearch(
                    request.getQuery(),
                    request.getKnowledgeBaseId(),
                    request.getTopK()
            );

            return Result.success(docs);
        } catch (Exception e) {
            log.error("文档搜索失败: {}", request.getQuery(), e);
            return Result.error("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 删除知识库的所有文档
     *
     * @param kbId 知识库 ID
     * @return 删除结果
     */
    @DeleteMapping("/knowledge-bases/{kbId}/documents")
    public Result<Void> deleteKnowledgeBaseDocuments(@PathVariable String kbId) {
        try {
            documentLoaderService.deleteKnowledgeBaseDocuments(kbId);
            return Result.success(null, "知识库文档已删除");
        } catch (Exception e) {
            log.error("删除知识库文档失败: {}", kbId, e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    // ========== 知识库管理端点 ==========

    /**
     * 获取所有知识库
     */
    @GetMapping("/knowledge-bases")
    public Result<List<KnowledgeBaseVO>> getKnowledgeBases() {
        try {
            List<KnowledgeBaseEntity> entities = knowledgeBaseService.getAllKnowledgeBases();
            List<KnowledgeBaseVO> vos = entities.stream()
                    .map(this::toVO)
                    .toList();
            return Result.success(vos);
        } catch (Exception e) {
            log.error("获取知识库列表失败", e);
            return Result.error("获取知识库列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定知识库
     */
    @GetMapping("/knowledge-bases/{kbId}")
    public Result<KnowledgeBaseVO> getKnowledgeBase(@PathVariable String kbId) {
        try {
            Optional<KnowledgeBaseEntity> entity = knowledgeBaseService.getKnowledgeBaseById(kbId);
            if (entity.isEmpty()) {
                return Result.error("知识库不存在: " + kbId);
            }
            return Result.success(toVO(entity.get()));
        } catch (Exception e) {
            log.error("获取知识库失败: {}", kbId, e);
            return Result.error("获取知识库失败: " + e.getMessage());
        }
    }

    /**
     * 创建知识库
     */
    @PostMapping("/knowledge-bases")
    public Result<KnowledgeBaseVO> createKnowledgeBase(@RequestBody CreateKnowledgeBaseRequest request) {
        try {
            // 检查 kbId 是否已存在
            if (knowledgeBaseService.existsByKbId(request.getKbId())) {
                return Result.error("知识库ID已存在: " + request.getKbId());
            }

            KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
            entity.setKbId(request.getKbId());
            entity.setKbName(request.getKbName());
            entity.setDescription(request.getDescription());
            entity.setEmbeddingModel(request.getEmbeddingModel());
            entity.setDimension(request.getDimension());
            entity.setStatus(request.getStatus());
            entity.setCreatedBy(request.getCreatedBy());

            KnowledgeBaseEntity created = knowledgeBaseService.createKnowledgeBase(entity);
            return Result.success(toVO(created), "知识库创建成功");
        } catch (Exception e) {
            log.error("创建知识库失败: {}", request.getKbId(), e);
            return Result.error("创建知识库失败: " + e.getMessage());
        }
    }

    /**
     * 更新知识库
     */
    @PutMapping("/knowledge-bases/{kbId}")
    public Result<KnowledgeBaseVO> updateKnowledgeBase(
            @PathVariable String kbId,
            @RequestBody UpdateKnowledgeBaseRequest request) {
        try {
            if (!knowledgeBaseService.existsByKbId(kbId)) {
                return Result.error("知识库不存在: " + kbId);
            }

            KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
            entity.setKbId(kbId);
            entity.setKbName(request.getKbName());
            entity.setDescription(request.getDescription());
            entity.setEmbeddingModel(request.getEmbeddingModel());
            entity.setDimension(request.getDimension());
            entity.setStatus(request.getStatus());

            Optional<KnowledgeBaseEntity> updated = knowledgeBaseService.updateKnowledgeBase(entity);
            if (updated.isEmpty()) {
                return Result.error("更新知识库失败");
            }
            return Result.success(toVO(updated.get()), "知识库更新成功");
        } catch (Exception e) {
            log.error("更新知识库失败: {}", kbId, e);
            return Result.error("更新知识库失败: " + e.getMessage());
        }
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/knowledge-bases/{kbId}")
    public Result<Void> deleteKnowledgeBase(@PathVariable String kbId) {
        try {
            // 先删除知识库关联的文档
            documentLoaderService.deleteKnowledgeBaseDocuments(kbId);

            // 再删除知识库
            boolean deleted = knowledgeBaseService.deleteKnowledgeBase(kbId);
            if (!deleted) {
                return Result.error("知识库不存在: " + kbId);
            }
            return Result.success(null, "知识库已删除");
        } catch (Exception e) {
            log.error("删除知识库失败: {}", kbId, e);
            return Result.error("删除知识库失败: " + e.getMessage());
        }
    }

    /**
     * 转换为 VO
     */
    private KnowledgeBaseVO toVO(KnowledgeBaseEntity entity) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(entity.getId());
        vo.setKbId(entity.getKbId());
        vo.setKbName(entity.getKbName());
        vo.setDescription(entity.getDescription());
        vo.setEmbeddingModel(entity.getEmbeddingModel());
        vo.setDimension(entity.getDimension());
        vo.setStatus(entity.getStatus());
        vo.setDocumentCount(entity.getDocumentCount());
        vo.setTotalChunks(entity.getTotalChunks());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 构建配置
     */
    private RagAdvisorConfig buildRagConfig(RagQueryRequest request) {
        RagAdvisorConfig.RagAdvisorConfigBuilder builder = RagAdvisorConfig.builder();

        if (request.getTopK() != null) {
            builder.topK(request.getTopK());
        }
        if (request.getSimilarityThreshold() != null) {
            builder.similarityThreshold(request.getSimilarityThreshold());
        }

        return builder.build();
    }

    // ========== DTO 类 ==========

    /**
     * RAG 查询请求
     */
    public static class RagQueryRequest {
        private String query;
        private String knowledgeBaseId = "default";
        private Integer topK = 5;
        private Double similarityThreshold = 0.7;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public String getKnowledgeBaseId() {
            return knowledgeBaseId;
        }

        public void setKnowledgeBaseId(String knowledgeBaseId) {
            this.knowledgeBaseId = knowledgeBaseId;
        }

        public Integer getTopK() {
            return topK;
        }

        public void setTopK(Integer topK) {
            this.topK = topK;
        }

        public Double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(Double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }
    }

    /**
     * RAG 搜索请求
     */
    public static class RagSearchRequest {
        private String query;
        private String knowledgeBaseId = "default";
        private int topK = 5;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public String getKnowledgeBaseId() {
            return knowledgeBaseId;
        }

        public void setKnowledgeBaseId(String knowledgeBaseId) {
            this.knowledgeBaseId = knowledgeBaseId;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }
    }

    /**
     * 上传结果
     */
    @lombok.Builder
    @lombok.Data
    public static class UploadResult {
        private String fileName;
        private String knowledgeBaseId;
        private int chunkCount;
        private String message;
    }

    /**
     * 批量上传结果
     */
    @lombok.Builder
    @lombok.Data
    public static class BatchUploadResult {
        private int fileCount;
        private int successCount;
        private String knowledgeBaseId;
        private int totalChunks;
        private String message;
    }

    /**
     * 通用响应结果
     */
    @lombok.Builder
    @lombok.Data
    public static class Result<T> {
        private int code;
        private String message;
        private T data;

        public static <T> Result<T> success(T data) {
            return Result.<T>builder()
                    .code(200)
                    .message("success")
                    .data(data)
                    .build();
        }

        public static <T> Result<T> success(T data, String message) {
            return Result.<T>builder()
                    .code(200)
                    .message(message)
                    .data(data)
                    .build();
        }

        public static <T> Result<T> error(String message) {
            return Result.<T>builder()
                    .code(500)
                    .message(message)
                    .build();
        }
    }

    // ========== 知识库 DTO 类 ==========

    /**
     * 知识库视图对象
     */
    @lombok.Data
    public static class KnowledgeBaseVO {
        private Long id;
        private String kbId;
        private String kbName;
        private String description;
        private String embeddingModel;
        private Integer dimension;
        private String status;
        private Integer documentCount;
        private Integer totalChunks;
        private String createdBy;
        private Instant createdAt;
        private Instant updatedAt;
    }

    /**
     * 创建知识库请求
     */
    @lombok.Data
    public static class CreateKnowledgeBaseRequest {
        private String kbId;
        private String kbName;
        private String description;
        private String embeddingModel;
        private Integer dimension;
        private String status;
        private String createdBy;
    }

    /**
     * 更新知识库请求
     */
    @lombok.Data
    public static class UpdateKnowledgeBaseRequest {
        private String kbName;
        private String description;
        private String embeddingModel;
        private Integer dimension;
        private String status;
    }
}
