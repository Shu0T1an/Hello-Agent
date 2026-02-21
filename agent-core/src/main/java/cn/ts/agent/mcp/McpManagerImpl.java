package cn.ts.agent.mcp;

import cn.ts.agent.mcp.config.McpManagerConfig;
import cn.ts.agent.mcp.event.McpConnectionEvent;
import cn.ts.agent.mcp.model.McpConnection;
import cn.ts.agent.mcp.model.McpConnectionConfig;
import cn.ts.agent.mcp.model.McpConnectionStatus;
import cn.ts.agent.mcp.model.McpConnectionType;
import cn.ts.agent.mcp.model.McpStatistics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * MCP 绠＄悊鍣ㄥ疄鐜扮被
 * <p>
 * 璐熻矗 MCP 杩炴帴鐨勭敓鍛藉懆鏈熺鐞嗭紝鍖呮嫭鍒涘缓銆佽繛鎺ャ€佸仴搴锋鏌ュ拰鑷姩閲嶈繛
 * </p>
 *
 * @author tianshuo
 */
public class McpManagerImpl implements McpManager {

    private static final Logger logger = LoggerFactory.getLogger(McpManagerImpl.class);

    private final McpManagerConfig config;
    private final Map<String, McpConnection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final ExecutorService connectionExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ApplicationEventPublisher eventPublisher;
    private ScheduledFuture<?> healthCheckTask;

    public McpManagerImpl(McpManagerConfig config, ApplicationEventPublisher eventPublisher) {
        this.config = config;
        this.eventPublisher = eventPublisher;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "mcp-manager-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        this.connectionExecutor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "mcp-manager-connector");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 鍙戝竷 MCP 杩炴帴浜嬩欢
     */
    private void publishEvent(McpConnectionEvent event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            logger.warn("Failed to publish MCP connection event: {}", event, e);
        }
    }

    @Override
    public boolean registerConnection(McpConnectionConfig config) {
        if (!StringUtils.hasText(config.getName())) {
            throw new IllegalArgumentException("杩炴帴鍚嶇О涓嶈兘涓虹┖");
        }
        if (connections.containsKey(config.getName())) {
            throw new IllegalArgumentException("杩炴帴鍚嶇О宸插瓨鍦? " + config.getName());
        }

        try {
            McpConnection connection = new McpConnection();
            connection.setName(config.getName());
            connection.setConfig(config);
            connection.setStatus(McpConnectionStatus.CONNECTING);

            connections.put(config.getName(), connection);

            // 寮傛杩炴帴
            connectAsync(connection);

            logger.info("MCP 杩炴帴娉ㄥ唽鎴愬姛: {}, 绫诲瀷: {}", config.getName(), config.getType());
            return true;
        } catch (Exception e) {
            logger.error("MCP 杩炴帴娉ㄥ唽澶辫触: {}", config.getName(), e);
            connections.remove(config.getName());
            return false;
        }
    }

    @Override
    public void unregisterConnection(String name) {
        McpConnection connection = connections.remove(name);
        if (connection == null) {
            throw new IllegalArgumentException("杩炴帴涓嶅瓨鍦? " + name);
        }

        disconnect(connection);
        logger.info("MCP 杩炴帴宸叉敞閿€: {}", name);
    }

    @Override
    public void updateConnection(String name, McpConnectionConfig newConfig) {
        McpConnection connection = connections.get(name);
        if (connection == null) {
            throw new IllegalArgumentException("杩炴帴涓嶅瓨鍦? " + name);
        }

        // 鍏堟柇寮€鏃ц繛鎺?
        disconnect(connection);

        // 鏇存柊閰嶇疆
        connection.setConfig(newConfig);
        connection.setStatus(McpConnectionStatus.CONNECTING);
        connection.setErrorMessage(null);

        // 閲嶆柊杩炴帴
        connectAsync(connection);

        logger.info("MCP 杩炴帴宸叉洿鏂? {}", name);
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
        // TODO: 绛夊緟 Spring AI MCP API 绋冲畾鍚庡疄鐜?
        // 鏆傛椂杩斿洖绌烘暟缁?
        return new ToolCallback[0];
    }

