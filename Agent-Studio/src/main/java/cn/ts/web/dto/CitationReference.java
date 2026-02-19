package cn.ts.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 引用信息（AI 回答中的引用）
 * <p>
 * 用于在 AI 回答中显示引用来源
 * </p>
 *
 * @author tianshuo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationReference {

    /**
     * 引用的块 ID
     */
    private String chunkId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 引用的内容片段
     */
    private String content;

    /**
     * 块序号
     */
    private Integer chunkIndex;
}
