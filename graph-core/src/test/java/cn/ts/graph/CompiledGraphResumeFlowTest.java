package cn.ts.graph;

import cn.ts.graph.checkpoint.InterruptionMetadata;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.node.AsyncNodeActionWithConfig;
import cn.ts.graph.node.InterruptableAction;
import cn.ts.graph.node.Node;
import cn.ts.graph.state.State;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledGraphResumeFlowTest {

    @Test
    void resumeFromInterruptNodeShouldContinueWhenFeedbackDataPresent() {
        StateGraph graph = new StateGraph();

        class ResumeSensitiveAction implements InterruptableAction, AsyncNodeActionWithConfig {
            @Override
            public Optional<InterruptionMetadata> interrupt(String nodeId, State state, RunnableConfig config) {
                Map<String, Object> feedbackData = config.feedbackData();
                if (feedbackData == null || feedbackData.isEmpty()) {
                    return Optional.of(InterruptionMetadata.builder(nodeId, state)
                            .message("need clarification")
                            .build());
                }
                return Optional.empty();
            }

            @Override
            public CompletableFuture<Map<String, Object>> applyAsync(State state, RunnableConfig config) {
                Map<String, Object> feedbackData = config.feedbackData();
                boolean hasFeedback = feedbackData != null && !feedbackData.isEmpty();
                return CompletableFuture.completedFuture(Map.of(
                        "resume_processed", hasFeedback,
                        "seen_mode", hasFeedback ? String.valueOf(feedbackData.get("mode")) : "none"
                ));
            }
        }

        graph.addNode(Node.ofInterruptable("interrupt_node", new ResumeSensitiveAction()));
        graph.addNode("processed_node", state -> Map.of("processed_node_executed", true));
        graph.addEdge(GraphConstants.START, "interrupt_node");
        graph.addConditionalEdge(
                "interrupt_node",
                state -> state.<Boolean>value("resume_processed").orElse(false) ? "processed" : "end",
                Map.of("processed", "processed_node", "end", GraphConstants.END)
        );
        graph.addEdge("processed_node", GraphConstants.END);

        CompiledGraph compiled = graph.compile();

        List<GraphResponse<NodeOutput>> firstRunResponses = compiled.stream(Map.of()).collectList().block();
        assertNotNull(firstRunResponses);
        assertTrue(firstRunResponses.stream().anyMatch(GraphResponse::isInterruption));
        assertFalse(firstRunResponses.stream().anyMatch(response -> "processed_node".equals(response.getNodeId())));

        RunnableConfig resumeConfig = RunnableConfig.builder()
                .startNode("interrupt_node")
                .threadId("thread-1")
                .feedbackData(Map.of(
                        "mode", "clarification_qa",
                        "clarificationAnswers", List.of(Map.of("id", "q1", "answer", "100"))
                ))
                .build();

        List<GraphResponse<NodeOutput>> resumeResponses = compiled.stream(Map.of(), resumeConfig).collectList().block();
        assertNotNull(resumeResponses);
        assertFalse(resumeResponses.stream().anyMatch(GraphResponse::isInterruption));
        assertTrue(resumeResponses.stream().anyMatch(response -> "processed_node".equals(response.getNodeId())));
    }
}
