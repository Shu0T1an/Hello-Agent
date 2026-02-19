package cn.ts.web.controller;

import cn.ts.web.dto.TemporaryFileContent;
import cn.ts.web.service.TemporaryFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 临时文件上传控制器
 * <p>
 * 提供临时文件上传 API，文件仅用于当前会话，不会持久化到知识库
 * </p>
 *
 * @author tianshuo
 */
@RestController
@RequestMapping("/api/files/temporary")
@CrossOrigin(origins = "*")
public class TemporaryFileController {

    private static final Logger logger = LoggerFactory.getLogger(TemporaryFileController.class);

    private final TemporaryFileService temporaryFileService;

    public TemporaryFileController(TemporaryFileService temporaryFileService) {
        this.temporaryFileService = temporaryFileService;
    }

    /**
     * 上传临时文件（传统方式）
     * <p>
     * 支持的文件格式：PDF、TXT、MD、Markdown
     * 最大文件大小：10MB
     * </p>
     *
     * @param files    上传的文件列表
     * @param sessionId 会话 ID（可选，用于追踪）
     * @return 文件内容列表（包含分块信息）
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadTemporaryFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "sessionId", required = false) String sessionId) {

        logger.info("收到临时文件上传请求，文件数: {}, sessionId: {}", files.size(), sessionId);

        try {
            // 处理文件
            List<TemporaryFileContent> fileContents = temporaryFileService.extractAndChunkFiles(files);

            // 统计信息
            int totalChunks = fileContents.stream()
                    .mapToInt(fc -> fc.getChunks() != null ? fc.getChunks().size() : 0)
                    .sum();

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "文件上传成功");
            response.put("data", fileContents);
            response.put("summary", Map.of(
                    "fileCount", fileContents.size(),
                    "totalChunks", totalChunks,
                    "sessionId", sessionId != null ? sessionId : "unknown"
            ));

            logger.info("临时文件上传成功，文件数: {}, 总块数: {}", fileContents.size(), totalChunks);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("临时文件上传失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("code", 500);
            errorResponse.put("message", "文件上传失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 上传临时文件（响应式方式）
     * <p>
     * 支持的文件格式：PDF、TXT、MD、Markdown
     * 最大文件大小：10MB
     * </p>
     *
     * @param files    上传的文件列表（Flux）
     * @param sessionId 会话 ID（可选，用于追踪）
     * @return 文件内容列表（包含分块信息）
     */
    @PostMapping(value = "/upload-reactive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> uploadTemporaryFilesReactive(
            @RequestPart("files") Flux<FilePart> files,
            @RequestParam(value = "sessionId", required = false) String sessionId) {

        logger.info("收到响应式临时文件上传请求，sessionId: {}", sessionId);

        return temporaryFileService.extractAndChunkFilesReactive(files)
                .collectList()
                .map(fileContents -> {
                    // 统计信息
                    int totalChunks = fileContents.stream()
                            .mapToInt(fc -> fc.getChunks() != null ? fc.getChunks().size() : 0)
                            .sum();

                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 200);
                    response.put("message", "文件上传成功");
                    response.put("data", fileContents);
                    response.put("summary", Map.of(
                            "fileCount", fileContents.size(),
                            "totalChunks", totalChunks,
                            "sessionId", sessionId != null ? sessionId : "unknown"
                    ));

                    logger.info("响应式临时文件上传成功，文件数: {}, 总块数: {}", fileContents.size(), totalChunks);

                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    logger.error("响应式临时文件上传失败", e);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("code", 500);
                    errorResponse.put("message", "文件上传失败: " + e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
                });
    }

    /**
     * 获取支持的文件类型
     */
    @GetMapping("/supported-types")
    public ResponseEntity<Map<String, Object>> getSupportedTypes() {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", List.of("pdf", "txt", "md", "markdown"));
        return ResponseEntity.ok(response);
    }
}
