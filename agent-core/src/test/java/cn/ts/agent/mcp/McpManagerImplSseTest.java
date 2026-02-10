package cn.ts.agent.mcp;

import cn.ts.agent.mcp.config.McpManagerConfig;
import cn.ts.agent.mcp.model.McpConnectionConfig;
import cn.ts.agent.mcp.model.McpConnectionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doNothing;

/**
 * MCP 管理器 SSE 传输方式测试
 * <p>
 * 测试 SSE 类型的 MCP 连接配置和创建
 * </p>
 *
 * @author tianshuo
 */
class McpManagerImplSseTest {

    private McpManager mcpManager;
    private McpManagerConfig config;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        config = new McpManagerConfig();
        config.setHealthCheckInterval(Duration.ofMinutes(1));
        config.setEnableHealthCheck(true);

        eventPublisher = mock(ApplicationEventPublisher.class);
        doNothing().when(eventPublisher).publishEvent(any());

        mcpManager = new McpManagerImpl(config, eventPublisher);
        mcpManager.start();
    }

    @Test
    void testValidSseConfig() {
        McpConnectionConfig sseConfig = McpConnectionConfig.builder()
                .name("test-sse-server")
                .type(McpConnectionType.SSE)
                .description("测试 SSE 服务器")
                .sseConfig(McpConnectionConfig.SseConfig.builder()
                        .url("http://localhost:3000/sse")
                        .headers(new HashMap<>())
                        .build())
                .timeout(Duration.ofSeconds(30))
                .autoReconnect(true)
                .maxRetries(3)
                .retryInterval(Duration.ofSeconds(5))
                .build();

        boolean result = mcpManager.registerConnection(sseConfig);

        assertTrue(result, "SSE 连接注册应该成功");
        assertTrue(mcpManager.getConnection("test-sse-server").isPresent(),
                "应该能找到已注册的 SSE 连接");
    }

    @Test
    void testSseConfigWithMissingUrl() {
        McpConnectionConfig sseConfig = McpConnectionConfig.builder()
                .name("test-sse-server-no-url")
                .type(McpConnectionType.SSE)
                .description("测试缺少 URL 的 SSE 配置")
                .sseConfig(McpConnectionConfig.SseConfig.builder()
                        .url(null)
                        .headers(new HashMap<>())
                        .build())
                .timeout(Duration.ofSeconds(30))
                .autoReconnect(true)
                .maxRetries(3)
                .retryInterval(Duration.ofSeconds(5))
                .build();

        boolean result = mcpManager.registerConnection(sseConfig);

        assertTrue(result, "连接注册应该成功（异步连接）");
        // 注意：由于是异步连接，URL 验证会在连接过程中进行
    }

    @Test
    void testSseConfigWithEmptyUrl() {
        McpConnectionConfig sseConfig = McpConnectionConfig.builder()
                .name("test-sse-server-empty-url")
                .type(McpConnectionType.SSE)
                .description("测试空 URL 的 SSE 配置")
                .sseConfig(McpConnectionConfig.SseConfig.builder()
                        .url("   ")
                        .headers(new HashMap<>())
                        .build())
                .timeout(Duration.ofSeconds(30))
                .autoReconnect(true)
                .maxRetries(3)
                .retryInterval(Duration.ofSeconds(5))
                .build();

        boolean result = mcpManager.registerConnection(sseConfig);

        assertTrue(result, "连接注册应该成功（异步连接）");
        // 注意：由于是异步连接，URL 验证会在连接过程中进行
    }

    @Test
    void testSseConfigWithHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer test-token");
        headers.put("Content-Type", "application/json");

        McpConnectionConfig sseConfig = McpConnectionConfig.builder()
                .name("test-sse-server-with-headers")
                .type(McpConnectionType.SSE)
                .description("测试带请求头的 SSE 配置")
                .sseConfig(McpConnectionConfig.SseConfig.builder()
                        .url("http://localhost:3000/sse")
                        .headers(headers)
                        .build())
                .timeout(Duration.ofSeconds(30))
                .autoReconnect(true)
                .maxRetries(3)
                .retryInterval(Duration.ofSeconds(5))
                .build();

        boolean result = mcpManager.registerConnection(sseConfig);

        assertTrue(result, "带请求头的 SSE 连接注册应该成功");
        assertTrue(mcpManager.getConnection("test-sse-server-with-headers").isPresent(),
                "应该能找到已注册的带请求头的 SSE 连接");
    }

    @Test
    void testDuplicateSseConnectionName() {
        McpConnectionConfig sseConfig1 = McpConnectionConfig.builder()
                .name("duplicate-sse-server")
                .type(McpConnectionType.SSE)
                .description("第一个 SSE 服务器")
                .sseConfig(McpConnectionConfig.SseConfig.builder()
                        .url("http://localhost:3000/sse")
                        .headers(new HashMap<>())
                        .build())
                .timeout(Duration.ofSeconds(30))
                .autoReconnect(true)
                .maxRetries(3)
                .retryInterval(Duration.ofSeconds(5))
                .build();

        McpConnectionConfig sseConfig2 = McpConnectionConfig.builder()
                .name("duplicate-sse-server")
                .type(McpConnectionType.SSE)
                .description("第二个 SSE 服务器")
                .sseConfig(McpConnectionConfig.SseConfig.builder()
                        .url("http://localhost:3001/sse")
                        .headers(new HashMap<>())
                        .build())
                .timeout(Duration.ofSeconds(30))
                .autoReconnect(true)
                .maxRetries(3)
                .retryInterval(Duration.ofSeconds(5))
                .build();

        boolean result1 = mcpManager.registerConnection(sseConfig1);
        assertTrue(result1, "第一个 SSE 连接注册应该成功");

        // 重复名称应该抛出 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            mcpManager.registerConnection(sseConfig2);
        }, "重复名称的 SSE 连接注册应该抛出 IllegalArgumentException");
    }

    @Test
    void testHttpTypeNotSupported() {
        McpConnectionConfig httpConfig = McpConnectionConfig.builder()
                .name("test-http-server")
                .type(McpConnectionType.HTTP)
                .description("测试 HTTP 服务器（暂不支持）")
                .httpConfig(McpConnectionConfig.HttpConfig.builder()
                        .url("http://localhost:3000/api")
                        .headers(new HashMap<>())
                        .method("POST")
                        .build())
                .timeout(Duration.ofSeconds(30))
                .autoReconnect(true)
                .maxRetries(3)
                .retryInterval(Duration.ofSeconds(5))
                .build();

        boolean result = mcpManager.registerConnection(httpConfig);

        // 注册会成功，但连接会失败
        assertTrue(result, "HTTP 连接注册应该成功（但连接会失败）");
    }

    @Test
    void testMixedStdioAndSseConnections() {
        // STDIO 配置
        McpConnectionConfig stdioConfig = McpConnectionConfig.builder()
                .name("test-stdio-server")
                .type(McpConnectionType.STDIO)
                .description("测试 STDIO 服务器")
                .stdioConfig(McpConnectionConfig.StdioConfig.builder()
                        .command("node")
                        .args(java.util.List.of("server.js"))
                        .env(new HashMap<>())
                        .build())
                .timeout(Duration.ofSeconds(30))
                .autoReconnect(true)
                .maxRetries(3)
                .retryInterval(Duration.ofSeconds(5))
                .build();

        // SSE 配置
        McpConnectionConfig sseConfig = McpConnectionConfig.builder()
                .name("test-sse-server")
                .type(McpConnectionType.SSE)
                .description("测试 SSE 服务器")
                .sseConfig(McpConnectionConfig.SseConfig.builder()
                        .url("http://localhost:3000/sse")
                        .headers(new HashMap<>())
                        .build())
                .timeout(Duration.ofSeconds(30))
                .autoReconnect(true)
                .maxRetries(3)
                .retryInterval(Duration.ofSeconds(5))
                .build();

        boolean result1 = mcpManager.registerConnection(stdioConfig);
        boolean result2 = mcpManager.registerConnection(sseConfig);

        assertTrue(result1, "STDIO 连接注册应该成功");
        assertTrue(result2, "SSE 连接注册应该成功");
        assertEquals(2, mcpManager.getAllConnections().size(),
                "应该有 2 个连接");
    }
}
