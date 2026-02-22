package cn.ts.web.service.rag;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件系统测试工具类
 * <p>
 * 提供文件系统相关的测试辅助方法，包括创建测试文件、目录管理等
 * </p>
 *
 * @author tianshuo
 */
public class FileSystemTestUtils {

    private FileSystemTestUtils() {
        // 工具类，禁止实例化
    }

    // ==================== 文本文件创建 ====================

    /**
     * 创建测试文本文件
     *
     * @param directory 目录路径
     * @param filename  文件名
     * @param content   文件内容
     * @return 文件的完整路径
     */
    public static String createTextFile(String directory, String filename, String content) throws IOException {
        Path dirPath = Paths.get(directory);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        Path filePath = dirPath.resolve(filename);
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        return filePath.toString();
    }

    /**
     * 创建测试 Markdown 文件
     *
     * @param directory 目录路径
     * @param filename  文件名
     * @param content   文件内容
     * @return 文件的完整路径
     */
    public static String createMarkdownFile(String directory, String filename, String content) throws IOException {
        return createTextFile(directory, filename, content);
    }

    // ==================== PDF 文件创建 ====================

    /**
     * 创建简单的测试 PDF 文件
     *
     * @param directory 目录路径
     * @param filename  文件名
     * @param text      PDF 文本内容
     * @return 文件的完整路径
     */
    public static String createPdfFile(String directory, String filename, String text) throws IOException {
        Path dirPath = Paths.get(directory);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        Path filePath = dirPath.resolve(filename);

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            PDFont font = PDType1Font.HELVETICA;
            int fontSize = 12;
            float margin = 50;
            float y = page.getMediaBox().getHeight() - margin;

            // 简单的文本换行处理 - 只写入前几行以避免资源管理问题
            String[] lines = text.split("\n");
            int maxLines = 10; // 限制行数以简化实现

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(font, fontSize);
                contentStream.newLineAtOffset(margin, y);

                for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
                    String line = lines[i];
                    contentStream.showText(line);
                    contentStream.newLineAtOffset(0, -fontSize * 1.5f);
                }

