package cn.ts.web.service;

import cn.ts.agent.constant.EventConstants;
import cn.ts.agent.constant.StateKeys;
import cn.ts.graph.GraphResponse;
import cn.ts.graph.NodeOutput;
import cn.ts.graph.state.MapState;
import cn.ts.web.shared.constant.ApiConstants;
import cn.ts.web.dto.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * AgentResponseBuilder 测试
 * <p>
 * 验证 Agent 响应构建器的正确性
 * </p>
 *
 * @author tianshuo
 */
class AgentResponseBuilderTest {

    private AgentResponseBuilder builder;

    @BeforeEach
    void setUp() {
        MessageConversionService messageConversionService = mock(MessageConversionService.class);
        builder = new AgentResponseBuilder(messageConversionService);
    }

    @Test
    void testBuildErrorResponse_429() {
        AgentResponse response = builder.buildErrorResponse("429 Too Many Requests", "exec-123");

        assertEquals(EventConstants.RATE_LIMIT, response.getEventType());
        assertEquals(ApiConstants.ErrorMessages.RATE_LIMIT_EXCEEDED, response.getMessage());
    }

    @Test
    void testBuildErrorResponse_503() {
        AgentResponse response = builder.buildErrorResponse("503 Service Unavailable", "exec-123");

        assertEquals(EventConstants.SERVICE_UNAVAILABLE, response.getEventType());
        assertEquals(ApiConstants.ErrorMessages.SERVICE_UNAVAILABLE_MSG, response.getMessage());
    }

    @Test
    void testBuildErrorResponse_401() {
        AgentResponse response = builder.buildErrorResponse("401 Unauthorized", "exec-123");

        assertEquals(EventConstants.AUTH_FAILED, response.getEventType());
        assertEquals(ApiConstants.ErrorMessages.AUTH_FAILED_MSG, response.getMessage());
    }

    @Test
    void testBuildErrorResponse_GenericError() {
        String errorMessage = "Something went wrong";
        AgentResponse response = builder.buildErrorResponse(errorMessage, "exec-123");

        assertEquals(EventConstants.ERROR, response.getEventType());
        assertTrue(response.getMessage().contains(errorMessage));
    }

    @Test
    void testBuildErrorResponse_WebClient429() {
        AgentResponse response = builder.buildErrorResponse("WebClientResponseException: status 429", "exec-123");

        assertEquals(EventConstants.RATE_LIMIT, response.getEventType());
        assertEquals(ApiConstants.ErrorMessages.RATE_LIMIT_EXCEEDED, response.getMessage());
    }

    @Test
    void testBuildErrorResponse_WebClient503() {
        AgentResponse response = builder.buildErrorResponse("WebClientResponseException: status 503", "exec-123");

        assertEquals(EventConstants.SERVICE_UNAVAILABLE, response.getEventType());
        assertEquals(ApiConstants.ErrorMessages.SERVICE_UNAVAILABLE_MSG, response.getMessage());
    }

    @Test
    void testBuildErrorResponse_WebClient401() {
        AgentResponse response = builder.buildErrorResponse("WebClientResponseException: status 401", "exec-123");

        assertEquals(EventConstants.AUTH_FAILED, response.getEventType());
        assertEquals(ApiConstants.ErrorMessages.AUTH_FAILED_MSG, response.getMessage());
    }

    @Test
    void testBuildErrorResponse_WebClientGeneric() {
        AgentResponse response = builder.buildErrorResponse("WebClientResponseException: status 500", "exec-123");

        assertEquals(EventConstants.API_ERROR, response.getEventType());
        assertEquals(ApiConstants.ErrorMessages.API_ERROR_MSG, response.getMessage());
    }

    @Test
    void testBuildErrorResponse_NullMessage() {
        AgentResponse response = builder.buildErrorResponse(null, "exec-123");

        assertEquals(EventConstants.ERROR, response.getEventType());
        assertEquals(ApiConstants.ErrorMessages.EXECUTION_ERROR, response.getMessage());
    }

    @Test
    void testBuildErrorResponse_ExecutionId() {
        AgentResponse response = builder.buildErrorResponse("Test error", "test-exec-456");

        assertEquals("test-exec-456", response.getExecutionId());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testBuildErrorResponse_ErrorField() {
        String errorMessage = "Detailed error message";
        AgentResponse response = builder.buildErrorResponse(errorMessage, "exec-123");

        assertEquals(errorMessage, response.getError());
    }

    @Test
    void testBuildErrorResponse_TooManyRequests() {
        AgentResponse response = builder.buildErrorResponse("Too Many Requests", "exec-123");

        assertEquals(EventConstants.RATE_LIMIT, response.getEventType());
        assertEquals(ApiConstants.ErrorMessages.RATE_LIMIT_EXCEEDED, response.getMessage());
    }

    @Test
    void testBuildErrorResponse_ServiceUnavailable() {
        AgentResponse response = builder.buildErrorResponse("Service Unavailable", "exec-123");

        assertEquals(EventConstants.SERVICE_UNAVAILABLE, response.getEventType());
        assertEquals(ApiConstants.ErrorMessages.SERVICE_UNAVAILABLE_MSG, response.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testBuild_ForwardsTodoStateKeys() {
        Map<String, Object> stateData = Map.of(
                "execution_record", Map.of("executions", List.of(Map.of("id", "call-1"))),
                StateKeys.TODOS, List.of(Map.of("id", "todo-1", "content", "write tests", "status", "pending")),
                StateKeys.TODOS_META, Map.of("version", 3L, "lastOperation", "upsert_todos")
        );
        NodeOutput output = NodeOutput.of("_AGENT_TOOL_", null, new MapState(stateData));
        GraphResponse<NodeOutput> response = GraphResponse.of("_AGENT_TOOL_", output);

        AgentResponse result = builder.build(response, "exec-1");
        Map<String, Object> payload = result.getStateData();

        assertNotNull(payload);
        assertTrue(payload.containsKey("execution_record"));
        assertTrue(payload.containsKey(StateKeys.TODOS));
        assertTrue(payload.containsKey(StateKeys.TODOS_META));

        Map<String, Object> meta = (Map<String, Object>) payload.get(StateKeys.TODOS_META);
        assertEquals(3L, ((Number) meta.get("version")).longValue());
    }
}
