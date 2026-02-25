package cn.ts.agent.core;

import cn.ts.agent.constant.AgentConstants;
import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.agent.interceptor.ModelInvocationResult;
import cn.ts.agent.node.LLMNode;
import cn.ts.graph.CompiledGraph;
import cn.ts.graph.node.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class ReactAgentInterceptorWiringTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void builderInjectsModelInterceptorsIntoLlmNode() throws Exception {
        ModelInterceptor interceptor = new ModelInterceptor() {
            @Override
            public String getName() {
                return "test-interceptor";
            }

            @Override
            public java.util.concurrent.CompletableFuture<ModelInvocationResult> intercept(
                    cn.ts.agent.interceptor.ModelInvocationContext context,
                    cn.ts.agent.interceptor.ModelInvoker next) {
                return next.proceed(context);
            }
        };

        ReactAgent agent = ReactAgent.builder()
                .name("test")
                .chatModel(chatModel)
                .modelInterceptors(List.of(interceptor))
                .build();

        CompiledGraph graph = agent.getGraph();
        Node modelNode = graph.getNodes().get("_AGENT_MODEL_");
        assertNotNull(modelNode);

        LLMNode llmNode = assertInstanceOf(LLMNode.class, modelNode.action());

        Field interceptorsField = LLMNode.class.getDeclaredField("interceptors");
        interceptorsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ModelInterceptor> wired = (List<ModelInterceptor>) interceptorsField.get(llmNode);

        assertEquals(1, wired.size());
        assertEquals("test-interceptor", wired.get(0).getName());
    }

    @Test
    void builderInjectsConfiguredSystemPromptIntoLlmNode() throws Exception {
        String configuredSystemPrompt = "Business system prompt for routing and safety.";

        ReactAgent agent = ReactAgent.builder()
                .name("test")
                .chatModel(chatModel)
                .systemPrompt(configuredSystemPrompt)
                .build();

        CompiledGraph graph = agent.getGraph();
        Node modelNode = graph.getNodes().get("_AGENT_MODEL_");
        assertNotNull(modelNode);

        LLMNode llmNode = assertInstanceOf(LLMNode.class, modelNode.action());

        Field systemPromptField = LLMNode.class.getDeclaredField("systemPrompt");
        systemPromptField.setAccessible(true);
        String actualPrompt = (String) systemPromptField.get(llmNode);

        assertEquals(configuredSystemPrompt, actualPrompt);
    }

    @Test
    void builderFallsBackToDefaultSystemPromptWhenConfiguredPromptBlank() throws Exception {
        ReactAgent agent = ReactAgent.builder()
                .name("test")
                .chatModel(chatModel)
                .systemPrompt("   ")
                .build();

        CompiledGraph graph = agent.getGraph();
        Node modelNode = graph.getNodes().get("_AGENT_MODEL_");
        assertNotNull(modelNode);

        LLMNode llmNode = assertInstanceOf(LLMNode.class, modelNode.action());

        Field systemPromptField = LLMNode.class.getDeclaredField("systemPrompt");
        systemPromptField.setAccessible(true);
        String actualPrompt = (String) systemPromptField.get(llmNode);

        assertEquals(AgentConstants.DEFAULT_SYSTEM_PROMPT, actualPrompt);
    }
}
