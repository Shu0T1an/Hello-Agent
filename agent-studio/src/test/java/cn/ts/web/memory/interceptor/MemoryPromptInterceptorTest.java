package cn.ts.web.memory.interceptor;

import cn.ts.agent.interceptor.ModelInvocationContext;
import cn.ts.agent.interceptor.ModelInvocationResult;
import cn.ts.agent.model.ChatModelRequest;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import cn.ts.web.memory.config.MemoryProperties;
import cn.ts.web.memory.service.MemoryService;
import cn.ts.web.memory.spi.MemoryPayload;
import cn.ts.web.memory.spi.MemoryProvider;
import cn.ts.web.memory.spi.MemoryRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryPromptInterceptorTest {

    @Test
    void appendsMemoryPromptWhenPayloadExists() throws Exception {
        MemoryProperties properties = new MemoryProperties();
        properties.setEnabled(true);
        properties.setInjectHeader(true);

        MemoryProvider provider = new MemoryProvider() {
            @Override
            public String providerName() {
                return "test-provider";
            }

            @Override
            public Optional<MemoryPayload> load(MemoryRequest request) {
                return Optional.of(new MemoryPayload(
                        "Use DDL before DML.",
                        "memory.md",
                        false,
                        Map.of("provider", "test-provider")
                ));
            }
        };
        MemoryPromptInterceptor interceptor = new MemoryPromptInterceptor(
                properties,
                new MemoryService(List.of(provider))
        );

        State state = new MapState(Map.of("input", "hello"));
        ChatModelRequest request = ChatModelRequest.builder(state)
                .systemPrompt("base prompt")
                .build();
        ModelInvocationContext context = ModelInvocationContext.of(
                state,
                RunnableConfig.builder().threadId("session-1").executionId("exec-1").build(),
                request,
                false
        );

        AtomicReference<ModelInvocationContext> captured = new AtomicReference<>();
        interceptor.intercept(context, nextCtx -> {
            captured.set(nextCtx);
            return CompletableFuture.completedFuture(ModelInvocationResult.of(Map.of("ok", true)));
        }).get();

        String prompt = captured.get().request().getSystemPrompt();
        assertTrue(prompt.contains("base prompt"));
        assertTrue(prompt.contains("Memory Rules"));
        assertTrue(prompt.contains("Use DDL before DML."));
        assertTrue(prompt.contains("memory.md"));
    }

    @Test
    void keepsPromptWhenDisabled() throws Exception {
        MemoryProperties properties = new MemoryProperties();
        properties.setEnabled(false);

        MemoryProvider provider = new MemoryProvider() {
            @Override
            public String providerName() {
                return "test-provider";
            }

            @Override
            public Optional<MemoryPayload> load(MemoryRequest request) {
                return Optional.of(new MemoryPayload("should not be used", "memory.md", false, Map.of()));
            }
        };
        MemoryPromptInterceptor interceptor = new MemoryPromptInterceptor(
                properties,
                new MemoryService(List.of(provider))
        );

        State state = new MapState(Map.of("input", "hello"));
        ChatModelRequest request = ChatModelRequest.builder(state)
                .systemPrompt("base prompt")
                .build();
        ModelInvocationContext context = ModelInvocationContext.of(
                state,
                RunnableConfig.defaultConfig(),
                request,
                false
        );

        AtomicReference<ModelInvocationContext> captured = new AtomicReference<>();
        interceptor.intercept(context, nextCtx -> {
            captured.set(nextCtx);
            return CompletableFuture.completedFuture(ModelInvocationResult.of(Map.of("ok", true)));
        }).get();

        assertEquals("base prompt", captured.get().request().getSystemPrompt());
    }

    @Test
    void failOpenWhenProviderThrows() throws Exception {
        MemoryProperties properties = new MemoryProperties();
        properties.setEnabled(true);

        MemoryProvider provider = new MemoryProvider() {
            @Override
            public String providerName() {
                return "failing-provider";
            }

            @Override
            public Optional<MemoryPayload> load(MemoryRequest request) {
                throw new IllegalStateException("boom");
            }
        };
        MemoryPromptInterceptor interceptor = new MemoryPromptInterceptor(
                properties,
                new MemoryService(List.of(provider))
        );

        State state = new MapState(Map.of("input", "hello"));
        ChatModelRequest request = ChatModelRequest.builder(state)
                .systemPrompt("base prompt")
                .build();
        ModelInvocationContext context = ModelInvocationContext.of(
                state,
                RunnableConfig.defaultConfig(),
                request,
                false
        );

        AtomicReference<ModelInvocationContext> captured = new AtomicReference<>();
        interceptor.intercept(context, nextCtx -> {
            captured.set(nextCtx);
            return CompletableFuture.completedFuture(ModelInvocationResult.of(Map.of("ok", true)));
        }).get();

        assertEquals("base prompt", captured.get().request().getSystemPrompt());
        assertEquals(-175, interceptor.getOrder());
    }
}
