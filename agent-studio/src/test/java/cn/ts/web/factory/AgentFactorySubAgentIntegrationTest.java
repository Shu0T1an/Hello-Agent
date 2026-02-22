package cn.ts.web.factory;

import cn.ts.agent.core.ReactAgent;
import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.agent.node.LLMNode;
import cn.ts.agent.node.ToolNode;
import cn.ts.graph.CompiledGraph;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.node.Node;
import cn.ts.web.agent.dto.AgentConfigDTO;
import cn.ts.web.agent.dto.ModelConfigDTO;
import cn.ts.web.agent.dto.SubAgentMappingDTO;
import cn.ts.web.agent.dto.SubAgentToolsPolicy;
import cn.ts.web.agent.entity.AgentConfigEntity;
import cn.ts.web.agent.mapper.AgentConfigMapper;
import cn.ts.web.agent.mapper.SubAgentMappingMapper;
import cn.ts.web.agent.service.ModelConfigService;
import cn.ts.web.agent.service.SubAgentProgressBus;
import cn.ts.web.tool.service.ToolDefinitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.model.ChatModel;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentFactorySubAgentIntegrationTest {

    @Mock
    private ModelConfigService modelConfigService;

    @Mock
    private ToolDefinitionService toolDefinitionService;

    @Mock
    private AgentConfigMapper agentConfigMapper;

    @Mock
    private SubAgentMappingMapper subAgentMappingMapper;

    @Mock
    private CheckpointManager checkpointManager;

    @Mock
    private ChatModel chatModel;

    @Mock
    private SubAgentProgressBus subAgentProgressBus;

    private AgentFactory agentFactory;

    @BeforeEach
    void setUp() {
        agentFactory = new AgentFactory(
                modelConfigService,
                toolDefinitionService,
                agentConfigMapper,
                subAgentMappingMapper,
                checkpointManager,
                subAgentProgressBus,
                "test-agent"

        );
        when(modelConfigService.createChatModel(any())).thenReturn(chatModel);
        ModelConfigDTO fallbackModelConfig = new ModelConfigDTO();
        fallbackModelConfig.setId(1L);
        when(modelConfigService.getModelById(anyLong())).thenReturn(fallbackModelConfig);
        when(toolDefinitionService.instantiateTools(any())).thenReturn(new Object[0]);
        when(toolDefinitionService.getToolsByAgentId(anyLong())).thenReturn(List.of());
        when(toolDefinitionService.getActiveTools()).thenReturn(List.of());
    }

    @Test
    void injectsSubAgentInterceptorWhenEnabled() throws Exception {
        AgentConfigDTO main = mainConfig();
        main.setEnableSubAgentInterceptor(true);
        main.setIncludeGeneralPurpose(false);

        ReactAgent agent = agentFactory.createAgent(main);
        List<ModelInterceptor> interceptors = getLlmInterceptors(agent);

        assertFalse(interceptors.isEmpty());
        assertEquals("SubAgent", interceptors.get(0).getName());
        assertTrue(getToolNodeCallbacks(agent).stream().anyMatch(tc -> "task".equals(tc.getToolDefinition().name())));
    }

    @Test
    void subAgentInstancesDoNotInstallSubAgentInterceptor() throws Exception {
        AgentConfigDTO main = mainConfig();
        main.setEnableSubAgentInterceptor(true);
        main.setIncludeGeneralPurpose(false);

        SubAgentMappingDTO mapping = new SubAgentMappingDTO();
        mapping.setSubagentType("research");
        mapping.setTargetAgentId(200L);
        mapping.setEnabled(true);
        mapping.setToolsPolicy(SubAgentToolsPolicy.INHERIT);
        main.setSubAgents(List.of(mapping));

        AgentConfigEntity target = new AgentConfigEntity();
        target.setId(200L);
        target.setAgentName("target-agent");
        target.setDisplayName("Target Agent");
        target.setDescription("Target");
        target.setModelId(1L);
        target.setEnableStreaming(false);
        target.setEnableSubAgentInterceptor(true);
        when(agentConfigMapper.selectById(200L)).thenReturn(target);

        ReactAgent agent = agentFactory.createAgent(main);
        List<ModelInterceptor> mainInterceptors = getLlmInterceptors(agent);
        Object subAgentInterceptor = mainInterceptors.get(0);

        Field subAgentsField = subAgentInterceptor.getClass().getDeclaredField("subAgents");
        subAgentsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ReactAgent> subAgents = (Map<String, ReactAgent>) subAgentsField.get(subAgentInterceptor);

        ReactAgent sub = subAgents.get("research");
        assertNotNull(sub);
        List<ModelInterceptor> subInterceptors = getLlmInterceptors(sub);
        assertNotEquals(0, subInterceptors.size());
        assertTrue(subInterceptors.stream().noneMatch(i -> "SubAgent".equals(i.getName())));
    }

    @Test
    void mainAgentKeepsCheckpointManagerWhileSubAgentsDisableIt() throws Exception {
        AgentConfigDTO main = mainConfig();
        main.setEnableSubAgentInterceptor(true);
        main.setIncludeGeneralPurpose(false);

        SubAgentMappingDTO mapping = new SubAgentMappingDTO();
        mapping.setSubagentType("research");
        mapping.setTargetAgentId(200L);
        mapping.setEnabled(true);
        mapping.setToolsPolicy(SubAgentToolsPolicy.INHERIT);
        main.setSubAgents(List.of(mapping));

        AgentConfigEntity target = new AgentConfigEntity();
        target.setId(200L);
        target.setAgentName("target-agent");
        target.setDisplayName("Target Agent");
        target.setDescription("Target");
        target.setModelId(1L);
        target.setEnableStreaming(false);
        target.setEnableSubAgentInterceptor(false);
        when(agentConfigMapper.selectById(200L)).thenReturn(target);

        ReactAgent agent = agentFactory.createAgent(main);
        assertTrue(agent.getGraph().hasCheckpointManager());

        List<ModelInterceptor> mainInterceptors = getLlmInterceptors(agent);
        ModelInterceptor subAgentInterceptor = mainInterceptors.stream()
                .filter(i -> "SubAgent".equals(i.getName()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, ReactAgent> subAgents =
                (Map<String, ReactAgent>) readPrivateField(subAgentInterceptor, "subAgents");

        assertFalse(subAgents.isEmpty());
        for (ReactAgent subAgent : subAgents.values()) {
            assertFalse(subAgent.getGraph().hasCheckpointManager());
        }
    }

    private AgentConfigDTO mainConfig() {
        AgentConfigDTO config = new AgentConfigDTO();
        config.setId(100L);
        config.setAgentName("main-agent");
        config.setDisplayName("Main Agent");
        config.setDescription("Main");
        config.setModelId(1L);
        config.setEnableStreaming(false);
        config.setSubAgentToolsPolicy(SubAgentToolsPolicy.INHERIT);

        ModelConfigDTO modelConfig = new ModelConfigDTO();
        modelConfig.setId(1L);
        config.setModelConfig(modelConfig);
        config.setToolDefinitions(List.of());
        return config;
    }

    private List<ModelInterceptor> getLlmInterceptors(ReactAgent agent) throws Exception {
        CompiledGraph graph = agent.getGraph();
        Node modelNode = graph.getNodes().get("_AGENT_MODEL_");
        LLMNode llmNode = assertInstanceOf(LLMNode.class, modelNode.action());

        Field interceptorsField = LLMNode.class.getDeclaredField("interceptors");
        interceptorsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ModelInterceptor> interceptors = (List<ModelInterceptor>) interceptorsField.get(llmNode);
        return interceptors;
    }

    private List<org.springframework.ai.tool.ToolCallback> getToolNodeCallbacks(ReactAgent agent) throws Exception {
        CompiledGraph graph = agent.getGraph();
        Node toolNode = graph.getNodes().get("_AGENT_TOOL_");
        ToolNode node = assertInstanceOf(ToolNode.class, toolNode.action());

        Field callbacksField = ToolNode.class.getDeclaredField("toolCallbacks");
        callbacksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<org.springframework.ai.tool.ToolCallback> callbacks =
                (List<org.springframework.ai.tool.ToolCallback>) callbacksField.get(node);
        return callbacks;
    }

    private Object readPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
