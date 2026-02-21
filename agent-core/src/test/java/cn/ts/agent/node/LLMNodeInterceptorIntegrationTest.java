package cn.ts.agent.node;

import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.agent.interceptor.ModelInvocationResult;
import cn.ts.agent.model.ChatModelRequest;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LLMNodeInterceptorIntegrationTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

    private ChatResponse chatResponse;

    @BeforeEach
    void setUp() {
        AssistantMessage.builder()
                .build();
        AssistantMessage assistantMessage =  AssistantMessage.builder().build();
        chatResponse = new ChatResponse(List.of(new Generation(assistantMessage)));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.chatResponse()).thenReturn(Flux.just(chatResponse));
    }

    @Test
    void nonStreamingInterceptorCanRewriteRequestAndUpdates() throws Exception {
        State state = new MapState(Map.of("input", "original"));

        ModelInterceptor requestRewrite = new ModelInterceptor() {
            @Override
            public String getName() {
                return "request-rewrite";
            }

            @Override
            public java.util.concurrent.CompletableFuture<ModelInvocationResult> intercept(
                    cn.ts.agent.interceptor.ModelInvocationContext context,
                    cn.ts.agent.interceptor.ModelInvoker next) {
                ChatModelRequest rewritten = ChatModelRequest.builder(new MapState(Map.of("input", "rewritten"))).build();
                return next.proceed(context.withRequest(rewritten));
            }
        };

        ModelInterceptor updateEnhancer = new ModelInterceptor() {
            @Override
            public String getName() {
                return "update-enhancer";
            }

            @Override
            public java.util.concurrent.CompletableFuture<ModelInvocationResult> intercept(
                    cn.ts.agent.interceptor.ModelInvocationContext context,
                    cn.ts.agent.interceptor.ModelInvoker next) {
                return next.proceed(context).thenApply(result -> {
                    Map<String, Object> updates = new HashMap<>(result.updates());
                    updates.put("intercepted", true);
                    return ModelInvocationResult.of(updates);
                });
            }
        };

        LLMNode node = LLMNode.builder(chatClient)
                .streaming(false)
                .interceptors(List.of(requestRewrite, updateEnhancer))
                .build();

        Map<String, Object> result = node.apply(state);

        assertEquals(true, result.get("intercepted"));
        assertNotNull(result.get("chat_response"));
        assertTrue(result.containsKey("messages"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(requestSpec, atLeastOnce()).messages(messagesCaptor.capture());

        boolean hasRewrittenUserMessage = messagesCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .filter(msg -> msg instanceof UserMessage)
                .map(msg -> (UserMessage) msg)
                .anyMatch(msg -> "rewritten".equals(msg.getText()));
        assertTrue(hasRewrittenUserMessage);
    }

    @Test
    void streamingInterceptorWorksAtInvocationLevelAndPreservesStreamOutput() throws Exception {
        State state = new MapState(Map.of("input", "stream"));

        ModelInterceptor marker = new ModelInterceptor() {
            @Override
            public String getName() {
                return "stream-marker";
            }

            @Override
            public java.util.concurrent.CompletableFuture<ModelInvocationResult> intercept(
                    cn.ts.agent.interceptor.ModelInvocationContext context,
                    cn.ts.agent.interceptor.ModelInvoker next) {
                return next.proceed(context).thenApply(result -> {
                    Map<String, Object> updates = new HashMap<>(result.updates());
                    updates.put("marker", "stream-invocation");
                    return ModelInvocationResult.of(updates);
                });
            }
        };

        LLMNode node = LLMNode.builder(chatClient)
                .streaming(true)
                .interceptors(List.of(marker))
                .build();

        Map<String, Object> result = node.apply(state);

        assertEquals("stream-invocation", result.get("marker"));
        assertTrue(result.containsKey("llm_stream"));
        assertNotNull(result.get("llm_stream"));
    }

    @Test
    void noInterceptorKeepsOriginalBehavior() throws Exception {
        State state = new MapState(Map.of("input", "plain"));

        LLMNode node = LLMNode.builder(chatClient)
                .streaming(false)
                .build();

        Map<String, Object> result = node.apply(state);
        assertEquals(chatResponse, result.get("chat_response"));
        assertTrue(result.containsKey("messages"));
    }
}