    @Override
    public boolean healthCheck(String name) {
        McpConnection connection = connections.get(name);
        if (connection == null) {
            logger.warn("鍋ュ悍妫€鏌ュけ璐? 杩炴帴涓嶅瓨鍦?- {}", name);
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
            throw new IllegalArgumentException("杩炴帴涓嶅瓨鍦? " + name);
        }

        logger.info("鎵嬪姩閲嶈繛: {}", name);
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

            // 鑾峰彇宸ュ叿鏁伴噺
            int toolCount = 0;
            if (connection.getClient() != null) {
                try {
                    var tools = connection.getClient().listTools();
                    if (tools != null) {
                        toolCount = tools.tools().size();
                    }
                } catch (Exception e) {
                    logger.debug("鑾峰彇宸ュ叿鏁伴噺澶辫触: {}", connection.getName(), e);
                }
            }
            totalTools += toolCount;

            // 鏋勫缓杩炴帴缁熻淇℃伅
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
            logger.info("MCP 绠＄悊鍣ㄥ惎鍔ㄤ腑...");

            // 鍚姩鍋ュ悍妫€鏌ヤ换鍔?
            if (config.isEnableHealthCheck()) {
                startHealthCheckTask();
            }

            logger.info("MCP 绠＄悊鍣ㄥ凡鍚姩");
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("MCP 绠＄悊鍣ㄥ仠姝腑...");

            // 鍋滄鍋ュ悍妫€鏌ヤ换鍔?
            if (healthCheckTask != null) {
                healthCheckTask.cancel(false);
                healthCheckTask = null;
            }

            // 鍏抽棴鎵€鏈夎繛鎺?
            for (McpConnection connection : connections.values()) {
                disconnect(connection);
            }

            // 鍏抽棴璋冨害鍣?
            scheduler.shutdown();
            connectionExecutor.shutdownNow();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }

            logger.info("MCP 绠＄悊鍣ㄥ凡鍋滄");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 寮傛杩炴帴
     */
    private void connectAsync(McpConnection connection) {
        Duration timeout = resolveConnectionTimeout(connection);
        CompletableFuture.runAsync(() -> connectWithTimeout(connection, timeout), connectionExecutor)
                .exceptionally(ex -> {
                    handleConnectionFailure(connection, unwrapCompletionException(ex));
                    return null;
                });
    }

