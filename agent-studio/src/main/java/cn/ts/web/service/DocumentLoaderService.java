package cn.ts.web.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文档加载服务
 * <p>
 * 功能：
 * 1. 支持 PDF、TXT、Markdown 格式文档加载
 * 2. 文档分块处理
 * 3. 向量化并存储到 PgVector
 * 4. 保留文档元数据
 * </p>
 *
 * @author tianshuo
 */
@Service
public class DocumentLoaderService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentLoaderService.class);

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;
    private final String uploadDirectory;

    public DocumentLoaderService(VectorStore vectorStore,
                                 TokenTextSplitter textSplitter,
                                 @Value("${rag.document.upload-directory}") String uploadDirectory) {
        this.vectorStore = vectorStore;
        this.textSplitter = textSplitter;
        this.uploadDirectory = uploadDirectory;
        initUploadDirectory();
    }

    /**
     * 初始化上传目录
     */
    private void initUploadDirectory() {
        try {
            Path path = Path.of(uploadDirectory);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("创建文档上传目录: {}", uploadDirectory);
            }
        } catch (IOException e) {
            logger.error("创建上传目录失败", e);
            throw new RuntimeException("创建上传目录失败", e);
        }
    }

    /**
     * 加载并处理文档
     *
     * @param file            上传的文件
     * @param knowledgeBaseId 知识库 ID
     * @return 处理的文档块数量
     */
    public int loadDocument(MultipartFile file, String knowledgeBaseId) {
        try {
            // 1. 保存文件到本地
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            String savedFilePath = saveFile(file, knowledgeBaseId);

            logger.info("开始处理文档: {}, 类型: {}", originalFilename, fileExtension);

            // 2. 根据文件类型读取文档
            List<Document> documents = readDocument(savedFilePath, fileExtension, originalFilename);

            // 3. 添加元数据
            documents = documents.stream()
                    .map(doc -> {
                        Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                        metadata.put("file_name", originalFilename);
                        metadata.put("knowledge_base_id", knowledgeBaseId);
                        metadata.put("file_path", savedFilePath);
                        metadata.put("document_id", UUID.randomUUID().toString());
                        metadata.put("upload_time", System.currentTimeMillis());
                        return new Document(doc.getText(), metadata);
                    })
                    .collect(Collectors.toList());

            // 4. 文本分块
            List<Document> splitDocuments = textSplitter.apply(documents);

            logger.info("文档分块完成: 原始{}个文档 -> 分割后{}个文档块",
                    documents.size(), splitDocuments.size());

            // 5. 向量化并存储


            vectorStore.add(splitDocuments);

            logger.info("文档处理完成: {}, 向量化{}个文档块", originalFilename, splitDocuments.size());

            return splitDocuments.size();

        } catch (Exception e) {
            logger.error("文档处理失败: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据文件类型读取文档
     */
    private List<Document> readDocument(String filePath, String extension, String originalFilename) {
        try {
            FileSystemResource resource = new FileSystemResource(filePath);

            return switch (extension.toLowerCase()) {
                case "pdf" -> readPdfDocument(resource);
                case "txt", "md", "markdown" -> readTextDocument(resource);
                default -> throw new IllegalArgumentException(
                        "不支持的文件类型: " + extension);
            };

        } catch (Exception e) {
            logger.error("读取文档失败: {}", filePath, e);
            throw new RuntimeException("读取文档失败", e);
        }
    }

    /**
     * 读取 PDF 文档（使用 Apache PDFBox）
     */
    private List<Document> readPdfDocument(FileSystemResource resource) {
        logger.info("使用 PDFBox 读取 PDF 文档");

        try (PDDocument document = PDDocument.load(resource.getFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            String text = stripper.getText(document);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", resource.getFilename());

            return List.of(new Document(text, metadata));
        } catch (IOException e) {
            logger.error("PDF 读取失败: {}", resource.getFilename(), e);
            throw new RuntimeException("PDF 读取失败", e);
        }
    }

    /**
     * 读取文本文档（TXT、Markdown）
     */
    private List<Document> readTextDocument(FileSystemResource resource) {
        logger.info("使用 TextReader 读取文本文档");
        TextReader reader = new TextReader(resource);
        reader.getCustomMetadata().put("source", resource.getFilename());
        return reader.get();
    }

    /**
     * 保存文件到本地
     */
    private String saveFile(MultipartFile file, String knowledgeBaseId) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String uniqueFileName = UUID.randomUUID() + "." + fileExtension;

        // 创建知识库子目录
        Path kbDir = Path.of(uploadDirectory, knowledgeBaseId);
        if (!Files.exists(kbDir)) {
            Files.createDirectories(kbDir);
        }

        Path targetPath = kbDir.resolve(uniqueFileName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        logger.info("文件已保存: {}", targetPath);

        return targetPath.toString();
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
     * 删除知识库的所有文档
     */
    public void deleteKnowledgeBaseDocuments(String knowledgeBaseId) {
        // 从向量存储中删除 - 搜索并删除
        try {
            // 使用空字符串搜索获取所有文档（或尽可能多的文档）
            List<Document> allDocs = vectorStore.similaritySearch("search all documents for deletion " + System.currentTimeMillis());

            // 过滤出属于该知识库的文档
            List<String> docIds = allDocs.stream()
                    .filter(doc -> {
                        Object kbId = doc.getMetadata().get("knowledge_base_id");
                        return knowledgeBaseId.equals(kbId);
                    })
                    .map(doc -> (String) doc.getMetadata().get("document_id"))
                    .filter(id -> id != null)
                    .toList();

            if (!docIds.isEmpty()) {
                vectorStore.delete(docIds);
                logger.info("知识库文档已从向量存储删除: {}, 删除数量: {}", knowledgeBaseId, docIds.size());
            }
        } catch (Exception e) {
            logger.error("删除向量存储文档失败: {}", knowledgeBaseId, e);
        }

        // 删除本地文件
        Path kbDir = Path.of(uploadDirectory, knowledgeBaseId);
        if (Files.exists(kbDir)) {
            try {
                Files.walk(kbDir)
                        .sorted((a, b) -> b.compareTo(a)) // 反向排序，先删除文件
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                logger.warn("删除文件失败: {}", path, e);
                            }
                        });
                logger.info("知识库文档已从本地删除: {}", knowledgeBaseId);
            } catch (IOException e) {
                logger.error("删除知识库目录失败: {}", knowledgeBaseId, e);
            }
        }
    }
}
