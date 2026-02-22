package cn.ts.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档块（带 ID 的内容片段）
 * <p>
 * 用于临时文件上传功能，每个块代表文件的一个段落或章节
 * </p>
 *
 * @author tianshuo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    /**
     * 块 ID，如 "doc1:p2"
     * 格式：{fileId}:p{chunkIndex}
     */
    private String chunkId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 块内容
     */
    private String content;

    /**
     * 块序号（从 0 开始）
     */
    private Integer chunkIndex;

    /**
     * 在原文中的起始位置
     */
    private Integer startPosition;
}
