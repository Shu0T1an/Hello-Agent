package cn.ts.agent.mcp;

import cn.ts.agent.mcp.config.McpManagerConfig;
import cn.ts.agent.mcp.model.McpConnection;
import cn.ts.agent.mcp.model.McpConnectionConfig;
import cn.ts.agent.mcp.model.McpConnectionStatus;
import cn.ts.agent.mcp.model.McpConnectionType;
import cn.ts.agent.mcp.model.McpStatistics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * MCP 管理器实现类
 * <p>
 * 负责 MCP 连接的生命周期管理，包括创建、连接、健康检查和自动重连
 * </p>
 *
 * @author tianshuo
 */
public class McpManagerImpl implements McpManager {

    private static final Logger logger = LoggerFactory.getLogger(McpManagerImpl.class);

    private final McpManagerConfig config;
    private final Map<String, McpConnection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledFuture<?> healthCheckTask;

    public McpManagerImpl(McpManagerConfig config) {
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "mcp-manager-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public boolean registerConnection(McpConnectionConfig config) {
        if (!StringUtils.hasText(config.getName())) {
            throw new IllegalArgumentException("连接名称不能为空");
        }
        if (connections.containsKey(config.getName())) {
            throw new IllegalArgumentException("连接名称已存在: " + config.getName());
        }

        try {
            McpConnection connection = new McpConnection();
            connection.setName(config.getName());
            connection.setConfig(config);
            connection.setStatus(McpConnectionStatus.CONNECTING);

            connections.put(config.getName(), connection);

            // 异步连接
            connectAsync(connection);

            logger.info("MCP 连接注册成功: {}, 类型: {}", config.getName(), config.getType());
            return true;
        } catch (Exception e) {
            logger.error("MCP 连接注册失败: {}", config.getName(), e);
            connections.remove(config.getName());
            return false;
        }
    }

    @Override
    public void unregisterConnection(String name) {
        McpConnection connection = connections.remove(name);
        if (connection == null) {
            throw new IllegalArgumentException("连接不存在: " + name);
        }

        disconnect(connection);
        logger.info("MCP 连接已注销: {}", name);
    }

    @Override
    public void updateConnection(String name, McpConnectionConfig newConfig) {
        McpConnection connection = connections.get(name);
        if (connection == null) {
            throw new IllegalArgumentException("连接不存在: " + name);
        }

        // 先断开旧连接
        disconnect(connection);

        // 更新配置
        connection.setConfig(newConfig);
        connection.setStatus(McpConnectionStatus.CONNECTING);
        connection.setErrorMessage(null);

        // 重新连接
        connectAsync(connection);

        logger.info("MCP 连接已更新: {}", name);
    }

    @Override
    public Optional<McpConnection> getConnection(String name) {
        return Optional.ofNullable(connections.get(name));
    }

    @Override
    public List<McpConnection> getAllConnections() {
        return new ArrayList<>(connections.values());
    }

