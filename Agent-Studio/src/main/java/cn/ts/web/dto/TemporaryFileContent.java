package cn.ts.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文件内容（包含多个块）
 * <p>
 * 用于临时文件上传功能，包含文件的元数据和分块内容
 * </p>
 *
 * @author tianshuo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemporaryFileContent {

    /**
     * 文件 ID，如 "doc0", "doc1"
     */
    private String fileId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long size;

    /**
     * 文件分块列表
     */
    private List<DocumentChunk> chunks;
}
