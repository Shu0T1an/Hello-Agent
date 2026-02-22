package cn.ts.web.dto;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP 工具信息 DTO
 *
 * @author tianshuo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolInfo {

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 输入模式（JSON Schema）
     */
    private Map<String, Object> inputSchema;

    /**
     * 连接名称
     */
    private String connectionName;

    /**
     * 从 McpSchema.Tool 转换
     */
    public static McpToolInfo from(McpSchema.Tool tool, String connectionName) {
        return McpToolInfo.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(tool.inputSchema().properties())
                .connectionName(connectionName)
                .build();
    }
}
