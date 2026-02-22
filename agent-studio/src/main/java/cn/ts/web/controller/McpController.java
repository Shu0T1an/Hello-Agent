package cn.ts.web.controller;

import cn.ts.agent.mcp.McpManager;
import cn.ts.agent.mcp.model.McpConnection;
import cn.ts.agent.mcp.model.McpConnectionConfig;
import cn.ts.agent.mcp.model.McpStatistics;
import cn.ts.web.controller.response.Result;
import cn.ts.web.controller.response.ResultCode;
import cn.ts.web.dto.McpConnectionRequest;
import cn.ts.web.dto.McpConnectionResponse;
import cn.ts.web.dto.McpToolInfo;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 管理控制器
 * <p>
 * 提供 MCP 连接的 REST API 端点
 * </p>
 *
 * @author tianshuo
 */
@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private static final Logger logger = LoggerFactory.getLogger(McpController.class);

    private final McpManager mcpManager;

    public McpController(McpManager mcpManager) {
        this.mcpManager = mcpManager;
    }

    /**
     * 注册新的 MCP 连接
     */
    @PostMapping("/connections")
    public Result<Map<String, Object>> registerConnection(
            @Valid @RequestBody McpConnectionRequest request) {
        try {
            logger.info("注册 MCP 连接: {}, 类型: {}", request.getName(), request.getType());

            McpConnectionConfig config = request.toConfig();
            boolean success = mcpManager.registerConnection(config);

            if (success) {
                Map<String, Object> response = new HashMap<>();
                response.put("name", request.getName());
                return Result.success("连接注册成功", response);
            } else {
                return Result.error("连接注册失败");
            }

        } catch (IllegalArgumentException e) {
            logger.warn("注册 MCP 连接失败: {}", e.getMessage());
            return Result.error(ResultCode.BAD_REQUEST.getCode(), e.getMessage());
        } catch (Exception e) {
            logger.error("注册 MCP 连接时发生错误", e);
            return Result.error(ResultCode.ERROR.getCode(), "服务器错误: " + e.getMessage());
        }
    }

    /**
     * 获取所有连接
     */
    @GetMapping("/connections")
    public Result<List<McpConnectionResponse>> getAllConnections() {
        List<McpConnectionResponse> responses = mcpManager.getAllConnections().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return Result.success(responses);
    }

    /**
     * 获取指定连接
     */
    @GetMapping("/connections/{name}")
    public Result<McpConnectionResponse> getConnection(@PathVariable String name) {
        return mcpManager.getConnection(name)
                .map(conn -> Result.success(toResponse(conn)))
                .orElse(Result.error(ResultCode.MCP_CONNECTION_NOT_FOUND));
    }

    /**
     * 删除连接
     */
    @DeleteMapping("/connections/{name}")
    public Result<Void> deleteConnection(@PathVariable String name) {
        try {
            mcpManager.unregisterConnection(name);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(ResultCode.MCP_CONNECTION_NOT_FOUND);
        }
    }

    /**
     * 更新连接
     */
    @PutMapping("/connections/{name}")
    public Result<Map<String, Object>> updateConnection(
            @PathVariable String name,
            @Valid @RequestBody McpConnectionRequest request) {
        try {
            McpConnectionConfig config = request.toConfig();
            mcpManager.updateConnection(name, config);

            Map<String, Object> response = new HashMap<>();
            response.put("name", name);
            return Result.success("连接已更新", response);
        } catch (IllegalArgumentException e) {
            return Result.error(ResultCode.MCP_CONNECTION_NOT_FOUND);
        } catch (Exception e) {
            return Result.error(ResultCode.ERROR.getCode(), e.getMessage());
        }
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/statistics")
    public Result<McpStatistics> getStatistics() {
        return Result.success(mcpManager.getStatistics());
    }

    /**
     * 健康检查指定连接
     */
    @PostMapping("/connections/{name}/health-check")
    public Result<Map<String, Object>> healthCheck(@PathVariable String name) {
        boolean healthy = mcpManager.healthCheck(name);

        Map<String, Object> response = new HashMap<>();
        response.put("name", name);
        response.put("healthy", healthy);
        response.put("timestamp", System.currentTimeMillis());

        return Result.success(response);
    }

    /**
     * 健康检查所有连接
     */
    @PostMapping("/health-check-all")
    public Result<Map<String, Boolean>> healthCheckAll() {
        return Result.success(mcpManager.healthCheckAll());
    }

    /**
     * 手动重连
     */
    @PostMapping("/connections/{name}/reconnect")
    public Result<Map<String, String>> reconnect(@PathVariable String name) {
        try {
            mcpManager.reconnect(name);
            Map<String, String> response = new HashMap<>();
            response.put("name", name);
            return Result.success("重连请求已发送", response);
        } catch (IllegalArgumentException e) {
            return Result.error(ResultCode.MCP_CONNECTION_NOT_FOUND);
        }
    }

    /**
     * 获取所有工具
     */
    @GetMapping("/tools")
    public Result<List<McpToolInfo>> getAllTools() {
        // TODO: 实现 MCP 工具列表获取
        // 由于 MCP SDK API 可能变化，暂时返回空列表
        return Result.success(new ArrayList<>());
    }

    /**
     * 启动 MCP 管理器
     */
    @PostMapping("/start")
    public Result<Void> start() {
        if (mcpManager.isRunning()) {
            return Result.success("MCP 管理器已在运行", null);
        }

        mcpManager.start();
        return Result.success("MCP 管理器已启动", null);
    }

    /**
     * 停止 MCP 管理器
     */
    @PostMapping("/stop")
    public Result<Void> stop() {
        if (!mcpManager.isRunning()) {
            return Result.success("MCP 管理器未运行", null);
        }

        mcpManager.stop();
        return Result.success("MCP 管理器已停止", null);
    }

    /**
     * 获取管理器状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", mcpManager.isRunning());
        status.put("connections", mcpManager.getAllConnections().size());
        status.put("statistics", mcpManager.getStatistics());
        return Result.success(status);
    }

    /**
     * 转换为响应 DTO
     */
    private McpConnectionResponse toResponse(McpConnection connection) {
        McpConnectionConfig config = connection.getConfig();

        McpConnectionResponse.ConfigSummary configSummary = null;
        if (config != null) {
            String command = null;
            String url = null;

            if (config.getStdioConfig() != null) {
                command = config.getStdioConfig().getCommand();
            } else if (config.getSseConfig() != null) {
                url = config.getSseConfig().getUrl();
            } else if (config.getHttpConfig() != null) {
                url = config.getHttpConfig().getUrl();
            }

            configSummary = McpConnectionResponse.ConfigSummary.builder()
                    .command(command)
                    .url(url)
                    .timeoutSeconds(config.getTimeout() != null ? (int) config.getTimeout().getSeconds() : null)
                    .autoReconnect(config.isAutoReconnect())
                    .build();
        }

        McpConnection.ConnectionStatistics stats = connection.getStatistics();
        McpConnectionResponse.StatisticsSummary statsSummary = McpConnectionResponse.StatisticsSummary.builder()
                .totalCalls(stats.getTotalCalls().get())
                .successfulCalls(stats.getSuccessfulCalls().get())
                .failedCalls(stats.getFailedCalls().get())
                .averageResponseTime(stats.getAverageResponseTime())
                .lastCallTime(stats.getLastCallTime())
                .build();

        return McpConnectionResponse.builder()
                .name(connection.getName())
                .type(config != null ? config.getType() : null)
                .description(config != null ? config.getDescription() : null)
                .status(connection.getStatus())
                .connectedAt(connection.getConnectedAt())
                .errorMessage(connection.getErrorMessage())
                .config(configSummary)
                .statistics(statsSummary)
                .build();
    }
}
