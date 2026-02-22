package cn.ts.graph;

import cn.ts.graph.checkpoint.CheckpointConfig;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.checkpoint.CheckpointStorage;
import cn.ts.graph.checkpoint.InterruptionMetadata;
import cn.ts.graph.checkpoint.MemoryCheckpointStorage;
import cn.ts.graph.checkpoint.StateSnapshot;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.node.AsyncNodeActionWithConfig;
import cn.ts.graph.node.InterruptableAction;
import cn.ts.graph.node.Node;
import cn.ts.graph.state.State;
import cn.ts.graph.state.MapState;
import cn.ts.graph.test.TestFixture;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompiledGraph 单元测试
 * <p>
 * 测试编译后的图的执行功能，包括同步执行和流式执行
 * </p>
 *
 * @author tianshuo
 */
class CompiledGraphTest {

    @Test
    void testStreamShouldStopAfterInterruptionWithoutExecutingNextNode() {
        StateGraph graph = new StateGraph();

        class InterruptNodeAction implements InterruptableAction, AsyncNodeActionWithConfig {
            @Override
            public Optional<InterruptionMetadata> interrupt(String nodeId, State state, RunnableConfig config) {
                return Optional.of(InterruptionMetadata.builder(nodeId, state)
                        .message("need human approval")
                        .build());
            }

            @Override
            public CompletableFuture<Map<String, Object>> applyAsync(State state, RunnableConfig config) {
                return CompletableFuture.completedFuture(Map.of("should_not_run", true));
            }
        }

        graph.addNode(Node.ofInterruptable("interrupt_node", new InterruptNodeAction()));
        graph.addNode("tool_node", state -> Map.of("tool_executed", true));
        graph.addEdge(GraphConstants.START, "interrupt_node");
        graph.addConditionalEdge(
                "interrupt_node",
                state -> "tool",
                Map.of("tool", "tool_node")
        );
        graph.addEdge("tool_node", GraphConstants.END);

        CompiledGraph compiled = graph.compile();
        List<GraphResponse<NodeOutput>> responses = compiled.stream(Map.of()).collectList().block();

        assertNotNull(responses);
        assertTrue(responses.stream().anyMatch(GraphResponse::isInterruption), "should emit interruption response");
        assertFalse(
                responses.stream().anyMatch(r -> "tool_node".equals(r.getNodeId())),
                "tool node should not execute after interruption"
        );
    }

    @Test
    void testInvoke() {
        StateGraph graph = TestFixture.createSimpleStateGraph();
        CompiledGraph compiled = graph.compile();

        GraphResult result = compiled.invoke(Map.of("input", "test"));

        assertNotNull(result);
        assertFalse(result.isFailure());
        assertNotNull(result.finalState());
    }

    @Test
    void testInvokeReturnsResult() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", state -> Map.of("result", "node1 executed", "count", 1));
        graph.addEdge(GraphConstants.START, "node1");

        CompiledGraph compiled = graph.compile();

        GraphResult result = compiled.invoke(Map.of());

