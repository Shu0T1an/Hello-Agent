package cn.ts.graph;

import cn.ts.graph.checkpoint.InterruptionMetadata;
import cn.ts.graph.node.AsyncNodeActionWithConfig;
import cn.ts.graph.node.InterruptableAction;
import cn.ts.graph.node.Node;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NodeExecutor 中断机制测试
 *
 * @author tianshuo
 */
class NodeExecutorTest {

    /**
     * 测试可中断节点返回中断响应
     */
    @Test
    void testInterruptableActionReturnsInterruption() {
        NodeExecutor executor = NodeExecutor.create();

        // 创建一个会中断的动作
        // 使用匿名类同时实现 InterruptableAction 和 AsyncNodeActionWithConfig
        class TestInterruptableAction implements InterruptableAction, AsyncNodeActionWithConfig {
            @Override
            public Optional<InterruptionMetadata> interrupt(String nodeId, State state, cn.ts.graph.config.RunnableConfig config) {
                InterruptionMetadata metadata = InterruptionMetadata.builder(nodeId, state)
                        .message("需要审批")
                        .build();
                return Optional.of(metadata);
            }

            @Override
            public CompletableFuture<Map<String, Object>> applyAsync(State state, cn.ts.graph.config.RunnableConfig config) {
                return CompletableFuture.completedFuture(Map.of());
            }
        }

        InterruptableAction interruptable = new TestInterruptableAction();

        // 使用 Node.ofInterruptable 创建节点以保留 InterruptableAction 类型
        Node node = Node.ofInterruptable("__test__", interruptable);
        State state = new MapState();
        GraphRunnerContext context = GraphRunnerContext.create(state, cn.ts.graph.config.RunnableConfig.defaultConfig());
        context.setCurrentNodeId("__test__");

        Flux<GraphResponse<NodeOutput>> result = executor.execute(node, context);
        java.util.List<GraphResponse<NodeOutput>> results = result.collectList().block();

        assertNotNull(results);
        // 检查是否有中断响应
        boolean hasInterruption = results.stream()
                .anyMatch(r -> r.type() == GraphResponse.ResponseType.INTERRUPTION);
        assertTrue(hasInterruption, "应该包含中断响应");
    }

    /**
     * 测试可中断节点不中断时正常执行
     */
    @Test
    void testInterruptableActionNoInterruption() {
        NodeExecutor executor = NodeExecutor.create();

        // 创建一个不会中断的动作
        // 使用匿名类同时实现 InterruptableAction 和 AsyncNodeActionWithConfig
        class TestNoInterruptAction implements InterruptableAction, AsyncNodeActionWithConfig {
            @Override
            public Optional<InterruptionMetadata> interrupt(String nodeId, State state, cn.ts.graph.config.RunnableConfig config) {
                return Optional.empty();
            }

            @Override
            public CompletableFuture<Map<String, Object>> applyAsync(State state, cn.ts.graph.config.RunnableConfig config) {
                return CompletableFuture.completedFuture(Map.of("result", "success"));
            }
        }

        InterruptableAction interruptable = new TestNoInterruptAction();

        // 使用 Node.ofInterruptable 创建节点以保留 InterruptableAction 类型
        Node node = Node.ofInterruptable("__test__", interruptable);
        State state = new MapState();
        GraphRunnerContext context = GraphRunnerContext.create(state, cn.ts.graph.config.RunnableConfig.defaultConfig());
        context.setCurrentNodeId("__test__");

        Flux<GraphResponse<NodeOutput>> result = executor.execute(node, context);
        java.util.List<GraphResponse<NodeOutput>> results = result.collectList().block();

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertNotEquals(GraphResponse.ResponseType.INTERRUPTION, results.get(0).type());
    }
}
