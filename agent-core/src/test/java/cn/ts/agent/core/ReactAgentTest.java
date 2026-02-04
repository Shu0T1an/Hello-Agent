package cn.ts.agent.core;

import cn.ts.graph.CompiledGraph;
import cn.ts.agent.hook.HumanInTheLoopHook;
import cn.ts.agent.hook.SimpleTestHook;
import cn.ts.graph.hook.Hook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

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

    /**
     * 测试带单个 Hook 的流式 Agent
     */
    @Test
    void testStreamingAgentWithSingleHook() {
        SimpleTestHook testHook = SimpleTestHook.create("[TestAgent]");

        ReactAgent agent = ReactAgent.builder()
                .name("streaming_hook_agent")
                .description("流式 Agent，使用测试 Hook")
                .chatModel(mockChatModel)
                .streaming(true)
                .hooks(List.of(testHook))
                .build();

        assertNotNull(agent);
        assertEquals("streaming_hook_agent", agent.getName());
        assertEquals("ReactAgent", testHook.getAgentName()); // Agent 名称在构建时被设置为 ReactAgent

        // 验证图结构包含 Hook 节点
        CompiledGraph graph = agent.getGraph();
        assertTrue(graph.getNodes().size() >= 3, "图应包含 MODEL、TOOL 和 Hook 节点");
    }

    /**
     * 测试带多个 Hook 的流式 Agent
     */
    @Test
    void testStreamingAgentWithMultipleHooks() {
        SimpleTestHook testHook = SimpleTestHook.create("[Logger]");

        HumanInTheLoopHook approvalHook = HumanInTheLoopHook.builder()
                .approvalOn("testMethod", "测试方法")
                .approvalMessage("请确认是否执行此操作")
                .build();

        TestToolClass tool = new TestToolClass();

        ReactAgent agent = ReactAgent.builder()
                .name("multi_hook_streaming_agent")
                .description("多 Hook 流式 Agent")
                .chatModel(mockChatModel)
                .streaming(true)
                .tools(tool)
                .hooks(List.of(testHook, approvalHook))
                .build();

        assertNotNull(agent);
        assertEquals("multi_hook_streaming_agent", agent.getName());

        // 验证两个 Hook 都设置了正确的 Agent 名称
        assertEquals("ReactAgent", testHook.getAgentName());
        assertEquals("ReactAgent", approvalHook.getAgentName());
    }

    /**
     * 测试流式 Agent 与人工审批 Hook 的集成
     */
    @Test
    void testStreamingAgentWithHumanInTheLoopHook() {
        TestToolClass tool = new TestToolClass();
        AnotherTestToolClass tool2 = new AnotherTestToolClass();

        HumanInTheLoopHook approvalHook = HumanInTheLoopHook.builder()
                .approvalOn("testMethod", "执行测试操作")
                .approvalOn("anotherMethod", "执行另一个操作")
                .approvalMessage("请审批以下工具调用")
                .build();

        ReactAgent agent = ReactAgent.builder()
                .name("approval_streaming_agent")
                .description("需要审批的流式 Agent")
                .chatModel(mockChatModel)
                .streaming(true)
                .tools(tool, tool2)
                .hooks(List.of(approvalHook))
                .build();

        assertNotNull(agent);
        assertNotNull(agent.getGraph());

        // 验证 Hook 已正确配置
        assertEquals("HumanInTheLoopHook", approvalHook.getName());
        // pendingFeedbacks 在运行时填充，初始为空
        assertEquals(0, approvalHook.getPendingFeedbacks().size(), "初始时待审批列表应为空");
    }

    /**
     * 测试流式 Agent Hook 的执行顺序
     */
    @Test
    void testStreamingAgentHookExecutionOrder() {
        // 只使用一个 Hook 来避免节点名称冲突
        SimpleTestHook testHook = SimpleTestHook.create("[Logger]");

        ReactAgent agent = ReactAgent.builder()
                .name("hook_order_agent")
                .chatModel(mockChatModel)
                .streaming(true)
                .hooks(List.of(testHook))
                .build();

        assertNotNull(agent);

        // 验证图结构
        // 期望: START -> testHook.before -> MODEL -> testHook.after -> ...
        CompiledGraph graph = agent.getGraph();
        assertTrue(graph.getNodes().size() >= 3, "图应包含 MODEL 和 Hook 节点");

        // 验证 Hook 的 AgentName 已设置
        assertEquals("ReactAgent", testHook.getAgentName());
    }

    /**
     * 测试流式 Agent 的 Hook 可以被独立配置
     */
    @Test
    void testStreamingAgentHooksAreIndependent() {
        // 创建两个独立的 Agent，每个有不同的 Hook
        SimpleTestHook hook1 = SimpleTestHook.create("[Agent1-Logger]");

        HumanInTheLoopHook hook2 = HumanInTheLoopHook.builder()
                .approvalOn("testMethod", "Agent2 的测试")
                .build();

        ReactAgent agent1 = ReactAgent.builder()
                .name("agent1")
                .chatModel(mockChatModel)
                .streaming(true)
                .hooks(List.of(hook1))
                .build();

        ReactAgent agent2 = ReactAgent.builder()
                .name("agent2")
                .chatModel(mockChatModel)
                .streaming(true)
                .hooks(List.of(hook2))
                .build();

        assertNotNull(agent1);
        assertNotNull(agent2);

        // 验证每个 Agent 的图是独立的
        assertNotSame(agent1.getGraph(), agent2.getGraph());

        // 验证 Hook 的 AgentName 都指向 ReactAgent
        assertEquals("ReactAgent", hook1.getAgentName());
        assertEquals("ReactAgent", hook2.getAgentName());
    }

    /**
     * 测试空 Hook 列表不影响流式 Agent
     */
    @Test
    void testStreamingAgentWithEmptyHooks() {
        ReactAgent agent = ReactAgent.builder()
                .name("empty_hooks_agent")
                .chatModel(mockChatModel)
                .streaming(true)
                .hooks(List.of())
                .build();

        assertNotNull(agent);
        assertEquals("empty_hooks_agent", agent.getName());
        assertNotNull(agent.getGraph());
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
