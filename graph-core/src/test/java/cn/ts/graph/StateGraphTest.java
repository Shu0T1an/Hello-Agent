package cn.ts.graph;

import cn.ts.graph.checkpoint.CheckpointConfig;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.checkpoint.StateSnapshot;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.edge.Edge;
import cn.ts.graph.node.Node;
import cn.ts.graph.test.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StateGraph 单元测试
 * <p>
 * 测试图构建器的各项功能，包括节点和边的添加、验证和编译
 * </p>
 *
 * @author tianshuo
 */
class StateGraphTest {

    @Test
    void testAddNode() {
        StateGraph graph = new StateGraph();
        StateGraph result = graph.addNode("node1", state -> Map.of("result", "executed"));

        // 验证链式调用返回自身
        assertSame(graph, result);

        // 验证节点已添加
        assertTrue(graph.getNodes().containsKey("node1"));
        assertEquals(1, graph.getNodes().size());
    }

    @Test
    void testAddNodeWithDescription() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", state -> Map.of("result", "executed"), "This is node 1");

        // 验证节点已添加
        assertTrue(graph.getNodes().containsKey("node1"));

        // 验证描述
        assertEquals("This is node 1", graph.getNodes().get("node1").description());
    }

    @Test
    void testAddDuplicateNodeThrowsException() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", state -> Map.of("result", "executed"));

        // 尝试添加重复节点应该抛出异常
        GraphException.EdgeConfigurationException exception = assertThrows(
                GraphException.EdgeConfigurationException.class,
                () -> graph.addNode("node1", state -> Map.of("result", "executed"))
        );

        assertTrue(exception.getMessage().contains("Node already exists"));
    }

    @Test
    void testAddNodeWithNullIdThrowsException() {
        StateGraph graph = new StateGraph();

        // null ID 应该抛出异常
        assertThrows(
                GraphException.EdgeConfigurationException.class,
                () -> graph.addNode(null, state -> Map.of("result", "executed"))
        );
    }

    @Test
    void testAddNodeWithEmptyIdThrowsException() {
        StateGraph graph = new StateGraph();

        // 空字符串 ID 应该抛出异常
        assertThrows(
                GraphException.EdgeConfigurationException.class,
                () -> graph.addNode("   ", state -> Map.of("result", "executed"))
        );
    }

    @Test
    void testAddEdge() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);
        graph.addNode("node2", TestFixture.SIMPLE_NODE_ACTION);

        graph.addEdge("node1", "node2");

        // 验证边已添加
        assertEquals(1, graph.getEdges().size());
        assertEquals("node1", graph.getEdges().get(0).from());
        assertEquals("node2", graph.getEdges().get(0).to());
    }

    @Test
    void testAddConditionalEdge() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);
        graph.addNode("node2", TestFixture.SIMPLE_NODE_ACTION);
        graph.addNode("node3", TestFixture.SIMPLE_NODE_ACTION);

        Map<String, String> routeMapping = Map.of(
                "continue", "node2",
                "end", GraphConstants.END
        );

        graph.addConditionalEdge("node1", state -> "continue", routeMapping);

        // 验证条件边已添加
        assertEquals(1, graph.getEdges().size());
        assertTrue(graph.getEdges().get(0).isConditional());
    }

    @Test
    void testAddEdgeFromNonExistentNodeThrowsException() {
        StateGraph graph = new StateGraph();
        graph.addNode("node2", TestFixture.SIMPLE_NODE_ACTION);

        // 从不存在的节点添加边应该抛出异常
        assertThrows(
                GraphException.NodeNotFoundException.class,
                () -> graph.addEdge("node1", "node2")
        );
    }

    @Test
    void testAddEdgeToNonExistentNodeThrowsException() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);

        // 添加边到不存在的节点应该抛出异常
        assertThrows(
                GraphException.NodeNotFoundException.class,
                () -> graph.addEdge("node1", "node2")
        );
    }

    @Test
    void testSetStateInitializer() {
        StateGraph graph = new StateGraph();

        StateGraph result = graph.setStateInitializer(TestFixture.BASIC_STATE_INITIALIZER);

        // 验证链式调用返回自身
        assertSame(graph, result);

        // 验证编译时使用了自定义初始化器
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);
        graph.addEdge(GraphConstants.START, "node1");

        CompiledGraph compiled = graph.compile();
        assertNotNull(compiled);
    }

    @Test
    void testSetCheckpointManager() {
        StateGraph graph = new StateGraph();
        CheckpointManager mockManager = new NoOpCheckpointManager();

        StateGraph result = graph.setCheckpointManager(mockManager);

        // 验证链式调用返回自身
        assertSame(graph, result);

        // 验证编译时使用了检查点管理器
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);
        graph.addEdge(GraphConstants.START, "node1");

        CompiledGraph compiled = graph.compile();
        assertTrue(compiled.hasCheckpointManager());
    }

    @Test
    void testCompile() {
        StateGraph graph = TestFixture.createSimpleStateGraph();

        CompiledGraph compiled = graph.compile();

        assertNotNull(compiled);
        assertEquals("node1", compiled.getEntryPoint());
        assertTrue(compiled.getNodes().containsKey("node1"));
        assertEquals(1, compiled.getEdges().size());
    }

    @Test
    void testCompileEmptyGraphThrowsException() {
        StateGraph graph = new StateGraph();

        // 空图编译应该抛出异常
        GraphException.GraphCompileException exception = assertThrows(
                GraphException.GraphCompileException.class,
                graph::compile
        );

        assertTrue(exception.getMessage().contains("at least one node"));
    }

    @Test
    void testCompileWithoutEntryPointThrowsException() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);

        // 没有入口点的图编译应该抛出异常
        GraphException.GraphCompileException exception = assertThrows(
                GraphException.GraphCompileException.class,
                graph::compile
        );

        assertTrue(exception.getMessage().contains("entry point"));
    }

    @Test
    void testGetNodesReturnsUnmodifiableMap() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);

        Map<String, Node> nodes = graph.getNodes();

        // 尝试修改返回的 Map 应该抛出异常
        assertThrows(UnsupportedOperationException.class, () -> nodes.put("node2", Node.of("node2", TestFixture.SIMPLE_NODE_ACTION)));
    }

    @Test
    void testGetEdgesReturnsUnmodifiableList() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);
        graph.addEdge(GraphConstants.START, "node1");

        // 尝试修改返回的 List 应该抛出异常
        assertThrows(UnsupportedOperationException.class, () -> graph.getEdges().add(Edge.of("node1", "node2")));
    }

    @Test
    void testAddEdgeFromStartSetsEntryPoint() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);

        graph.addEdge(GraphConstants.START, "node1");

        // 验证入口点已设置
        assertEquals("node1", graph.getEntryPoint());
    }

    @Test
    void testAddConditionalEdgeFromStartSetsEntryPoint() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);
        graph.addNode("node2", TestFixture.SIMPLE_NODE_ACTION);

        Map<String, String> routeMapping = Map.of("continue", "node1", "end", "node2");

        graph.addConditionalEdge(GraphConstants.START, state -> "continue", routeMapping);

        // 验证入口点已设置（取第一个路由目标）
        assertNotNull(graph.getEntryPoint());
    }

    @Test
    void testToMermaidDiagram() {
        StateGraph graph = TestFixture.createSimpleStateGraph();

        String diagram = graph.toMermaidDiagram();

        assertNotNull(diagram);
        assertTrue(diagram.contains("graph"));
        assertTrue(diagram.contains("node1"));
    }

    @Test
    void testMultipleNodesAndEdges() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);
        graph.addNode("node2", TestFixture.STATE_NODE_ACTION);
        graph.addNode("node3", TestFixture.EMPTY_NODE_ACTION);

        graph.addEdge(GraphConstants.START, "node1");
        graph.addEdge("node1", "node2");
        graph.addEdge("node2", "node3");

        CompiledGraph compiled = graph.compile();

        assertEquals(3, compiled.getNodes().size());
        assertEquals(3, compiled.getEdges().size());
        assertEquals("node1", compiled.getEntryPoint());
    }

    @Test
    void testAddEdgeWithSpecialNodes() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);

        // START 和 END 是特殊节点，不需要添加
        graph.addEdge(GraphConstants.START, "node1");
        graph.addEdge("node1", GraphConstants.END);

        assertEquals(2, graph.getEdges().size());
    }

    @Test
    void testConditionalEdgeWithNullRouteMappingThrowsException() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);

        // null 路由映射应该抛出异常
        assertThrows(
                GraphException.EdgeConfigurationException.class,
                () -> graph.addConditionalEdge("node1", state -> "continue", null)
        );
    }

    @Test
    void testConditionalEdgeWithEmptyRouteMappingThrowsException() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);

        // 空路由映射应该抛出异常
        assertThrows(
                GraphException.EdgeConfigurationException.class,
                () -> graph.addConditionalEdge("node1", state -> "continue", Map.of())
        );
    }

    @Test
    void testToString() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);
        graph.addEdge(GraphConstants.START, "node1");

        String str = graph.toString();

        assertTrue(str.contains("StateGraph"));
        assertTrue(str.contains("node1"));
    }

    /**
     * 无操作的 CheckpointManager，用于测试
     */
    private static class NoOpCheckpointManager implements CheckpointManager {
        @Override
        public String createCheckpoint(GraphRunnerContext context, String source) {
            return "checkpoint-id";
        }

        @Override
        public GraphRunnerContext restoreContext(String threadId, String checkpointId) {
            return null;
        }

        @Override
        public Optional<StateSnapshot> getState(String threadId) {
            return Optional.empty();
        }

        @Override
        public java.util.List<StateSnapshot> getStateHistory(String threadId) {
            return java.util.List.of();
        }

        @Override
        public void updateState(String threadId, java.util.Map<String, Object> updates, String asNode) {
        }

        @Override
        public void deleteCheckpoint(String threadId, String checkpointId) {
        }

        @Override
        public void deleteThread(String threadId) {
        }

        @Override
        public boolean shouldCheckpoint(String nodeId) {
            return false;
        }

        @Override
        public boolean shouldCheckpointOnError() {
            return false;
        }

        @Override
        public CheckpointConfig getConfig() {
            return CheckpointConfig.builder().strategy(CheckpointConfig.CheckpointStrategy.MANUAL).build();
        }
    }
}