    @Override
    public List<McpSyncClient> getAllMcpClients() {
        return connections.values().stream()
                .filter(c -> c.getStatus() == McpConnectionStatus.CONNECTED)
                .map(McpConnection::getClient)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public ToolCallback[] getAllToolCallbacks() {
        // TODO: 等待 Spring AI MCP API 稳定后实现
        // 暂时返回空数组
        return new ToolCallback[0];
    }

    @Override
    public boolean healthCheck(String name) {
        McpConnection connection = connections.get(name);
        if (connection == null) {
            logger.warn("健康检查失败: 连接不存在 - {}", name);
            return false;
        }

        return performHealthCheck(connection);
    }

    @Override
    public Map<String, Boolean> healthCheckAll() {
        Map<String, Boolean> results = new HashMap<>();
        for (String name : connections.keySet()) {
            results.put(name, healthCheck(name));
        }
        return results;
    }

    @Override
    public void reconnect(String name) {
        McpConnection connection = connections.get(name);
        if (connection == null) {
            throw new IllegalArgumentException("连接不存在: " + name);
        }

        logger.info("手动重连: {}", name);
        disconnect(connection);
        connection.setStatus(McpConnectionStatus.CONNECTING);
        connection.setErrorMessage(null);
        connectAsync(connection);
    }

    @Override
    public McpStatistics getStatistics() {
        McpStatistics.McpStatisticsBuilder builder = McpStatistics.builder();

        int connectedCount = 0;
        int connectingCount = 0;
        int errorCount = 0;
        int disconnectedCount = 0;
        long totalCalls = 0;
        long successfulCalls = 0;
        long failedCalls = 0;
        double totalResponseTime = 0;
        int totalTools = 0;

        Map<String, McpStatistics.ConnectionStatistics> connStats = new HashMap<>();

        for (McpConnection connection : connections.values()) {
            switch (connection.getStatus()) {
                case CONNECTED -> connectedCount++;
                case CONNECTING -> connectingCount++;
                case ERROR -> errorCount++;
                case DISCONNECTED -> disconnectedCount++;
            }

            McpConnection.ConnectionStatistics stats = connection.getStatistics();
            long connTotalCalls = stats.getTotalCalls().get();
            long connSuccessfulCalls = stats.getSuccessfulCalls().get();
            long connFailedCalls = stats.getFailedCalls().get();

            totalCalls += connTotalCalls;
            successfulCalls += connSuccessfulCalls;
            failedCalls += connFailedCalls;

            double avgResponse = stats.getAverageResponseTime();
            totalResponseTime += avgResponse * connSuccessfulCalls;

            // 获取工具数量
            int toolCount = 0;
            if (connection.getClient() != null) {
                try {
                    var tools = connection.getClient().listTools();
                    if (tools != null) {
                        toolCount = tools.tools().size();
                    }
                } catch (Exception e) {
                    logger.debug("获取工具数量失败: {}", connection.getName(), e);
                }
            }
            totalTools += toolCount;

            // 构建连接统计信息
            McpStatistics.ConnectionStatistics cs = McpStatistics.ConnectionStatistics.builder()
                    .name(connection.getName())
                    .status(connection.getStatus())
                    .totalCalls(connTotalCalls)
                    .successfulCalls(connSuccessfulCalls)
                    .failedCalls(connFailedCalls)
                    .averageResponseTime(avgResponse)
                    .toolCount(toolCount)
                    .lastCallTime(stats.getLastCallTime())
                    .build();
            connStats.put(connection.getName(), cs);
        }

        return builder
                .totalConnections(connections.size())
                .connectedCount(connectedCount)
                .connectingCount(connectingCount)
                .errorCount(errorCount)
                .disconnectedCount(disconnectedCount)
                .totalTools(totalTools)
                .totalCalls(totalCalls)
                .successfulCalls(successfulCalls)
                .failedCalls(failedCalls)
                .averageResponseTime(successfulCalls > 0 ? totalResponseTime / successfulCalls : 0)
                .connectionStatistics(connStats)
                .timestamp(Instant.now())
                .build();
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("MCP 管理器启动中...");

            // 启动健康检查任务
            if (config.isEnableHealthCheck()) {
                startHealthCheckTask();
            }

            logger.info("MCP 管理器已启动");
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("MCP 管理器停止中...");

            // 停止健康检查任务
            if (healthCheckTask != null) {
                healthCheckTask.cancel(false);
                healthCheckTask = null;
            }

            // 关闭所有连接
            for (McpConnection connection : connections.values()) {
                disconnect(connection);
            }

            // 关闭调度器
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }

            logger.info("MCP 管理器已停止");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 异步连接
     */
    private void connectAsync(McpConnection connection) {
        CompletableFuture.runAsync(() -> {
            try {
                connect(connection);
            } catch (Exception e) {
                logger.error("连接失败: {}", connection.getName(), e);
                connection.setStatus(McpConnectionStatus.ERROR);
                connection.setErrorMessage(e.getMessage());

                // 尝试重连
                if (connection.getConfig().isAutoReconnect()) {
                    scheduleReconnect(connection);
                }
            }
        }, scheduler);
    }

    /**
     * 执行连接
     */
    private void connect(McpConnection connection) {
        McpConnectionConfig config = connection.getConfig();
        McpSyncClient client = createMcpClient(config);
        client.initialize();
        logger.info("MCP 客户端已初始化");

        connection.setClient(client);
        connection.setStatus(McpConnectionStatus.CONNECTED);
        connection.setConnectedAt(Instant.now());
        connection.setErrorMessage(null);

        logger.info("MCP 连接成功: {}, 类型: {}", config.getName(), config.getType());
    }

    /**
     * 创建 MCP 客户端
     * <p>
     * 注意：此方法需要根据实际的 MCP SDK API 进行调整
     * </p>
     */
    private McpSyncClient createMcpClient(McpConnectionConfig config) {
        // TODO: 实现 MCP 客户端创建逻辑
        // 由于 MCP SDK API 可能变化，暂时返回 null

        if(config.getStdioConfig()!=null){
            ServerParameters params = ServerParameters
                    .builder(config.getStdioConfig().getCommand())
                    .args(config.getStdioConfig().getArgs())
                    .env(config.getStdioConfig().getEnv())
                    .build();
//            JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
            ObjectMapper objectMapper = new ObjectMapper();
            McpClientTransport transport = new StdioClientTransport(params, objectMapper);
            return McpClient.sync(transport)
                    .build();

        }
        logger.warn("MCP 客户端创建功能待实现，类型: {}", config.getType());
        return null;
    }

    /**
     * 断开连接
     */
    private void disconnect(McpConnection connection) {
        if (connection.getClient() != null) {
            try {
                connection.getClient().close();
            } catch (Exception e) {
                logger.warn("关闭 MCP 客户端失败: {}", connection.getName(), e);
            }
            connection.setClient(null);
        }
        connection.setStatus(McpConnectionStatus.DISCONNECTED);
    }

    /**
     * 执行健康检查
     */
    private boolean performHealthCheck(McpConnection connection) {
        if (connection.getClient() == null) {
            connection.setStatus(McpConnectionStatus.ERROR);
            connection.setErrorMessage("客户端未初始化");
            return false;
        }

        try {
            long startTime = System.currentTimeMillis();
            connection.getClient().listTools();
            long responseTime = System.currentTimeMillis() - startTime;

            connection.getStatistics().recordSuccess(responseTime);
            connection.setStatus(McpConnectionStatus.CONNECTED);
            connection.setErrorMessage(null);
            return true;

        } catch (Exception e) {
            logger.warn("健康检查失败: {}", connection.getName(), e);
            connection.getStatistics().recordFailure();
            connection.setStatus(McpConnectionStatus.ERROR);
            connection.setErrorMessage(e.getMessage());

            // 尝试重连
            if (connection.getConfig().isAutoReconnect()) {
                scheduleReconnect(connection);
            }

            return false;
        }
    }

    /**
     * 启动健康检查任务
     */
    private void startHealthCheckTask() {
        Duration interval = config.getHealthCheckInterval();
        healthCheckTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                logger.debug("执行健康检查...");
                healthCheckAll();
            } catch (Exception e) {
                logger.error("健康检查任务执行失败", e);
            }
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 安排重连
     */
    private void scheduleReconnect(McpConnection connection) {
        int maxRetries = connection.getConfig().getMaxRetries();
        Duration retryInterval = connection.getConfig().getRetryInterval();

        scheduler.schedule(() -> {
            if (connection.getStatus() != McpConnectionStatus.CONNECTED) {
                logger.info("尝试重连: {}", connection.getName());
                disconnect(connection);
                connection.setStatus(McpConnectionStatus.CONNECTING);
                connection.setErrorMessage(null);
                connectAsync(connection);
            }
        }, retryInterval.toMillis(), TimeUnit.MILLISECONDS);
    }
}
