package cn.ts.web.service;

import cn.ts.web.dto.CitationReference;
import cn.ts.web.dto.DocumentChunk;
import cn.ts.web.dto.TemporaryFileContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 引用追踪服务
 * <p>
 * 功能：
 * 1. 构建带引用标记的上下文（用于注入到 LLM）
 * 2. 解析 LLM 回答中的引用标记
 * 3. 生成引用信息列表
 * </p>
 *
 * @author tianshuo
 */
@Service
public class CitationService {

    private static final Logger logger = LoggerFactory.getLogger(CitationService.class);

    /**
     * 引用标记正则表达式
     * 匹配格式：[doc0:p2], [doc1:p15] 等
     */
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[([a-zA-Z]+\\d*):p(\\d+)\\]");

    /**
     * 构建带标记的上下文（用于注入到 LLM）
     *
     * @param fileContents 文件内容列表
     * @return 带引用标记的上下文字符串
     */
    public String buildAnnotatedContext(List<TemporaryFileContent> fileContents) {
        if (fileContents == null || fileContents.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder("\n\n--- 参考文档 ---\n");

        for (TemporaryFileContent file : fileContents) {
            context.append(String.format("\n【文件：%s】\n", file.getFileName()));

            for (DocumentChunk chunk : file.getChunks()) {
                // 格式：[chunkId] content
                context.append(String.format("[%s] %s\n\n",
                        chunk.getChunkId(),
                        chunk.getContent()));
            }
        }

        context.append("--- 参考文档结束 ---\n");

        return context.toString();
    }

    /**
     * 生成引用指令（添加到 System Message）
     *
     * @param annotatedContext 带标记的上下文
     * @return 引用指令字符串
     */
    public String buildCitationInstruction(String annotatedContext) {
        return String.format("""
                回答用户问题时，如果参考了文档内容，请在引用处使用文档块标记，如 [doc0:p2]。
                以下是为您提供的参考文档：

                %s

                请确保在回答中准确标注引用来源。
                """, annotatedContext);
    }

    /**
     * 从 LLM 回答中提取引用标记
     *
     * @param llmResponse   LLM 回答文本
     * @param fileContents  文件内容列表
     * @return 引用信息列表
     */
    public List<CitationReference> extractCitations(String llmResponse, List<TemporaryFileContent> fileContents) {
        if (llmResponse == null || llmResponse.isEmpty()) {
            return Collections.emptyList();
        }

        if (fileContents == null || fileContents.isEmpty()) {
            return Collections.emptyList();
        }

        // 构建块 ID 到内容的映射
        Map<String, DocumentChunk> chunkMap = buildChunkMap(fileContents);

        // 提取所有引用标记
        Set<String> citedChunkIds = extractCitedChunkIds(llmResponse);

        // 构建引用信息列表
        List<CitationReference> citations = new ArrayList<>();
        for (String chunkId : citedChunkIds) {
            DocumentChunk chunk = chunkMap.get(chunkId);
            if (chunk != null) {
                citations.add(CitationReference.builder()
                        .chunkId(chunk.getChunkId())
                        .fileName(chunk.getFileName())
                        .content(chunk.getContent())
                        .chunkIndex(chunk.getChunkIndex())
                        .build());
            } else {
                logger.warn("未找到引用的块: {}", chunkId);
            }
        }

        // 按文件名和块索引排序
        citations.sort(Comparator.comparing(CitationReference::getFileName)
                .thenComparing(CitationReference::getChunkIndex));

        logger.info("提取到 {} 个引用", citations.size());

        return citations;
    }

    /**
     * 构建块 ID 到内容的映射
     */
    private Map<String, DocumentChunk> buildChunkMap(List<TemporaryFileContent> fileContents) {
        Map<String, DocumentChunk> chunkMap = new HashMap<>();

        for (TemporaryFileContent file : fileContents) {
            if (file.getChunks() != null) {
                for (DocumentChunk chunk : file.getChunks()) {
                    chunkMap.put(chunk.getChunkId(), chunk);
                }
            }
        }

        return chunkMap;
    }

    /**
     * 从文本中提取所有引用的块 ID
     */
    private Set<String> extractCitedChunkIds(String text) {
        Set<String> chunkIds = new LinkedHashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(text);

        while (matcher.find()) {
            String chunkId = matcher.group(1) + ":p" + matcher.group(2);
            chunkIds.add(chunkId);
        }

        return chunkIds;
    }

    /**
     * 清理回答文本中的引用标记（可选功能）
     * <p>
     * 如果需要在显示给用户时移除引用标记，可以使用此方法
     * </p>
     *
     * @param text 原始文本
     * @return 清理后的文本
     */
    public String stripCitationMarkers(String text) {
        if (text == null) {
            return null;
        }
        return CITATION_PATTERN.matcher(text).replaceAll("");
    }

    /**
     * 高亮回答文本中的引用位置（可选功能）
     * <p>
     * 为引用标记添加 HTML 标签，便于前端样式化
     * </p>
     *
     * @param text 原始文本
     * @return 带 HTML 标记的文本
     */
    public String highlightCitations(String text) {
        if (text == null) {
            return null;
        }
        return CITATION_PATTERN.matcher(text).replaceAll("<citation>$0</citation>");
    }
}
