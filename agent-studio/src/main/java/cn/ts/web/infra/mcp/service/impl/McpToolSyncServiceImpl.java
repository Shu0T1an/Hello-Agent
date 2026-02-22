package cn.ts.web.infra.mcp.service.impl;

import cn.ts.agent.mcp.McpManager;
// import cn.ts.agent.mcp.event.McpConnectionEvent;
import cn.ts.agent.mcp.model.McpConnection;
import cn.ts.agent.mcp.model.McpConnectionStatus;
import cn.ts.web.agent.dto.ToolDefinitionDTO;
import cn.ts.web.agent.dto.ToolType;
import cn.ts.web.infra.mcp.service.McpToolSyncService;
import cn.ts.web.tool.service.ToolDefinitionService;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// import org.springframework.context.event.EventListener;
// import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP 工具同步服务实现
 * <p>
 * 监听 MCP 连接事件，自动同步工具到数据库
 * </p>
 *
 * @author tianshuo
 */
@Service
public class McpToolSyncServiceImpl implements McpToolSyncService {

    private static final Logger logger = LoggerFactory.getLogger(McpToolSyncServiceImpl.class);

    private final McpManager mcpManager;
    private final ToolDefinitionService toolDefinitionService;

    public McpToolSyncServiceImpl(
            McpManager mcpManager,
            ToolDefinitionService toolDefinitionService) {
        this.mcpManager = mcpManager;
        this.toolDefinitionService = toolDefinitionService;
    }

    /**
     * 监听 MCP 连接事件
     * 暂时注释，等待事件类实现
     */
    /*
    @Async("mcpSyncTaskExecutor")
    @EventListener
    public void handleMcpConnectionEvent(McpConnectionEvent event) {
        logger.info("Received MCP connection event: {}", event);

        String connectionName = event.getConnectionName();

        switch (event.getStatus()) {
            case CONNECTED -> {
                try {
                    int count = syncTools(connectionName);
                    logger.info("Successfully synced {} tools for connection: {}", count, connectionName);
                } catch (Exception e) {
                    logger.error("Failed to sync tools for connection: {}", connectionName, e);
                }
            }
            case DISCONNECTED, ERROR -> {
                try {
                    disableTools(connectionName);
                    logger.info("Disabled tools for connection: {}", connectionName);
                } catch (Exception e) {
                    logger.error("Failed to disable tools for connection: {}", connectionName, e);
                }
            }
            default -> {
                // 其他状态不做处理
                logger.debug("Ignoring MCP connection event with status: {}", event.getStatus());
            }
        }
    }
    */

    @Override
    public int syncTools(String connectionName) {
        logger.info("Syncing tools for MCP connection: {}", connectionName);

        // 获取 MCP 连接
        McpConnection connection = mcpManager.getConnection(connectionName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "MCP connection not found: " + connectionName));

        // 获取 MCP 客户端
        McpSyncClient client = connection.getClient();

        // 列出所有工具
        var toolsListResult = client.listTools();

        // 转换为 ToolDefinitionService.McpToolInfo
        List<ToolDefinitionService.McpToolInfo> serviceMcpToolInfos = toolsListResult.tools().stream()
                .map(tool -> new ToolDefinitionService.McpToolInfo(tool.name(), tool.description()))
                .collect(Collectors.toList());

        // 同步到数据库
        toolDefinitionService.syncMcpTools(connectionName, serviceMcpToolInfos);

        logger.info("Synced {} tools for connection: {}", serviceMcpToolInfos.size(), connectionName);
        return serviceMcpToolInfos.size();
    }

    @Override
    public int syncAllTools() {
        logger.info("Syncing tools for all MCP connections");

        List<McpConnection> connections = mcpManager.getAllConnections();
        int totalCount = 0;

        for (McpConnection connection : connections) {
            if (connection.getStatus() == McpConnectionStatus.CONNECTED) {
                try {
                    int count = syncTools(connection.getName());
                    totalCount += count;
                } catch (Exception e) {
                    logger.error("Failed to sync tools for connection: {}", connection.getName(), e);
                }
            }
        }

        logger.info("Synced {} tools from {} connections", totalCount, connections.size());
        return totalCount;
    }

    @Override
    public void disableTools(String connectionName) {
        logger.info("Disabling tools for MCP connection: {}", connectionName);
        toolDefinitionService.disableToolsByConnection(connectionName);
    }
}
