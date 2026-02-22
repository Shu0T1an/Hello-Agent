package cn.ts.web.service;

import java.util.List;

/**
 * MCP 工具同步服务
 * <p>
 * 负责 MCP 连接状态变化时自动同步工具到数据库
 * </p>
 *
 * @author tianshuo
 */
public interface McpToolSyncService {

    /**
     * 同步指定 MCP 连接的工具
     *
     * @param connectionName MCP 连接名称
     * @return 同步的工具数量
     */
    int syncTools(String connectionName);

    /**
     * 同步所有 MCP 连接的工具
     *
     * @return 同步的工具数量
     */
    int syncAllTools();

    /**
     * 禁用指定 MCP 连接的所有工具
     *
     * @param connectionName MCP 连接名称
     */
    void disableTools(String connectionName);

    /**
     * MCP 工具信息
     */
    record McpToolInfo(
            String name,
            String description
    ) {
    }
}
