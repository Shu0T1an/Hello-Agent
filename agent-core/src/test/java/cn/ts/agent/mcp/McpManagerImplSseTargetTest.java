package cn.ts.agent.mcp;

import cn.ts.agent.mcp.config.McpManagerConfig;
import cn.ts.agent.mcp.model.McpConnectionConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class McpManagerImplSseTargetTest {

    private final McpManagerImpl manager = new McpManagerImpl(new McpManagerConfig(), mock(ApplicationEventPublisher.class));

    @Test
    void shouldResolveBaseUrlAndEndpointFromFullSseUrl() {
        McpConnectionConfig.SseConfig sse = McpConnectionConfig.SseConfig.builder()
                .url("http://localhost:18080/mcp/sse")
                .headers(Map.of())
                .build();

        McpManagerImpl.SseTarget target = manager.resolveSseTarget(sse);

        assertEquals("http://localhost:18080", target.baseUrl());
        assertEquals("/mcp/sse", target.sseEndpoint());
    }

    @Test
    void shouldPreferExplicitBaseUrlAndEndpoint() {
        McpConnectionConfig.SseConfig sse = McpConnectionConfig.SseConfig.builder()
                .baseUrl("http://localhost:18080")
                .sseEndpoint("/mcp/sse")
                .headers(Map.of())
                .build();

        McpManagerImpl.SseTarget target = manager.resolveSseTarget(sse);

        assertEquals("http://localhost:18080", target.baseUrl());
        assertEquals("/mcp/sse", target.sseEndpoint());
    }

    @Test
    void shouldUseDefaultSseEndpointWhenOnlyBaseUrlProvided() {
        McpConnectionConfig.SseConfig sse = McpConnectionConfig.SseConfig.builder()
                .baseUrl("http://localhost:18080")
                .headers(Map.of())
                .build();

        McpManagerImpl.SseTarget target = manager.resolveSseTarget(sse);

        assertEquals("http://localhost:18080", target.baseUrl());
        assertEquals("/sse", target.sseEndpoint());
    }
}
