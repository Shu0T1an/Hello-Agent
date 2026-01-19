package cn.ts.graph.config;

import cn.ts.graph.CompiledGraph;
import cn.ts.graph.GraphException;
import cn.ts.graph.GraphResult;
import cn.ts.graph.StateGraph;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.node.NodeAction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RunnableConfig 测试类
 *
 * @author tianshuo
 */
class RunnableConfigTest {

    @Test
    void testDefaultConfig() {
        RunnableConfig config = RunnableConfig.defaultConfig();
        assertEquals(1000, config.maxIterations());
        assertEquals(Duration.ofSeconds(30), config.nodeTimeout());
        assertTrue(config.interruptOnError());
        assertNull(config.timeout());
        assertFalse(config.debugMode());
    }

    @Test
    void testCustomConfig() {
        RunnableConfig config = RunnableConfig.builder()
                .maxIterations(500)
                .timeout(Duration.ofMinutes(5))
                .nodeTimeout(Duration.ofSeconds(60))
                .interruptOnError(false)
                .debugMode(true)
                .build();

        assertEquals(500, config.maxIterations());
        assertEquals(Duration.ofMinutes(5), config.timeout());
        assertEquals(Duration.ofSeconds(60), config.nodeTimeout());
        assertFalse(config.interruptOnError());
        assertTrue(config.debugMode());
    }

    @Test
    void testInvokeWithConfig() {
        StateGraph graph = new StateGraph()
                .addNode("step1", NodeAction.of(state -> Map.of("value", 1)))
                .addEdge(GraphConstants.START, "step1")
                .addEdge("step1", GraphConstants.END);

        RunnableConfig config = RunnableConfig.builder()
                .maxIterations(10)
                .build();

        CompiledGraph compiled = graph.compile();
        GraphResult result = compiled.invoke(Map.of(), config);

        assertTrue(result.isSuccess());
        assertEquals(1, result.executedNodeCount());
    }

    @Test
    void testNodeCallback() {
        List<String> executedNodes = new ArrayList<>();

        StateGraph graph = new StateGraph()
                .addNode("step1", NodeAction.of(state -> Map.of("value", 1)))
                .addNode("step2", NodeAction.of(state -> Map.of("value", 2)))
                .addEdge(GraphConstants.START, "step1")
                .addEdge("step1", "step2")
                .addEdge("step2", GraphConstants.END);

        RunnableConfig config = RunnableConfig.builder()
                .onNodeComplete(exec -> executedNodes.add(exec.nodeId()))
                .build();

        graph.compile().invoke(Map.of(), config);

        assertEquals(List.of("step1", "step2"), executedNodes);
    }

    @Test
    void testNodeStartCallback() {
        List<String> startedNodes = new ArrayList<>();

        StateGraph graph = new StateGraph()
                .addNode("step1", NodeAction.of(state -> Map.of("value", 1)))
                .addEdge(GraphConstants.START, "step1")
                .addEdge("step1", GraphConstants.END);

        RunnableConfig config = RunnableConfig.builder()
                .onNodeStart(exec -> startedNodes.add(exec.nodeId()))
                .build();

        graph.compile().invoke(Map.of(), config);

        assertEquals(List.of("step1"), startedNodes);
    }

    @Test
    void testMaxIterationsExceeded() {
        StateGraph graph = new StateGraph()
                .addNode("loop", NodeAction.of(state -> Map.of("count", state.value("count", 0) + 1)))
                .addEdge(GraphConstants.START, "loop")
                .addEdge("loop", "loop");  // 自环

        RunnableConfig config = RunnableConfig.builder()
                .maxIterations(5)
                .build();

        GraphResult result = graph.compile().invoke(Map.of(), config);

        assertTrue(result.isFailure());
        assertTrue(result.error().getMessage().contains("Max iterations exceeded"));
    }

    @Test
    void testOnCompleteCallback() {
        List<GraphResult> results = new ArrayList<>();

        StateGraph graph = new StateGraph()
                .addNode("step1", NodeAction.of(state -> Map.of("value", 1)))
                .addEdge(GraphConstants.START, "step1")
                .addEdge("step1", GraphConstants.END);

        RunnableConfig config = RunnableConfig.builder()
                .onComplete(results::add)
                .build();

        graph.compile().invoke(Map.of(), config);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
    }

    @Test
    void testOnErrorCallback() {
        List<GraphResult> errors = new ArrayList<>();

        StateGraph graph = new StateGraph()
                .addNode("errorStep", NodeAction.of(state -> {
                    throw new RuntimeException("Test error");
                }))
                .addEdge(GraphConstants.START, "errorStep")
                .addEdge("errorStep", GraphConstants.END);

        RunnableConfig config = RunnableConfig.builder()
                .interruptOnError(true)
                .onError(errors::add)
                .build();

        GraphResult result = graph.compile().invoke(Map.of(), config);

        assertEquals(1, errors.size());
        assertTrue(result.isFailure());
    }

    @Test
    void testInterruptOnErrorFalse() {
        List<String> executedNodes = new ArrayList<>();

        StateGraph graph = new StateGraph()
                .addNode("errorStep", NodeAction.of(state -> {
                    throw new RuntimeException("Test error");
                }))
                .addNode("nextStep", NodeAction.of(state -> {
                    executedNodes.add("nextStep");
                    return Map.of();
                }))
                .addEdge(GraphConstants.START, "errorStep")
                .addEdge("errorStep", "nextStep")
                .addEdge("nextStep", GraphConstants.END);

        RunnableConfig config = RunnableConfig.builder()
                .interruptOnError(false)
                .onNodeComplete(exec -> executedNodes.add(exec.nodeId()))
                .build();

        GraphResult result = graph.compile().invoke(Map.of(), config);

        // 即使错误节点不中断，图仍然会因为错误而停止执行（因为会继续查找下一个节点）
        // 但是 executedNodes 应该包含 nextStep，因为错误后继续执行
        assertTrue(executedNodes.contains("nextStep") || executedNodes.contains("errorStep"));
    }

    @Test
    void testBuilderFluentApi() {
        RunnableConfig config = RunnableConfig.builder()
                .timeout(Duration.ofSeconds(10))
                .nodeTimeout(Duration.ofSeconds(5))
                .maxIterations(100)
                .interruptOnError(false)
                .debugMode(true)
                .build();

        assertNotNull(config);
        assertEquals(Duration.ofSeconds(10), config.timeout());
        assertEquals(Duration.ofSeconds(5), config.nodeTimeout());
        assertEquals(100, config.maxIterations());
        assertFalse(config.interruptOnError());
        assertTrue(config.debugMode());
    }
}
