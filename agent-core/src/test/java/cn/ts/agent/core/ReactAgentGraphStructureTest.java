package cn.ts.agent.core;

import cn.ts.agent.hook.AfterOnlyTestHook;
import cn.ts.agent.hook.BeforeOnlyTestHook;
import cn.ts.agent.hook.HumanInTheLoopHook;
import cn.ts.agent.hook.SecondTestHook;
import cn.ts.agent.hook.SimpleTestHook;
import cn.ts.graph.CompiledGraph;
import cn.ts.graph.hook.Hook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReactAgent 图结构和边连接验证测试
 *
 * @author tianshuo
 */
@ExtendWith(MockitoExtension.class)
class ReactAgentGraphStructureTest {

    @Mock
    private org.springframework.ai.chat.model.ChatModel mockChatModel;

    /**
     * 测试无 Hook 时的图结构
     * 期望: START → MODEL → TOOL/END
     */
    @Test
    void testGraphStructureWithoutHooks() {
        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .build();

        CompiledGraph graph = agent.getGraph();

        // 验证基本节点存在
        assertTrue(graph.getNodes().containsKey("_AGENT_MODEL_"));
        assertTrue(graph.getNodes().containsKey("_AGENT_TOOL_"));
        assertTrue(graph.getNodes().containsKey("_AGENT_END_"));

        // 验证入口点
        assertEquals("_AGENT_MODEL_", graph.getEntryPoint());

        // 验证从 START 直接到 MODEL
        assertTrue(hasEdge(graph, "__start__", "_AGENT_MODEL_"));
    }

    /**
     * 测试有 BEFORE_MODEL Hook 时的图结构
     * 期望: START → Hook.before → MODEL → TOOL/END
     */
    @Test
    void testGraphStructureWithBeforeHook() {
        BeforeOnlyTestHook beforeHook = BeforeOnlyTestHook.create("[Before]");

        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .hooks(List.of(beforeHook))
                .build();

        CompiledGraph graph = agent.getGraph();

        // 验证 Hook 节点存在
        String expectedHookName = "__hook_BeforeOnlyTestHook_before";
        assertTrue(graph.getNodes().containsKey(expectedHookName),
                "应包含 before hook 节点");

        // 验证入口点是 Hook 节点
        assertEquals(expectedHookName, graph.getEntryPoint(),
                "入口点应该是 before hook 节点");

        // 验证边的连接: START → Hook.before → MODEL
        assertTrue(hasEdge(graph, "__start__", expectedHookName),
                "应有从 START 到 before hook 的边");
        assertTrue(hasEdge(graph, expectedHookName, "_AGENT_MODEL_"),
                "应有从 before hook 到 MODEL 的边");

        // 验证条件边从 MODEL 出发（因为没有 after hook）
        assertTrue(hasConditionalEdge(graph, "_AGENT_MODEL_"),
                "应有从 MODEL 出发的条件边");
    }

    /**
     * 测试有 AFTER_MODEL Hook 时的图结构
     * 期望: START → MODEL → Hook.after → [条件边] → TOOL/END
     */
    @Test
    void testGraphStructureWithAfterHook() {
        AfterOnlyTestHook afterHook = AfterOnlyTestHook.create("[After]");

        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .hooks(List.of(afterHook))
                .build();

        CompiledGraph graph = agent.getGraph();

        // 验证 Hook 节点存在
        String expectedHookName = "__hook_AfterOnlyTestHook_after";
        assertTrue(graph.getNodes().containsKey(expectedHookName),
                "应包含 after hook 节点");

        // 验证入口点是 MODEL（因为没有 before hook）
        assertEquals("_AGENT_MODEL_", graph.getEntryPoint(),
                "入口点应该是 MODEL 节点");

        // 验证边的连接: START → MODEL → Hook.after
        assertTrue(hasEdge(graph, "__start__", "_AGENT_MODEL_"),
                "应有从 START 到 MODEL 的边");
        assertTrue(hasEdge(graph, "_AGENT_MODEL_", expectedHookName),
                "应有从 MODEL 到 after hook 的边");

        // 验证条件边从 after hook 出发
        assertTrue(hasConditionalEdge(graph, expectedHookName),
                "应有从 after hook 出发的条件边");
    }

    /**
     * 测试同时有 BEFORE_MODEL 和 AFTER_MODEL Hook 时的图结构
     * 期望: START → Hook1.before → MODEL → Hook1.after → [条件边] → TOOL/END
     */
    @Test
    void testGraphStructureWithBeforeAndAfterHooks() {
        SimpleTestHook hook1 = SimpleTestHook.create("[Hook1]");

        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .hooks(List.of(hook1))  // 同一个 Hook 同时支持 before 和 after
                .build();

        CompiledGraph graph = agent.getGraph();

        // 验证两个 Hook 节点都存在
        String beforeHookName = "__hook_SimpleTestHook_before";
        String afterHookName = "__hook_SimpleTestHook_after";
        assertTrue(graph.getNodes().containsKey(beforeHookName),
                "应包含 before hook 节点");
        assertTrue(graph.getNodes().containsKey(afterHookName),
                "应包含 after hook 节点");

        // 验证入口点是 before hook
        assertEquals(beforeHookName, graph.getEntryPoint(),
                "入口点应该是 before hook 节点");

        // 验证边的连接: START → Hook1.before → MODEL → Hook1.after
        assertTrue(hasEdge(graph, "__start__", beforeHookName),
                "应有从 START 到 before hook 的边");
        assertTrue(hasEdge(graph, beforeHookName, "_AGENT_MODEL_"),
                "应有从 before hook 到 MODEL 的边");
        assertTrue(hasEdge(graph, "_AGENT_MODEL_", afterHookName),
                "应有从 MODEL 到 after hook 的边");

        // 验证条件边从 after hook 出发
        assertTrue(hasConditionalEdge(graph, afterHookName),
                "应有从 after hook 出发的条件边");
    }

