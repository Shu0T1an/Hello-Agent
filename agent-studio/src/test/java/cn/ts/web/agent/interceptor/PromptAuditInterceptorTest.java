package cn.ts.web.agent.interceptor;

import cn.ts.agent.interceptor.ModelInvocationContext;
import cn.ts.agent.interceptor.ModelInvocationResult;
import cn.ts.agent.model.ChatModelRequest;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.flux.GraphFlux;
import cn.ts.graph.state.State;
import cn.ts.graph.util.StateTemplates;
import cn.ts.web.infra.audit.entity.LlmPromptAuditEntity;
import cn.ts.web.infra.audit.service.LlmPromptAuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PromptAuditInterceptorTest {

    @Mock
    private LlmPromptAuditService auditService;

    private PromptAuditInterceptor interceptor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        interceptor = new PromptAuditInterceptor(auditService, true, true, 32768);
    }

    @Test
    void writesRequestAndResponseAuditRecords() {
        ModelInvocationContext context = buildContext("session-1", "exec-1");

        interceptor.intercept(context, ctx -> CompletableFuture.completedFuture(
                        ModelInvocationResult.of(Map.of("messages", java.util.List.of(new AssistantMessage("done"))))))
                .join();

        ArgumentCaptor<LlmPromptAuditEntity> captor = ArgumentCaptor.forClass(LlmPromptAuditEntity.class);
        verify(auditService, times(2)).save(captor.capture());
        assertEquals("REQUEST", captor.getAllValues().get(0).getPhase());
        assertEquals("RESPONSE", captor.getAllValues().get(1).getPhase());
        assertEquals("session-1", captor.getAllValues().get(0).getSessionId());
        assertEquals("exec-1", captor.getAllValues().get(0).getExecutionId());
    }

    @Test
    void writesErrorAuditRecordWhenInvocationFails() {
        ModelInvocationContext context = buildContext("session-2", "exec-2");

        CompletionException exception = assertThrows(CompletionException.class, () ->
                interceptor.intercept(context, ctx -> CompletableFuture.failedFuture(
                                new IllegalStateException("model failed")))
                        .join());
        assertTrue(exception.getCause() instanceof IllegalStateException);

        ArgumentCaptor<LlmPromptAuditEntity> captor = ArgumentCaptor.forClass(LlmPromptAuditEntity.class);
        verify(auditService, times(2)).save(captor.capture());
        assertEquals("REQUEST", captor.getAllValues().get(0).getPhase());
        assertEquals("ERROR", captor.getAllValues().get(1).getPhase());
        assertTrue(captor.getAllValues().get(1).getErrorMessage().contains("model failed"));
    }

    @Test
    void skipsPersistenceWhenDisabled() {
        PromptAuditInterceptor disabled = new PromptAuditInterceptor(auditService, false, true, 32768);
        ModelInvocationContext context = buildContext("session-3", "exec-3");

        disabled.intercept(context, ctx -> CompletableFuture.completedFuture(ModelInvocationResult.of(Map.of())))
                .join();

        verify(auditService, times(0)).save(any());
    }

    @Test
    void writesAggregatedFullTextForStreamingResponse() throws Exception {
        ModelInvocationContext context = buildContext("session-4", "exec-4");
        Flux<ChatResponse> stream = Flux.just(chatResponse("A"), chatResponse("B"));

        ModelInvocationResult result = interceptor.intercept(context, ctx -> CompletableFuture.completedFuture(
                        ModelInvocationResult.of(Map.of("llm_stream", GraphFlux.of("llm", stream)))))
                .join();

        Object streamValue = result.updates().get("llm_stream");
        assertNotNull(streamValue);
        GraphFlux<?> auditedStream = (GraphFlux<?>) streamValue;
        ((Flux<?>) auditedStream.getStream()).collectList().block();

        ArgumentCaptor<LlmPromptAuditEntity> captor = ArgumentCaptor.forClass(LlmPromptAuditEntity.class);
        verify(auditService, times(2)).save(captor.capture());
        LlmPromptAuditEntity responseEntity = captor.getAllValues().get(1);
        assertEquals("RESPONSE", responseEntity.getPhase());

        JsonNode responseJson = objectMapper.readTree(responseEntity.getResponseJson());
        assertEquals("AB", responseJson.get("fullText").asText());
        assertEquals(2, responseJson.get("chunkCount").asInt());
        assertTrue(responseJson.get("hasStream").asBoolean());
    }

    @Test
    void writesErrorWhenStreamingFluxFails() {
        ModelInvocationContext context = buildContext("session-5", "exec-5");
        Flux<ChatResponse> stream = Flux.concat(
                Flux.just(chatResponse("ok")),
                Flux.error(new IllegalStateException("stream failed"))
        );

        ModelInvocationResult result = interceptor.intercept(context, ctx -> CompletableFuture.completedFuture(
                        ModelInvocationResult.of(Map.of("llm_stream", GraphFlux.of("llm", stream)))))
                .join();

        GraphFlux<?> auditedStream = (GraphFlux<?>) result.updates().get("llm_stream");
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ((Flux<?>) auditedStream.getStream()).collectList().block()
        );
        assertEquals("stream failed", exception.getMessage());

        ArgumentCaptor<LlmPromptAuditEntity> captor = ArgumentCaptor.forClass(LlmPromptAuditEntity.class);
        verify(auditService, times(2)).save(captor.capture());
        assertEquals("REQUEST", captor.getAllValues().get(0).getPhase());
        assertEquals("ERROR", captor.getAllValues().get(1).getPhase());
        assertTrue(captor.getAllValues().get(1).getErrorMessage().contains("stream failed"));
    }

    private ChatResponse chatResponse(String text) {
        return new ChatResponse(java.util.List.of(new Generation(new AssistantMessage(text))));
    }

    private ModelInvocationContext buildContext(String sessionId, String executionId) {
        State state = StateTemplates.createWithCustomData(Map.of(
                "input", "hello",
                "current_agent", "TestAgent"
        ));
        ChatModelRequest request = ChatModelRequest.builder(state)
                .systemPrompt("you are assistant")
                .build();
        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .executionId(executionId)
                .build();
        return ModelInvocationContext.of(state, config, request, false);
    }
}
