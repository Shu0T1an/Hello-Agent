package cn.ts.agent.extension.interceptor;

import cn.ts.agent.core.ReactAgent;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubAgentInterceptorTest {

    @Test
    void appendsPromptAndInjectsTaskToolWithoutDroppingExistingTools() throws Exception {
        ToolCallback existing = MethodToolCallbackProvider.builder()
                .toolObjects(new ExistingTool())
                .build()
                .getToolCallbacks()[0];

        State state = new MapState(Map.of("input", "hi"));
        ChatModelRequest request = ChatModelRequest.builder(state)
                .systemPrompt("base")
                .toolCallbacks(List.of(existing))
                .build();
        ModelInvocationContext context = ModelInvocationContext.of(
                state,
                RunnableConfig.defaultConfig(),
                request,
                false
        );

        ReactAgent subAgent = mock(ReactAgent.class);
        when(subAgent.getDescription()).thenReturn("Research helper");

        SubAgentInterceptor interceptor = new SubAgentInterceptor("extra", Map.of("research", subAgent));

        AtomicReference<ModelInvocationContext> captured = new AtomicReference<>();
        interceptor.intercept(context, nextCtx -> {
            captured.set(nextCtx);
            return CompletableFuture.completedFuture(ModelInvocationResult.of(Map.of("ok", true)));
        }).get();

        ModelInvocationContext enhanced = captured.get();
        String systemPrompt = enhanced.request().getSystemPrompt();
        assertTrue(systemPrompt.contains("base"));
        assertTrue(systemPrompt.contains("extra"));
        assertTrue(systemPrompt.contains("research"));

        List<ToolCallback> tools = enhanced.request().getToolCallbacks();
        assertEquals(2, tools.size());
        assertTrue(tools.stream().anyMatch(t -> "existing_tool".equals(t.getToolDefinition().name())));
        assertTrue(tools.stream().anyMatch(t -> "task".equals(t.getToolDefinition().name())));
    }

    static class ExistingTool {
        @Tool(name = "existing_tool", description = "existing")
        public String existing(String input) {
            return input;
        }
    }
}