                contentStream.endText();
            }

            document.save(filePath.toFile());
        }

        return filePath.toString();
    }

    /**
     * 创建多页 PDF 文件（用于测试分页）
     *
     * @param directory  目录路径
     * @param filename   文件名
     * @param pageCount  页数
     * @param textPerPage 每页文本
     * @return 文件的完整路径
     */
    public static String createMultiPagePdfFile(String directory, String filename, int pageCount, String textPerPage) throws IOException {
        Path dirPath = Paths.get(directory);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        Path filePath = dirPath.resolve(filename);

        try (PDDocument document = new PDDocument()) {
            PDFont font = PDType1Font.HELVETICA;
            int fontSize = 12;
            float margin = 50;

            for (int i = 0; i < pageCount; i++) {
                PDPage page = new PDPage();
                document.addPage(page);

                float y = page.getMediaBox().getHeight() - margin;

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    contentStream.setFont(font, fontSize);
                    contentStream.newLineAtOffset(margin, y);
                    contentStream.showText("Page " + (i + 1) + ": " + textPerPage);
                    contentStream.endText();
                }
            }

            document.save(filePath.toFile());
        }

        return filePath.toString();
    }

    /**
     * 创建包含中文的 PDF 文件
     * <p>
     * 注意：PDFBox 默认字体不支持中文，这里使用英文代替
     * </p>
     *
     * @param directory 目录路径
     * @param filename  文件名
     * @return 文件的完整路径
     */
    public static String createChinesePdfFile(String directory, String filename) throws IOException {
        // 使用英文代替中文，因为 PDType1Font 不支持中文
        String englishText = "This is a test PDF with English text instead of Chinese.\n" +
                "Line 2: Testing PDFBox text extraction.\n" +
                "Line 3: Multiple lines for testing.";
        return createPdfFile(directory, filename, englishText);
    }

    // ==================== 目录管理 ====================

    /**
     * 创建测试目录（如果不存在）
     *
     * @param directory 目录路径
     * @return 目录的 Path 对象
     */
    public static Path createDirectory(String directory) throws IOException {
        Path dirPath = Paths.get(directory);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
        return dirPath;
    }

    /**
     * 创建知识库目录结构
     *
     * @param baseDir        基础目录
     * @param knowledgeBaseId 知识库 ID
     * @return 知识库目录的 Path 对象
     */
    public static Path createKnowledgeBaseDirectory(String baseDir, String knowledgeBaseId) throws IOException {
        Path kbDir = Paths.get(baseDir, knowledgeBaseId);
        if (!Files.exists(kbDir)) {
            Files.createDirectories(kbDir);
        }
        return kbDir;
    }

    /**
     * 删除测试目录及其所有内容
     *
     * @param directory 要删除的目录路径
     */
    public static void deleteDirectory(String directory) throws IOException {
        Path dirPath = Paths.get(directory);
        if (Files.exists(dirPath)) {
            Files.walk(dirPath)
                    .sorted((a, b) -> b.compareTo(a)) // 反向排序，先删除文件
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // 静默失败，用于清理测试文件
                        }
                    });
        }
    }

    /**
     * 删除单个文件
     *
     * @param filePath 文件路径
     */
    public static void deleteFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }

    // ==================== 文件检查 ====================

    /**
     * 检查文件是否存在
     *
     * @param filePath 文件路径
     * @return 文件是否存在
     */
    public static boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }

    /**
     * 检查目录是否存在
     *
     * @param directory 目录路径
     * @return 目录是否存在
     */
    public static boolean directoryExists(String directory) {
        return Files.exists(Paths.get(directory));
    }

    /**
     * 读取文件内容
     *
     * @param filePath 文件路径
     * @return 文件内容
     */
    public static String readFileContent(String filePath) throws IOException {
        return Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
    }

    /**
     * 获取文件大小（字节）
     *
     * @param filePath 文件路径
     * @return 文件大小
     */
    public static long getFileSize(String filePath) throws IOException {
        return Files.size(Paths.get(filePath));
    }

    // ==================== 预定义测试内容 ====================

    /**
     * 获取标准的测试文本内容
     */
    public static String getStandardTestText() {
        return "This is a test document for RAG functionality.\n" +
                "It contains multiple lines of text.\n" +
                "The purpose is to test document loading and processing.\n" +
                "Each line represents a segment of information.\n" +
                "End of test document.";
    }

    /**
     * 获取 Markdown 格式的测试内容
     */
    public static String getMarkdownTestContent() {
        return "# Test Markdown Document\n" +
                "\n" +
                "## Introduction\n" +
                "This is a test markdown file for RAG testing.\n" +
                "\n" +
                "## Features\n" +
                "- Feature 1: Text extraction\n" +
                "- Feature 2: Metadata handling\n" +
                "- Feature 3: Vector storage\n" +
                "\n" +
                "## Conclusion\n" +
                "This concludes the test document.";
    }

    /**
     * 获取长文本内容（用于测试分块）
     */
    public static String getLongTextContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("Long Document for Testing Chunk Splitting\n\n");

        for (int i = 1; i <= 50; i++) {
            sb.append("Section ").append(i).append(": ");
            sb.append("This is a paragraph with enough content to test ");
            sb.append("text splitting functionality. ");
            sb.append("The splitter should break this into reasonable chunks. ");
            sb.append("Each chunk should be within the configured size limit.\n\n");
        }

        return sb.toString();
    }

    /**
     * 获取包含特殊字符的测试内容
     */
    public static String getSpecialCharsTextContent() {
        return "Special Characters Test\n" +
                "========================\n" +
                "Symbols: !@#$%^&*()_+-=[]{}|;':\",./<>?\n" +
                "Quotes: \"Double\" and 'Single'\n" +
                "Tabs:\t\tTabbed content\n" +
                "Newlines:\n\nMultiple newlines\n" +
                "Unicode: (Note: Chinese may not display in default PDF font)\n";
    }

    /**
     * 获取 PDF 测试文本内容
     */
    public static String getPdfTestContent() {
        return "PDF Document Test Content\n" +
                "=========================\n" +
                "Page 1: This is the first page of the test PDF.\n" +
                "It contains text that should be extracted by PDFBox.\n" +
                "\n" +
                "The PDF is used for testing document loading in RAG system.\n" +
                "Each document will be processed and vectorized.";
    }
}
