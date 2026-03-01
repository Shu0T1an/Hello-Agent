package cn.ts.agent.mcp;

import cn.ts.agent.mcp.config.McpManagerConfig;
import cn.ts.agent.mcp.model.McpConnection;
import cn.ts.agent.mcp.model.McpConnectionConfig;
import cn.ts.agent.mcp.model.McpConnectionStatus;
import cn.ts.agent.mcp.model.McpConnectionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.HashMap;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class McpManagerImplLifecycleTest {

    private McpManager manager;

    @BeforeEach
    void setUp() {
        McpManagerConfig config = new McpManagerConfig();
        config.setEnableHealthCheck(false);
        config.setHealthCheckInterval(Duration.ofMinutes(1));

        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        doNothing().when(eventPublisher).publishEvent(any());

        manager = new McpManagerImpl(config, eventPublisher);
        manager.start();
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.stop();
        }
    }

    @Test
    void httpConnectionShouldFailWithReadableErrorInsteadOfNpe() {
        boolean registered = manager.registerConnection(buildHttpConfig("http-null-guard"));
        assertTrue(registered, "HTTP 连接注册应成功，连接失败应在异步阶段处理");

        waitUntil(() -> manager.getConnection("http-null-guard")
                .map(connection -> connection.getStatus() == McpConnectionStatus.ERROR)
                .orElse(false));

        McpConnection connection = manager.getConnection("http-null-guard").orElseThrow();
        assertEquals(McpConnectionStatus.ERROR, connection.getStatus());
        assertNotNull(connection.getErrorMessage());
        assertTrue(connection.getErrorMessage().contains("HTTP transport is not implemented"));
        assertFalse(connection.getErrorMessage().contains("Cannot invoke"),
                "不应暴露 createMcpClient 返回 null 导致的 NPE 信息");
    }

    @Test
    void shouldStillAllowRegisterAfterStopAndStart() {
        manager.stop();
        manager.start();

        boolean registered = manager.registerConnection(buildHttpConfig("http-after-restart"));
        assertTrue(registered, "stop/start 后应仍可注册新连接");
        assertTrue(manager.getConnection("http-after-restart").isPresent());
    }

    private McpConnectionConfig buildHttpConfig(String name) {
        return McpConnectionConfig.builder()
                .name(name)
                .type(McpConnectionType.HTTP)
                .httpConfig(McpConnectionConfig.HttpConfig.builder()
                        .url("http://localhost:3000/api")
                        .headers(new HashMap<>())
                        .method("POST")
                        .build())
                .timeout(Duration.ofSeconds(5))
                .autoReconnect(false)
                .build();
    }

    private void waitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("等待异步状态时被中断");
            }
        }
        fail("等待异步状态超时");
    }
}