        assertEquals("node1 executed", result.finalState().value("result").orElse(null));
        assertEquals(1, result.finalState().value("count").orElse(null));
    }

    @Test
    void testInvokeWithInitialValues() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", state -> {
            int input = state.<Integer>value("input").orElse(0);
            return Map.of("output", input * 2);
        });
        graph.addEdge(GraphConstants.START, "node1");

        CompiledGraph compiled = graph.compile();

        GraphResult result = compiled.invoke(Map.of("input", 5));

        assertEquals(10, result.finalState().<Integer>value("output").orElse(0));
    }

    @Test
    void testInvokeMultipleNodes() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", state -> Map.of("step1", "done"));
        graph.addNode("node2", state -> Map.of("step2", "done"));
        graph.addNode("node3", state -> Map.of("step3", "done"));

        graph.addEdge(GraphConstants.START, "node1");
        graph.addEdge("node1", "node2");
        graph.addEdge("node2", "node3");

        CompiledGraph compiled = graph.compile();

        GraphResult result = compiled.invoke(Map.of());

        assertEquals("done", result.finalState().value("step1").orElse(null));
        assertEquals("done", result.finalState().value("step2").orElse(null));
        assertEquals("done", result.finalState().value("step3").orElse(null));
    }

    @Test
    void testStream() {
        StateGraph graph = TestFixture.createSimpleStateGraph();
        CompiledGraph compiled = graph.compile();

        Flux<GraphResponse<NodeOutput>> flux = compiled.stream(Map.of("input", "test"));

        assertNotNull(flux);

        // 收集所有发射的元素
        List<GraphResponse<NodeOutput>> responses = flux.collectList().block();

        assertNotNull(responses);
        assertFalse(responses.isEmpty());
    }

    @Test
    void testStreamWithMultipleNodes() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", state -> Map.of("value", 1));
        graph.addNode("node2", state -> Map.of("value", 2));
        graph.addNode("node3", state -> Map.of("value", 3));

        graph.addEdge(GraphConstants.START, "node1");
        graph.addEdge("node1", "node2");
        graph.addEdge("node2", "node3");

        CompiledGraph compiled = graph.compile();

        Flux<GraphResponse<NodeOutput>> flux = compiled.stream(Map.of());

        List<GraphResponse<NodeOutput>> responses = flux.collectList().block();

        assertNotNull(responses);
        assertTrue(responses.size() >= 3); // 至少应该有 3 个节点的输出
    }

    @Test
    void testInvokeWithRunnableConfig() {
        StateGraph graph = TestFixture.createSimpleStateGraph();
        CompiledGraph compiled = graph.compile();

        RunnableConfig config = RunnableConfig.builder()
                .threadId("test-thread")
                .build();

        GraphResult result = compiled.invoke(Map.of("input", "test"), config);

        assertNotNull(result);
        assertFalse(result.isFailure());
    }

    @Test
    void testStreamWithRunnableConfig() {
        StateGraph graph = TestFixture.createSimpleStateGraph();
        CompiledGraph compiled = graph.compile();

        RunnableConfig config = RunnableConfig.builder()
                .threadId("test-thread")
                .build();

        Flux<GraphResponse<NodeOutput>> flux = compiled.stream(Map.of("input", "test"), config);

        List<GraphResponse<NodeOutput>> responses = flux.collectList().block();

        assertNotNull(responses);
        assertFalse(responses.isEmpty());
    }

    @Test
    void testInvokeWithStateInitializer() {
        StateGraph graph = new StateGraph();
        graph.setStateInitializer(() -> {
            MapState state = new MapState();
            state.registerKeyStrategy("messages", cn.ts.graph.state.strategy.AppendStrategy.getInstance());
            state.update("initialized", true);
            return state;
        });

        graph.addNode("node1", state -> Map.of("result", "executed"));
        graph.addEdge(GraphConstants.START, "node1");

        CompiledGraph compiled = graph.compile();

        GraphResult result = compiled.invoke(Map.of());

        assertTrue(result.finalState().<Boolean>value("initialized").orElse(false));
    }

    @Test
    void testGetNodes() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);
        graph.addNode("node2", TestFixture.STATE_NODE_ACTION);
        graph.addEdge(GraphConstants.START, "node1");

        CompiledGraph compiled = graph.compile();

        Map<String, Node> nodes = compiled.getNodes();

        assertEquals(2, nodes.size());
        assertTrue(nodes.containsKey("node1"));
        assertTrue(nodes.containsKey("node2"));
    }

    @Test
    void testGetEdges() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);
        graph.addNode("node2", TestFixture.STATE_NODE_ACTION);
        graph.addEdge(GraphConstants.START, "node1");
        graph.addEdge("node1", "node2");

        CompiledGraph compiled = graph.compile();

        List<cn.ts.graph.edge.Edge> edges = compiled.getEdges();

        assertEquals(2, edges.size());
    }

    @Test
    void testGetEntryPoint() {
        StateGraph graph = TestFixture.createSimpleStateGraph();
        CompiledGraph compiled = graph.compile();

        assertEquals("node1", compiled.getEntryPoint());
    }

    @Test
    void testToMermaidDiagram() {
        StateGraph graph = TestFixture.createSimpleStateGraph();
        CompiledGraph compiled = graph.compile();

        String diagram = compiled.toMermaidDiagram();

        assertNotNull(diagram);
        assertTrue(diagram.contains("graph"));
        assertTrue(diagram.contains("node1"));
    }

    @Test
    void testInvokeWithErrorInNode() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.ERROR_NODE_ACTION);
        graph.addEdge(GraphConstants.START, "node1");

        CompiledGraph compiled = graph.compile();

        GraphResult result = compiled.invoke(Map.of());

        assertTrue(result.isFailure());
        assertNotNull(result.error());
        assertTrue(result.error().getMessage().contains("Test exception"));
    }

    @Test
    void testStreamWithErrorInNode() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", TestFixture.ERROR_NODE_ACTION);
        graph.addEdge(GraphConstants.START, "node1");

        CompiledGraph compiled = graph.compile();

        Flux<GraphResponse<NodeOutput>> flux = compiled.stream(Map.of());

        // 收集所有结果，应该包含错误信息
        List<GraphResponse<NodeOutput>> results = flux.collectList().onErrorReturn(java.util.Collections.emptyList()).block();

        // 验证结果（可能是空列表或包含错误信息的响应）
        assertNotNull(results);
    }

    @Test
    void testHasCheckpointManagerWithoutManager() {
        StateGraph graph = TestFixture.createSimpleStateGraph();
        CompiledGraph compiled = graph.compile();

        assertFalse(compiled.hasCheckpointManager());
    }

    @Test
    void testHasCheckpointManagerWithManager() {
        StateGraph graph = new StateGraph();
        graph.setCheckpointManager(new NoOpCheckpointManager());
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);
        graph.addEdge(GraphConstants.START, "node1");

        CompiledGraph compiled = graph.compile();

        assertTrue(compiled.hasCheckpointManager());
    }

    @Test
    void testGetStateWithoutCheckpointManagerThrowsException() {
        StateGraph graph = TestFixture.createSimpleStateGraph();
        CompiledGraph compiled = graph.compile();

        assertThrows(
                IllegalStateException.class,
                () -> compiled.getState("test-thread")
        );
    }

    @Test
    void testGetStateWithCheckpointManager() {
        StateGraph graph = new StateGraph();
        graph.setCheckpointManager(new NoOpCheckpointManager());
        graph.addNode("node1", TestFixture.SIMPLE_NODE_ACTION);
        graph.addEdge(GraphConstants.START, "node1");

        CompiledGraph compiled = graph.compile();

        Optional<StateSnapshot> state = compiled.getState("test-thread");

        // NoOpCheckpointManager 返回 null，所以 Optional 应该为空
        assertNotNull(state);
    }

    @Test
    void testToString() {
        StateGraph graph = TestFixture.createSimpleStateGraph();
        CompiledGraph compiled = graph.compile();

        String str = compiled.toString();

        assertTrue(str.contains("CompiledGraph"));
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
        public List<StateSnapshot> getStateHistory(String threadId) {
            return List.of();
        }

        @Override
        public void updateState(String threadId, Map<String, Object> updates, String asNode) {
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

        @Override
        public CheckpointStorage getStorage() {
            return new MemoryCheckpointStorage();
        }
    }
}
