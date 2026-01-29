package cn.ts.agent.core;

import cn.ts.graph.CompiledGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * ReactAgent 单元测试
 * <p>
 * 测试 ReAct Agent 的各项功能，包括 Builder、执行和属性获取
 * </p>
 *
 * @author tianshuo
 */
@ExtendWith(MockitoExtension.class)
class ReactAgentTest {

    @Mock
    private ChatModel mockChatModel;

    @Test
    void testBuilderWithAllParameters() {
        Object testTool = new TestToolClass();

        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .description("Test description")
                .chatModel(mockChatModel)
                .tools(testTool)
                .build();

        assertNotNull(agent);
        assertEquals("test_agent", agent.getName());
        assertEquals("Test description", agent.getDescription());
    }

    @Test
    void testBuilderWithMinimalParameters() {
        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .build();

        assertNotNull(agent);
        assertEquals("test_agent", agent.getName());
    }

    @Test
    void testBuilderWithDescription() {
        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .description("Custom description")
                .chatModel(mockChatModel)
                .build();

        assertNotNull(agent);
        assertEquals("Custom description", agent.getDescription());
    }

    @Test
    void testBuilderWithStreaming() {
        Object testTool = new TestToolClass();

        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .description("Test agent")
                .chatModel(mockChatModel)
                .streaming(true)
                .tools(testTool)
                .build();

        assertNotNull(agent);
    }

    @Test
    void testBuilderWithNoTools() {
        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .build();

        assertNotNull(agent);
        assertArrayEquals(new Object[0], agent.getTools());
    }

    @Test
    void testBuilderWithMultipleTools() {
        TestToolClass tool1 = new TestToolClass();
        AnotherTestToolClass tool2 = new AnotherTestToolClass();

        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .tools(tool1, tool2)
                .build();

        assertNotNull(agent);
        assertEquals(2, agent.getTools().length);
    }

    @Test
    void testGetName() {
        ReactAgent agent = ReactAgent.builder()
                .name("my_agent")
                .chatModel(mockChatModel)
                .build();

        assertEquals("my_agent", agent.getName());
    }

    @Test
    void testGetDescription() {
        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .description("This is a test agent")
                .chatModel(mockChatModel)
                .build();

        assertEquals("This is a test agent", agent.getDescription());
    }

    @Test
    void testGetChatModel() {
        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .build();

        assertSame(mockChatModel, agent.getChatModel());
    }

    @Test
    void testGetTools() {
        TestToolClass tool = new TestToolClass();

        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .tools(tool)
                .build();

        Object[] tools = agent.getTools();

        assertNotNull(tools);
        assertEquals(1, tools.length);
        assertSame(tool, tools[0]);
    }

    @Test
    void testGetGraph() {
        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .build();

        CompiledGraph graph = agent.getGraph();

        assertNotNull(graph);
        assertFalse(graph.getNodes().isEmpty());
    }

    @Test
    void testGraphHasRequiredNodes() {
        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .build();

        CompiledGraph graph = agent.getGraph();

        // 验证图包含必需的节点（节点名称可能因实现而异）
        assertTrue(graph.getNodes().size() >= 2);
    }

    @Test
    void testGraphHasEntryPoint() {
        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .build();

        CompiledGraph graph = agent.getGraph();

        assertNotNull(graph.getEntryPoint());
        assertFalse(graph.getEntryPoint().isEmpty());
    }

    @Test
    void testToString() {
        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .build();

        String str = agent.toString();

        assertNotNull(str);
        assertTrue(str.contains("test_agent") || str.contains("ReactAgent"));
    }

    @Test
    void testMultipleAgentsHaveIndependentGraphs() {
        ReactAgent agent1 = ReactAgent.builder()
                .name("agent1")
                .chatModel(mockChatModel)
                .build();
        ReactAgent agent2 = ReactAgent.builder()
                .name("agent2")
                .chatModel(mockChatModel)
                .build();

        CompiledGraph graph1 = agent1.getGraph();
        CompiledGraph graph2 = agent2.getGraph();

        // 验证每个 Agent 有独立的图实例
        assertNotSame(graph1, graph2);
    }

    // ==================== 测试工具类 ====================

    /**
     * 测试用的工具类 1
     */
    private static class TestToolClass {
        @org.springframework.ai.tool.annotation.Tool(description = "test_tool")
        public String testMethod(String input) {
            return "Result: " + input;
        }
    }

    /**
     * 测试用的工具类 2
     */
    private static class AnotherTestToolClass {
        @org.springframework.ai.tool.annotation.Tool(description = "another_tool")
        public String anotherMethod(String input) {
            return "Another: " + input;
        }
    }
}
