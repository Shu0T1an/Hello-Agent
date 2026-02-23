package cn.ts.graph;

import cn.ts.graph.checkpoint.InterruptionMetadata;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.execution.NodeExecutor;
import cn.ts.graph.node.AsyncNodeActionWithConfig;
import cn.ts.graph.node.InterruptableAction;
import cn.ts.graph.node.Node;
import cn.ts.graph.node.NodeAction;
import cn.ts.graph.node.NodeActionWithConfig;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NodeExecutorConfigPropagationTest {

    @Test
    void interruptableNodeShouldReceiveRuntimeConfigOnResumePath() {
        NodeExecutor executor = NodeExecutor.create();

        class ResumeAwareAction implements InterruptableAction, AsyncNodeActionWithConfig {
            @Override
            public Optional<InterruptionMetadata> interrupt(String nodeId, State state, RunnableConfig config) {
                return Optional.empty();
            }

            @Override
            public CompletableFuture<Map<String, Object>> applyAsync(State state, RunnableConfig config) {
                Map<String, Object> feedbackData = config.feedbackData();
                String mode = feedbackData == null ? null : String.valueOf(feedbackData.get("mode"));
                return CompletableFuture.completedFuture(Map.of(
                        "received_mode", mode == null ? "null" : mode,
                        "received_thread_id", config.threadId() == null ? "null" : config.threadId()
                ));
            }
        }

        Node node = Node.ofInterruptable("__test__", new ResumeAwareAction());
        RunnableConfig config = RunnableConfig.builder()
                .threadId("thread-1")
                .feedbackData(Map.of(
                        "mode", "clarification_qa",
                        "clarificationAnswers", List.of(Map.of("id", "q1", "answer", "100"))
                ))
                .build();

        GraphRunnerContext context = GraphRunnerContext.create(new MapState(), config);
        context.setCurrentNodeId("__test__");

        List<GraphResponse<NodeOutput>> responses = executor.execute(node, context).collectList().block();
        assertNotNull(responses);

        GraphResponse<NodeOutput> completed = responses.stream()
                .filter(response -> response.getData() != null && response.getData().getStatus() == NodeStatus.COMPLETED)
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        Map<String, Object> updates = (Map<String, Object>) completed.getData().getResultValue();
        assertEquals("clarification_qa", updates.get("received_mode"));
        assertEquals("thread-1", updates.get("received_thread_id"));
    }

    @Test
    void nodeActionWithConfigShouldReceiveRuntimeConfigOnStreamPath() {
        NodeExecutor executor = NodeExecutor.create();

        class SyncConfigAwareAction implements NodeAction, NodeActionWithConfig {
            @Override
            public Map<String, Object> apply(State state) {
                return Map.of(
                        "route", "legacy",
                        "received_thread_id", "null",
                        "received_execution_id", "null"
                );
            }

            @Override
            public Map<String, Object> apply(State state, RunnableConfig config) {
                return Map.of(
                        "route", "config",
                        "received_thread_id", config.threadId() == null ? "null" : config.threadId(),
                        "received_execution_id", config.executionId() == null ? "null" : config.executionId()
                );
            }
        }

        Node node = Node.of("__sync_config__", new SyncConfigAwareAction());
        RunnableConfig config = RunnableConfig.builder()
                .threadId("thread-2")
                .executionId("exec-2")
                .build();

        GraphRunnerContext context = GraphRunnerContext.create(new MapState(), config);
        context.setCurrentNodeId("__sync_config__");

        List<GraphResponse<NodeOutput>> responses = executor.execute(node, context).collectList().block();
        assertNotNull(responses);

        GraphResponse<NodeOutput> completed = responses.stream()
                .filter(response -> response.getData() != null && response.getData().getStatus() == NodeStatus.COMPLETED)
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        Map<String, Object> updates = (Map<String, Object>) completed.getData().getResultValue();
        assertEquals("config", updates.get("route"));
        assertEquals("thread-2", updates.get("received_thread_id"));
        assertEquals("exec-2", updates.get("received_execution_id"));
    }
}