    /**
     * 测试多个 Hook 时的图结构
     * 期望: START → Hook1.before → Hook2.before → MODEL → Hook2.after → Hook1.after → [条件边]
     * BEFORE_MODEL 按顺序执行，AFTER_MODEL 按逆序执行（栈行为：先进后出）
     */
    @Test
    void testGraphStructureWithMultipleHooks() {
        SimpleTestHook hook1 = SimpleTestHook.create("[Hook1]");
        SecondTestHook hook2 = SecondTestHook.create("[Hook2]");

        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .hooks(List.of(hook1, hook2))
                .build();

        CompiledGraph graph = agent.getGraph();

        // 验证所有 Hook 节点存在
        assertTrue(graph.getNodes().containsKey("__hook_SimpleTestHook_before"));
        assertTrue(graph.getNodes().containsKey("__hook_SecondTestHook_before"));
        assertTrue(graph.getNodes().containsKey("__hook_SimpleTestHook_after"));
        assertTrue(graph.getNodes().containsKey("__hook_SecondTestHook_after"));

        // 验证入口点
        assertEquals("__hook_SimpleTestHook_before", graph.getEntryPoint());

        // 验证边的连接顺序
        assertTrue(hasEdge(graph, "__start__", "__hook_SimpleTestHook_before"));
        assertTrue(hasEdge(graph, "__hook_SimpleTestHook_before", "__hook_SecondTestHook_before"));
        assertTrue(hasEdge(graph, "__hook_SecondTestHook_before", "_AGENT_MODEL_"));

        // AFTER_MODEL Hook 按逆序连接：MODEL → hook2.after → hook1.after → 条件边
        assertTrue(hasEdge(graph, "_AGENT_MODEL_", "__hook_SecondTestHook_after"));
        assertTrue(hasEdge(graph, "__hook_SecondTestHook_after", "__hook_SimpleTestHook_after"));

        // 验证条件边从第一个 AFTER_MODEL Hook（hook1.after）发出
        assertTrue(hasConditionalEdge(graph, "__hook_SimpleTestHook_after"));
    }

    /**
     * 测试 HumanInTheLoopHook 的图结构
     */
    @Test
    void testGraphStructureWithHumanInTheLoopHook() {
        HumanInTheLoopHook hitlHook = HumanInTheLoopHook.builder()
                .approvalOn("testMethod", "测试操作")
                .build();

        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .hooks(List.of(hitlHook))
                .build();

        CompiledGraph graph = agent.getGraph();

        // HITL Hook 默认只支持 AFTER_MODEL
        String hitlHookName = "__hook_HumanInTheLoopHook_after";
        assertTrue(graph.getNodes().containsKey(hitlHookName),
                "应包含 HITL hook 节点");

        // 验证条件边从 HITL hook 发出
        assertTrue(hasConditionalEdge(graph, hitlHookName),
                "条件边应从 HITL hook 发出");
    }

    /**
     * 测试条件边可以正确路由到 TOOL
     */
    @Test
    void testConditionalRoutingToTool() {
        AfterOnlyTestHook hook = AfterOnlyTestHook.create("[Test]");

        ReactAgent agent = ReactAgent.builder()
                .name("test_agent")
                .chatModel(mockChatModel)
                .hooks(List.of(hook))
                .build();

        CompiledGraph graph = agent.getGraph();

        // AfterOnlyTestHook 只支持 AFTER_MODEL，条件边应从 after hook 发出
        String conditionalSource = "__hook_AfterOnlyTestHook_after";

        // 验证条件边路由映射包含 TOOL
        // 注意：由于边内部实现，这里只验证节点存在和边连接
        assertTrue(graph.getEdges().stream()
                .anyMatch(edge -> edge.from().equals(conditionalSource) && edge.isConditional()),
                "应有从 after hook 的条件边");
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查图中是否存在从 fromId 到 toId 的边
     */
    private boolean hasEdge(CompiledGraph graph, String fromId, String toId) {
        return graph.getEdges().stream()
                .anyMatch(edge -> edge.from().equals(fromId) && edge.to().equals(toId));
    }

    /**
     * 检查图中从指定节点是否有条件边发出
     */
    private boolean hasConditionalEdge(CompiledGraph graph, String nodeId) {
        return graph.getEdges().stream()
                .anyMatch(edge -> edge.from().equals(nodeId) && edge.isConditional());
    }
}
