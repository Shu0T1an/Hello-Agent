package cn.ts.web.service;

import cn.ts.graph.CompiledGraph;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.web.config.AgentExecutionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 会话管理功能测试
 * <p>
 * 测试 AgentExecutionService 的会话管理功能，包括：
 * - sessionId 传递到 RunnableConfig
 * - 同一会话的状态保持
 * - 不同会话的隔离
 * </p>
 */
class SessionManagementTest {

    private AgentExecutionService service;
    private CompiledGraph mockGraph;
    private AgentExecutionConfig mockConfig;

    @BeforeEach
    void setUp() {
        // Mock 依赖
        SessionService mockSessionService = mock(SessionService.class);
        CheckpointManager mockCheckpointManager = mock(CheckpointManager.class);
        mockConfig = mock(AgentExecutionConfig.class);
        MessageConversionService mockMessageConversionService = mock(MessageConversionService.class);
        AgentRegistry mockAgentRegistry = mock(AgentRegistry.class);
        AgentResponseBuilder mockResponseBuilder = mock(AgentResponseBuilder.class);

        // 设置 mock 配置行为
        when(mockConfig.getTimeout()).thenReturn(java.time.Duration.ofSeconds(300));
        when(mockConfig.getHeartbeatInterval()).thenReturn(java.time.Duration.ofSeconds(30));
        when(mockConfig.getMaxTitleLength()).thenReturn(15);
        when(mockConfig.getDefaultMaxIterations()).thenReturn(10);
        when(mockConfig.isDebugMode()).thenReturn(false);

        service = new AgentExecutionService(mockAgentRegistry, mockSessionService, mockCheckpointManager,
                mockConfig, mockMessageConversionService, mockResponseBuilder);
        mockGraph = mock(CompiledGraph.class);

        // 设置 AgentRegistry mock 行为
        when(mockAgentRegistry.get("testAgent")).thenReturn(mockGraph);
        when(mockAgentRegistry.isRegistered("testAgent")).thenReturn(true);
    }

    @Test
    void testSessionIdPassedToConfig() {
        // 准备测试数据
        Map<String, Object> initialState = new HashMap<>();
        initialState.put("input", "test input");
        String sessionId = "test-session-123";

        // 模拟 graph.stream 返回空流
        when(mockGraph.stream(any(), any(RunnableConfig.class)))
                .thenReturn(Flux.empty());

        // 执行测试
        service.executeAgentStreamWithSession("testAgent", initialState, sessionId, mockConfig.getTimeout())
                .collectList()
                .block();

        // 验证 RunnableConfig 包含正确的 threadId
        ArgumentCaptor<RunnableConfig> configCaptor = ArgumentCaptor.forClass(RunnableConfig.class);
        verify(mockGraph).stream(eq(initialState), configCaptor.capture());

        RunnableConfig capturedConfig = configCaptor.getValue();
        assertEquals(sessionId, capturedConfig.threadId(),
                "sessionId 应该被传递到 RunnableConfig.threadId()");
    }

    @Test
    void testEmptySessionIdNotSet() {
        // 准备测试数据
        Map<String, Object> initialState = new HashMap<>();
        String emptySessionId = "";

        // 模拟 graph.stream 返回空流
        when(mockGraph.stream(any(), any(RunnableConfig.class)))
                .thenReturn(Flux.empty());

        // 执行测试
        service.executeAgentStreamWithSession("testAgent", initialState, emptySessionId, mockConfig.getTimeout())
                .collectList()
                .block();

        // 验证 RunnableConfig 不包含 threadId
        ArgumentCaptor<RunnableConfig> configCaptor = ArgumentCaptor.forClass(RunnableConfig.class);
        verify(mockGraph).stream(eq(initialState), configCaptor.capture());

        RunnableConfig capturedConfig = configCaptor.getValue();
        assertNull(capturedConfig.threadId(),
                "空 sessionId 不应该被设置到 RunnableConfig.threadId()");
    }

