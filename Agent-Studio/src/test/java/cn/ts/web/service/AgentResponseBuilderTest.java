package cn.ts.web.service;

import cn.ts.agent.constant.EventConstants;
import cn.ts.web.constant.ApiConstants;
import cn.ts.web.dto.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
