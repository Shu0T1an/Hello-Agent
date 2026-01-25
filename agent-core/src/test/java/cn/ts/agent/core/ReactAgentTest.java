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
 * 测试 ReAct Agent 的各项功能，包括构造函数、执行和属性获取
 * </p>
 *
 * @author tianshuo
 */
@ExtendWith(MockitoExtension.class)
class ReactAgentTest {

    @Mock
    private ChatModel mockChatModel;

    @Test
    void testConstructorWithAllParameters() {
        Object testTool = new TestToolClass();

        ReactAgent agent = new ReactAgent(
                "test_agent",
                "Test description",
                mockChatModel,
                testTool
        );

        assertNotNull(agent);
        assertEquals("test_agent", agent.getName());
        assertEquals("Test description", agent.getDescription());
    }

    @Test
    void testConstructorWithMinimalParameters() {
        ReactAgent agent = new ReactAgent("test_agent", mockChatModel);

        assertNotNull(agent);
        assertEquals("test_agent", agent.getName());
        assertEquals("ReAct Agent with tool calling capabilities", agent.getDescription());
    }

    @Test
    void testConstructorWithDescription() {
        ReactAgent agent = new ReactAgent(
                "test_agent",
                "Custom description",
                mockChatModel
        );

        assertNotNull(agent);
        assertEquals("Custom description", agent.getDescription());
    }

    @Test
    void testConstructorWithStreaming() {
        Object testTool = new TestToolClass();

        ReactAgent agent = new ReactAgent(
                "test_agent",
                "Test agent",
                mockChatModel,
                testTool
        );

        assertNotNull(agent);
    }

    @Test
    void testConstructorWithNoTools() {
        ReactAgent agent = new ReactAgent("test_agent", mockChatModel);

        assertNotNull(agent);
        assertArrayEquals(new Object[0], agent.getTools());
    }

    @Test
    void testConstructorWithMultipleTools() {
        TestToolClass tool1 = new TestToolClass();
        AnotherTestToolClass tool2 = new AnotherTestToolClass();

        ReactAgent agent = new ReactAgent(
                "test_agent",
                mockChatModel,
                tool1,
                tool2
        );

        assertNotNull(agent);
        assertEquals(2, agent.getTools().length);
    }

    @Test
    void testGetName() {
        ReactAgent agent = new ReactAgent("my_agent", mockChatModel);

        assertEquals("my_agent", agent.getName());
    }

    @Test
    void testGetDescription() {
        ReactAgent agent = new ReactAgent(
                "test_agent",
                "This is a test agent",
                mockChatModel
        );

        assertEquals("This is a test agent", agent.getDescription());
    }

    @Test
    void testGetChatModel() {
        ReactAgent agent = new ReactAgent("test_agent", mockChatModel);

        assertSame(mockChatModel, agent.getChatModel());
    }

    @Test
    void testGetTools() {
        TestToolClass tool = new TestToolClass();

        ReactAgent agent = new ReactAgent("test_agent", mockChatModel, tool);

        Object[] tools = agent.getTools();

        assertNotNull(tools);
        assertEquals(1, tools.length);
        assertSame(tool, tools[0]);
    }

    @Test
    void testGetGraph() {
        ReactAgent agent = new ReactAgent("test_agent", mockChatModel);

        CompiledGraph graph = agent.getGraph();

        assertNotNull(graph);
        assertFalse(graph.getNodes().isEmpty());
    }

    @Test
    void testGraphHasRequiredNodes() {
        ReactAgent agent = new ReactAgent("test_agent", mockChatModel);

        CompiledGraph graph = agent.getGraph();

        // 验证图包含必需的节点（节点名称可能因实现而异）
        assertTrue(graph.getNodes().size() >= 2);
    }

    @Test
    void testGraphHasEntryPoint() {
        ReactAgent agent = new ReactAgent("test_agent", mockChatModel);

        CompiledGraph graph = agent.getGraph();

        assertNotNull(graph.getEntryPoint());
        assertFalse(graph.getEntryPoint().isEmpty());
    }

    @Test
    void testToString() {
        ReactAgent agent = new ReactAgent("test_agent", mockChatModel);

        String str = agent.toString();

        assertNotNull(str);
        assertTrue(str.contains("test_agent") || str.contains("ReactAgent"));
    }

    @Test
    void testMultipleAgentsHaveIndependentGraphs() {
        ReactAgent agent1 = new ReactAgent("agent1", mockChatModel);
        ReactAgent agent2 = new ReactAgent("agent2", mockChatModel);

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