    @Test
    void testDifferentSessionsHaveDifferentThreadIds() {
        // 准备测试数据
        Map<String, Object> initialState = new HashMap<>();
        String sessionId1 = "session-1";
        String sessionId2 = "session-2";

        // 模拟 graph.stream 返回空流
        when(mockGraph.stream(any(), any(RunnableConfig.class)))
                .thenReturn(Flux.empty());

        // 执行第一个会话
        service.executeAgentStreamWithSession("testAgent", initialState, sessionId1, mockConfig.getTimeout())
                .collectList()
                .block();

        // 执行第二个会话
        service.executeAgentStreamWithSession("testAgent", initialState, sessionId2, mockConfig.getTimeout())
                .collectList()
                .block();

        // 验证两次调用使用了不同的 threadId
        ArgumentCaptor<RunnableConfig> configCaptor = ArgumentCaptor.forClass(RunnableConfig.class);
        verify(mockGraph, times(2)).stream(eq(initialState), configCaptor.capture());

        List<RunnableConfig> capturedConfigs = configCaptor.getAllValues();
        assertEquals(sessionId1, capturedConfigs.get(0).threadId());
        assertEquals(sessionId2, capturedConfigs.get(1).threadId());
        assertNotEquals(capturedConfigs.get(0).threadId(), capturedConfigs.get(1).threadId());
    }

    @Test
    void testAgentNotFound() {
        // 准备测试数据
        Map<String, Object> initialState = new HashMap<>();
        String sessionId = "test-session";

        // 执行测试 - Agent 不存在，捕获异常
        Throwable thrown = assertThrows(IllegalArgumentException.class, () -> {
            service.executeAgentStreamWithSession("nonExistentAgent", initialState, sessionId, mockConfig.getTimeout())
                    .blockLast();
        });

        // 验证错误消息
        assertTrue(thrown.getMessage().contains("Agent not found"), "错误消息应该包含 'Agent not found'");
    }

    @Test
    void testExecutionIdGeneratedForEachCall() {
        // 准备测试数据
        Map<String, Object> initialState = new HashMap<>();
        String sessionId = "test-session";

        // 模拟 graph.stream 返回测试数据
        when(mockGraph.stream(any(), any(RunnableConfig.class)))
                .thenReturn(Flux.empty());

        // 执行两次调用
        service.executeAgentStreamWithSession("testAgent", initialState, sessionId, mockConfig.getTimeout())
                .collectList()
                .block();

        service.executeAgentStreamWithSession("testAgent", initialState, sessionId, mockConfig.getTimeout())
                .collectList()
                .block();

        // 验证每次调用都生成了不同的 executionId
        ArgumentCaptor<RunnableConfig> configCaptor = ArgumentCaptor.forClass(RunnableConfig.class);
        verify(mockGraph, times(2)).stream(eq(initialState), configCaptor.capture());

        List<RunnableConfig> capturedConfigs = configCaptor.getAllValues();
        assertNotNull(capturedConfigs.get(0).executionId());
        assertNotNull(capturedConfigs.get(1).executionId());
        assertNotEquals(capturedConfigs.get(0).executionId(), capturedConfigs.get(1).executionId(),
                "每次调用应该生成不同的 executionId");
    }

    @Test
    void testAgentRegistration() {
        // 测试 Agent 注册
        assertTrue(service.isAgentRegistered("testAgent"), "testAgent 应该已注册");
        assertFalse(service.isAgentRegistered("nonExistentAgent"), "未注册的 Agent 应该返回 false");

        // 测试获取所有已注册的 Agent
        assertEquals(1, service.getRegisteredAgents().size(), "应该有1个已注册的 Agent");
        assertTrue(service.getRegisteredAgents().contains("testAgent"), "已注册列表应该包含 testAgent");

        // 测试注销 Agent
        service.unregisterAgent("testAgent");
        assertFalse(service.isAgentRegistered("testAgent"), "注销后 testAgent 不应该存在");
    }
}