    private void connectWithTimeout(McpConnection connection, Duration timeout) {
        Future<?> future = connectionExecutor.submit(() -> connect(connection));
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("MCP connection timeout after " + timeout.toSeconds() + "s", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("MCP connection interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        }
    }

    private Duration resolveConnectionTimeout(McpConnection connection) {
        Duration timeout = connection.getConfig().getTimeout();
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = config.getDefaultTimeout();
        }
        return (timeout == null || timeout.isNegative() || timeout.isZero()) ? Duration.ofSeconds(30) : timeout;
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        if ((throwable instanceof CompletionException || throwable instanceof ExecutionException)
                && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private void handleConnectionFailure(McpConnection connection, Throwable throwable) {
        String errorMessage = throwable == null ? "Unknown MCP connection error" : throwable.getMessage();
        logger.error("Connection failed: {}", connection.getName(), throwable);
        connection.setStatus(McpConnectionStatus.ERROR);
        connection.setErrorMessage(errorMessage);

        publishEvent(McpConnectionEvent.error(this, connection.getName(), errorMessage));

        if (connection.getConfig().isAutoReconnect()) {
            scheduleReconnect(connection);
        }
    }
    private void connect(McpConnection connection) {
        McpConnectionConfig config = connection.getConfig();
        McpSyncClient client = createMcpClient(config);
        client.initialize();
        logger.info("MCP client initialized");

        connection.setClient(client);
        connection.setStatus(McpConnectionStatus.CONNECTED);
        connection.setConnectedAt(Instant.now());
        connection.setErrorMessage(null);

        logger.info("MCP 杩炴帴鎴愬姛: {}, 绫诲瀷: {}", config.getName(), config.getType());

        // 鍙戝竷杩炴帴鎴愬姛浜嬩欢
        publishEvent(McpConnectionEvent.connected(this, config.getName()));
    }

    /**
     * 鍒涘缓 MCP 瀹㈡埛绔?
     * <p>
     * 鏍规嵁杩炴帴绫诲瀷鍒涘缓瀵瑰簲鐨?MCP 瀹㈡埛绔細
     * - STDIO: 鏍囧噯杈撳叆杈撳嚭杩炴帴
     * - SSE: Server-Sent Events 杩炴帴
     * - HTTP: HTTP 杩炴帴锛堟殏涓嶆敮鎸侊級
     * </p>
     */
    private McpSyncClient createMcpClient(McpConnectionConfig config) {
        ObjectMapper objectMapper = new ObjectMapper();

        return switch (config.getType()) {
            case STDIO -> {
                if (config.getStdioConfig() == null) {
                    throw new IllegalArgumentException("STDIO 閰嶇疆涓嶈兘涓虹┖");
                }
                ServerParameters params = ServerParameters
                        .builder(config.getStdioConfig().getCommand())
                        .args(config.getStdioConfig().getArgs())
                        .env(config.getStdioConfig().getEnv())
                        .build();
                McpClientTransport transport = new StdioClientTransport(params, new JacksonMcpJsonMapper(objectMapper));
                yield McpClient.sync(transport).build();
            }

            case SSE -> {
                if (config.getSseConfig() == null) {
                    throw new IllegalArgumentException("SSE 閰嶇疆涓嶈兘涓虹┖");
                }
                String url = config.getSseConfig().getUrl();
                if (url == null || url.isBlank()) {
                    throw new IllegalArgumentException("SSE URL 涓嶈兘涓虹┖");
                }
                try {
                    logger.info("鍒涘缓 SSE 瀹㈡埛绔紝URL: {}", url);
                    // 浣跨敤 Builder 鏂瑰紡鍒涘缓 SSE 浼犺緭灞?
                    McpClientTransport transport = HttpClientSseClientTransport.builder(url)
                            .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                            .build();
                    McpSyncClient client = McpClient.sync(transport).build();
                    logger.info("SSE client created successfully");
                    yield client;
                } catch (Exception e) {
                    logger.error("鍒涘缓 SSE 瀹㈡埛绔け璐? {}", url, e);
                    throw new RuntimeException("鍒涘缓 SSE 瀹㈡埛绔け璐? " + e.getMessage(), e);
                }
            }

            case HTTP -> {
                logger.warn("HTTP 绫诲瀷鏆備笉鏀寔锛岃浣跨敤 SSE 绫诲瀷");
                yield null;
            }
        };
    }

    /**
     * 鏂紑杩炴帴
     */
    private void disconnect(McpConnection connection) {
        if (connection.getClient() != null) {
            try {
                connection.getClient().close();
            } catch (Exception e) {
                logger.warn("鍏抽棴 MCP 瀹㈡埛绔け璐? {}", connection.getName(), e);
            }
            connection.setClient(null);
        }
        connection.setStatus(McpConnectionStatus.DISCONNECTED);

        // 鍙戝竷鏂紑杩炴帴浜嬩欢
        publishEvent(McpConnectionEvent.disconnected(this, connection.getName()));
    }

    /**
     * 鎵ц鍋ュ悍妫€鏌?
     */
    private boolean performHealthCheck(McpConnection connection) {
        if (connection.getClient() == null) {
            connection.setStatus(McpConnectionStatus.ERROR);
            connection.setErrorMessage("MCP client not initialized");

            // 鍙戝竷閿欒浜嬩欢
            publishEvent(McpConnectionEvent.error(this, connection.getName(), "MCP client not initialized"));

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
            logger.warn("鍋ュ悍妫€鏌ュけ璐? {}", connection.getName(), e);
            connection.getStatistics().recordFailure();
            connection.setStatus(McpConnectionStatus.ERROR);
            connection.setErrorMessage(e.getMessage());

            // 鍙戝竷閿欒浜嬩欢
            publishEvent(McpConnectionEvent.error(this, connection.getName(), e.getMessage()));

            // 灏濊瘯閲嶈繛
            if (connection.getConfig().isAutoReconnect()) {
                scheduleReconnect(connection);
            }

            return false;
        }
    }

    /**
     * 鍚姩鍋ュ悍妫€鏌ヤ换鍔?
     */
    private void startHealthCheckTask() {
        Duration interval = config.getHealthCheckInterval();
        healthCheckTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                logger.debug("鎵ц鍋ュ悍妫€鏌?..");
                healthCheckAll();
            } catch (Exception e) {
                logger.error("Health check task execution failed", e);
            }
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 瀹夋帓閲嶈繛
     */
    private void scheduleReconnect(McpConnection connection) {
        int maxRetries = connection.getConfig().getMaxRetries();
        Duration retryInterval = connection.getConfig().getRetryInterval();

        scheduler.schedule(() -> {
            if (connection.getStatus() != McpConnectionStatus.CONNECTED) {
                logger.info("灏濊瘯閲嶈繛: {}", connection.getName());
                disconnect(connection);
                connection.setStatus(McpConnectionStatus.CONNECTING);
                connection.setErrorMessage(null);
                connectAsync(connection);
            }
        }, retryInterval.toMillis(), TimeUnit.MILLISECONDS);
    }
}

