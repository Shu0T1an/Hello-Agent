package cn.ts.agent.extension.interceptor;

import cn.ts.agent.interceptor.ModelInvocationContext;
import cn.ts.agent.interceptor.ModelInvocationResult;
import cn.ts.agent.model.ChatModelRequest;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeContextPromptInterceptorTest {

    @Test
    void appendsRuntimeContextToSystemPromptWhenEnabled() throws Exception {
        RuntimeContextPromptInterceptor interceptor = new RuntimeContextPromptInterceptor(
                true,
                true,
                true,
                Clock.fixed(Instant.parse("2026-02-25T07:35:00Z"), ZoneId.of("UTC")),
                ZoneId.of("Asia/Shanghai")
        );

        ToolCallback taskTool = MethodToolCallbackProvider.builder()
                .toolObjects(new TaskLikeTool())
                .build()
                .getToolCallbacks()[0];

        State state = new MapState(Map.of("input", "research evomap"));
        ChatModelRequest request = ChatModelRequest.builder(state)
                .systemPrompt("business prompt")
                .toolCallbacks(List.of(taskTool))
                .build();
        ModelInvocationContext context = ModelInvocationContext.of(
                state,
                RunnableConfig.defaultConfig(),
                request,
                true
        );

        AtomicReference<ModelInvocationContext> captured = new AtomicReference<>();
        interceptor.intercept(context, nextCtx -> {
            captured.set(nextCtx);
            return CompletableFuture.completedFuture(ModelInvocationResult.of(Map.of("ok", true)));
        }).get();

        String mergedPrompt = captured.get().request().getSystemPrompt();
        assertTrue(mergedPrompt.contains("business prompt"));
        assertTrue(mergedPrompt.contains("Runtime Context"));
        assertTrue(mergedPrompt.contains("2026-02-25T15:35:00+08:00"));
        assertTrue(mergedPrompt.contains("Asia/Shanghai"));
        assertTrue(mergedPrompt.contains("Tool Calling"));
        assertTrue(mergedPrompt.contains("Subagent Delegation"));
    }

    @Test
    void keepsPromptUnchangedWhenDisabled() throws Exception {
        RuntimeContextPromptInterceptor interceptor = new RuntimeContextPromptInterceptor(
                false,
                true,
                true,
                Clock.systemUTC(),
                ZoneId.of("UTC")
        );

        State state = new MapState(Map.of("input", "hi"));
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

    static class TaskLikeTool {
        @Tool(name = "task", description = "task tool")
        public String task(String request) {
            return request;
        }
    }
}
