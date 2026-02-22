package cn.ts.web.agent.deepsearch;

import cn.ts.agent.core.ReactAgent;
import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.agent.node.LLMNode;
import cn.ts.agent.node.ToolNode;
import cn.ts.graph.CompiledGraph;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.node.Node;
import cn.ts.web.agent.service.SubAgentProgressBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DeepSearchAgentBuilderTest {

    @Mock
    private CheckpointManager checkpointManager;

    @Mock
    private ChatModel chatModel;

    @Mock
    private SubAgentProgressBus subAgentProgressBus;

    private DeepSearchProperties properties;
    private DeepSearchAgentBuilder builder;

    @BeforeEach
    void setUp() {
        properties = new DeepSearchProperties();
        properties.setAgentName("deep-search");
        properties.setDescription("test deep search");
        properties.setIncludeGeneralPurposeSubagent(true);

        builder = new DeepSearchAgentBuilder(
                properties,
                checkpointManager,
                subAgentProgressBus
        );
    }

    @Test
    void buildsAgentWithSubAgentInterceptorAndRequiredSubAgents() throws Exception {
        ReactAgent agent = builder.build(chatModel, new Object[0]);
        List<ModelInterceptor> interceptors = getLlmInterceptors(agent);
        assertFalse(interceptors.isEmpty());
        assertTrue(agent.getGraph().getNodes().containsKey("__hook_ClarificationQaHook_after"));
        assertTrue(getToolNodeCallbacks(agent).stream().anyMatch(tc -> "task".equals(tc.getToolDefinition().name())));

        ModelInterceptor subAgentInterceptor = interceptors.stream()
                .filter(i -> "SubAgent".equals(i.getName()))
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        Map<String, ReactAgent> subAgents = (Map<String, ReactAgent>) readPrivateField(subAgentInterceptor, "subAgents");
        assertTrue(subAgents.containsKey("research-agent"));
        assertTrue(subAgents.containsKey("critique-agent"));
        assertTrue(subAgents.containsKey("general-purpose"));
    }

    @Test
    void doesNotIncludeGeneralPurposeWhenDisabled() throws Exception {
        properties.setIncludeGeneralPurposeSubagent(false);

        ReactAgent agent = builder.build(chatModel, new Object[0]);
        List<ModelInterceptor> interceptors = getLlmInterceptors(agent);
        ModelInterceptor subAgentInterceptor = interceptors.stream()
                .filter(i -> "SubAgent".equals(i.getName()))
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        Map<String, ReactAgent> subAgents = (Map<String, ReactAgent>) readPrivateField(subAgentInterceptor, "subAgents");
        assertFalse(subAgents.containsKey("general-purpose"));
        assertTrue(subAgents.containsKey("research-agent"));
        assertTrue(subAgents.containsKey("critique-agent"));
    }

    @Test
    void mainAgentKeepsCheckpointManagerWhileBuiltInSubAgentsDisableIt() throws Exception {
        ReactAgent agent = builder.build(chatModel, new Object[0]);
        assertTrue(agent.getGraph().hasCheckpointManager());

        List<ModelInterceptor> interceptors = getLlmInterceptors(agent);
        ModelInterceptor subAgentInterceptor = interceptors.stream()
                .filter(i -> "SubAgent".equals(i.getName()))
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        Map<String, ReactAgent> subAgents = (Map<String, ReactAgent>) readPrivateField(subAgentInterceptor, "subAgents");
        assertFalse(subAgents.isEmpty());
        for (ReactAgent subAgent : subAgents.values()) {
            assertFalse(subAgent.getGraph().hasCheckpointManager());
        }
    }

    @Test
    void throwsWhenChatModelMissing() {
        assertThrows(IllegalArgumentException.class, () -> builder.build(null, new Object[0]));
    }

    @Test
    void throwsWhenAgentNameBlank() {
        properties.setAgentName(" ");
        assertThrows(IllegalArgumentException.class, () -> builder.build(chatModel, new Object[0]));
    }

    private List<ModelInterceptor> getLlmInterceptors(ReactAgent agent) throws Exception {
        CompiledGraph graph = agent.getGraph();
        Node modelNode = graph.getNodes().get("_AGENT_MODEL_");
        LLMNode llmNode = assertInstanceOf(LLMNode.class, modelNode.action());
        @SuppressWarnings("unchecked")
        List<ModelInterceptor> interceptors = (List<ModelInterceptor>) readPrivateField(llmNode, "interceptors");
        return interceptors;
    }

    private List<org.springframework.ai.tool.ToolCallback> getToolNodeCallbacks(ReactAgent agent) throws Exception {
        CompiledGraph graph = agent.getGraph();
        Node toolNode = graph.getNodes().get("_AGENT_TOOL_");
        ToolNode node = assertInstanceOf(ToolNode.class, toolNode.action());
        @SuppressWarnings("unchecked")
        List<org.springframework.ai.tool.ToolCallback> callbacks =
                (List<org.springframework.ai.tool.ToolCallback>) readPrivateField(node, "toolCallbacks");
        return callbacks;
    }

    private Object readPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
