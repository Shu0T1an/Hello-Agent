package cn.ts.web.infra.tempfile.service;

import cn.ts.web.rag.dto.DocumentChunk;
import cn.ts.web.infra.tempfile.dto.TemporaryFileContent;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 临时文件处理服务
 * <p>
 * 功能：
 * 1. 接收 MultipartFile 列表
 * 2. 复用 DocumentLoaderService 的文档解析逻辑
 * 3. 将文件内容按段落分块，每个块分配唯一 ID
 * 4. 返回带块的文件内容列表
 * </p>
 *
 * @author tianshuo
 */
@Service
public class TemporaryFileService {

    private static final Logger logger = LoggerFactory.getLogger(TemporaryFileService.class);

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[([a-zA-Z]+\\d*):p(\\d+)\\]");

    @Value("${temporary.file.max-size:10485760}")
    private long maxFileSize;

    @Value("${temporary.file.allowed-types:pdf,txt,md,markdown}")
    private String allowedTypes;

    /**
     * 提取并分块文件
     *
     * @param files 上传的文件列表
     * @return 文件内容列表（包含分块信息）
     */
    public List<TemporaryFileContent> extractAndChunkFiles(List<MultipartFile> files) {
        List<TemporaryFileContent> result = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);

            try {
                validateFile(file);
                String fileId = "doc" + i;
                String fileName = file.getOriginalFilename();
                long fileSize = file.getSize();

                // 提取全文
                String fullText = extractText(file);

                // 按段落分块
                List<DocumentChunk> chunks = chunkByParagraph(fullText, fileId, fileName);

                TemporaryFileContent content = TemporaryFileContent.builder()
                        .fileId(fileId)
                        .fileName(fileName)
                        .size(fileSize)
                        .chunks(chunks)
                        .build();

                result.add(content);

                logger.info("文件处理完成: {}, 分块数: {}", fileName, chunks.size());

            } catch (Exception e) {
                logger.error("文件处理失败: {}", file.getOriginalFilename(), e);
                throw new RuntimeException("文件处理失败: " + e.getMessage(), e);
            }
        }

        return result;
    }

    /**
     * 提取并分块文件（响应式版本）
     *
     * @param files 上传的文件列表（Flux）
     * @return 文件内容列表（包含分块信息）
     */
    public Flux<TemporaryFileContent> extractAndChunkFilesReactive(Flux<FilePart> files) {
        return files.index()
                .flatMap(tuple -> {
                    long index = tuple.getT1();
                    FilePart filePart = tuple.getT2();

                    return processFilePart(filePart, (int) index);
                });
    }

    /**
     * 处理单个 FilePart
     */
    private Mono<TemporaryFileContent> processFilePart(FilePart filePart, int index) {
        String fileId = "doc" + index;
        String fileName = filePart.filename();

        return filePart.content()
                .collectList()
                .map(dataBuffers -> {
                    // 计算总大小
                    int totalSize = dataBuffers.stream()
                            .mapToInt(DataBuffer::readableByteCount)
                            .sum();

                    byte[] bytes = new byte[totalSize];
                    int offset = 0;

                    for (DataBuffer buffer : dataBuffers) {
                        int length = buffer.readableByteCount();
                        buffer.read(bytes, offset, length);
                        offset += length;
                        DataBufferUtils.release(buffer);
                    }

                    return bytes;
                })
                .flatMap(bytes -> {
                    try {
                        // 验证文件
                        if (bytes.length > maxFileSize) {
                            return Mono.error(new RuntimeException("文件过大: " + fileName));
                        }

                        String fileExtension = getFileExtension(fileName);
                        if (!isAllowedType(fileExtension)) {
                            return Mono.error(new RuntimeException("不支持的文件类型: " + fileExtension));
                        }

                        // 提取文本
                        String fullText = extractTextFromBytes(bytes, fileExtension, fileName);

                        // 按段落分块
                        List<DocumentChunk> chunks = chunkByParagraph(fullText, fileId, fileName);

                        TemporaryFileContent content = TemporaryFileContent.builder()
                                .fileId(fileId)
                                .fileName(fileName)
                                .size((long) bytes.length)
                                .chunks(chunks)
                                .build();

                        logger.info("文件处理完成: {}, 分块数: {}", fileName, chunks.size());

                        return Mono.just(content);
                    } catch (Exception e) {
                        logger.error("文件处理失败: {}", fileName, e);
                        return Mono.error(new RuntimeException("文件处理失败: " + e.getMessage(), e));
                    }
                });
    }

    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }

        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("文件过大，最大支持 " + (maxFileSize / 1024 / 1024) + "MB");
        }

        String fileName = file.getOriginalFilename();
        String fileExtension = getFileExtension(fileName);

        if (!isAllowedType(fileExtension)) {
            throw new IllegalArgumentException("不支持的文件类型: " + fileExtension);
        }
    }

    /**
     * 检查文件类型是否允许
     */
    private boolean isAllowedType(String extension) {
        String[] types = allowedTypes.split(",");
        for (String type : types) {
            if (type.trim().equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    /**
     * 提取文本内容
     */
    private String extractText(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        String fileExtension = getFileExtension(fileName);

        byte[] bytes = file.getBytes();
        return extractTextFromBytes(bytes, fileExtension, fileName);
    }

    /**
     * 从字节数组提取文本
     */
    private String extractTextFromBytes(byte[] bytes, String extension, String fileName) throws IOException {
        // 将字节数组保存为临时文件
        Path tempFile = Files.createTempFile("upload-", "." + extension);
        try {
            Files.write(tempFile, bytes);

            return switch (extension.toLowerCase()) {
                case "pdf" -> extractPdfText(tempFile);
                case "txt", "md", "markdown" -> new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                default -> throw new IllegalArgumentException("不支持的文件类型: " + extension);
            };
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * 提取 PDF 文本
     */
    private String extractPdfText(Path filePath) throws IOException {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /**
     * 按段落分块
     * <p>
     * 使用双换行符作为段落分隔符，保持语义完整性
     * </p>
     */
    private List<DocumentChunk> chunkByParagraph(String text, String fileId, String fileName) {
        List<DocumentChunk> chunks = new ArrayList<>();

        // 按双换行符分割段落
        String[] paragraphs = text.split("\\n\\s*\\n");

        int position = 0;
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i].trim();

            // 跳过空段落
            if (paragraph.isEmpty()) {
                continue;
            }

            String chunkId = fileId + ":p" + i;

            DocumentChunk chunk = DocumentChunk.builder()
                    .chunkId(chunkId)
                    .fileName(fileName)
                    .content(paragraph)
                    .chunkIndex(i)
                    .startPosition(position)
                    .build();

            chunks.add(chunk);
            position += paragraph.length();
        }

        // 如果没有段落，整个文本作为一个块
        if (chunks.isEmpty() && !text.trim().isEmpty()) {
            chunks.add(DocumentChunk.builder()
                    .chunkId(fileId + ":p0")
                    .fileName(fileName)
                    .content(text.trim())
                    .chunkIndex(0)
                    .startPosition(0)
                    .build());
        }

        return chunks;
    }

    /**
     * 从块 ID 中提取文件 ID
     */
    public static String extractFileId(String chunkId) {
        Matcher matcher = CITATION_PATTERN.matcher(chunkId);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 从块 ID 中提取块索引
     */
    public static Integer extractChunkIndex(String chunkId) {
        Matcher matcher = CITATION_PATTERN.matcher(chunkId);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(2));
        }
        return null;
    }
}
