package cn.ts.agent.extension.tools;

import cn.ts.agent.core.ReactAgent;
import cn.ts.agent.extension.progress.SubAgentProgressEvent;
import cn.ts.agent.extension.progress.SubAgentProgressReporter;
import cn.ts.agent.tool.ToolContextConstants;
import cn.ts.graph.CompiledGraph;
import cn.ts.graph.GraphResponse;
import cn.ts.graph.NodeOutput;
import cn.ts.graph.StreamingOutput;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.node.Node;
import cn.ts.graph.node.NodeAction;
import cn.ts.graph.state.MapState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskToolTest {

    private List<SubAgentProgressEvent> emittedEvents;
    private SubAgentProgressReporter reporter;
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        emittedEvents = new ArrayList<>();
        reporter = (executionId, event) -> emittedEvents.add(event);

        @SuppressWarnings("unchecked")
        ToolContext mockedToolContext = mock(ToolContext.class);
        Map<String, Object> toolState = new HashMap<>();
        toolState.put("executionId", "exec-123");
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put(ToolContextConstants.TOOL_STATE_CONTEXT_KEY, toolState);
        contextMap.put(ToolContextConstants.TOOL_CALL_ID_CONTEXT_KEY, "tool-call-001");
        when(mockedToolContext.getContext()).thenReturn(contextMap);
        toolContext = mockedToolContext;
    }

    @Test
    void routesToSpecifiedSubagentType() {
        ReactAgent subAgent = mock(ReactAgent.class);
        CompiledGraph subGraph = mock(CompiledGraph.class);
        when(subAgent.getGraph()).thenReturn(subGraph);
        Node node = Node.of("subagent", NodeAction.of(state -> Map.of()));
        GraphResponse<NodeOutput> response = GraphResponse.stream(
                "subagent",
                StreamingOutput.ofChunk("subagent", node, "done", new MapState())
        );
        when(subGraph.stream(any(Map.class), any(RunnableConfig.class))).thenReturn(Flux.just(response));

        TaskTool tool = new TaskTool(Map.of("research", subAgent));
        TaskTool.TaskRequest request = new TaskTool.TaskRequest("summarize this", "research");

        String result = tool.task(request, null);
        assertEquals("done", result);
    }

    @Test
    void returnsReadableErrorWhenSubagentTypeNotFound() {
        TaskTool tool = new TaskTool(Map.of());
        TaskTool.TaskRequest request = new TaskTool.TaskRequest("do work", "missing");

        String result = tool.task(request, null);
        assertTrue(result.contains("unknown subagent_type"));
    }

    @Test
    void returnsErrorTextWhenSubagentExecutionFails() {
        ReactAgent subAgent = mock(ReactAgent.class);
        CompiledGraph subGraph = mock(CompiledGraph.class);
        when(subAgent.getGraph()).thenReturn(subGraph);
        when(subGraph.stream(any(Map.class), any(RunnableConfig.class)))
                .thenReturn(Flux.error(new RuntimeException("boom")));

        TaskTool tool = new TaskTool(Map.of("worker", subAgent));
        TaskTool.TaskRequest request = new TaskTool.TaskRequest("fail me", "worker");

        String result = tool.task(request, null);
        assertTrue(result.contains("boom"));
    }

    @Test
    void emitsFailedStepMetadataWithNodeContext() {
        ReactAgent subAgent = mock(ReactAgent.class);
        CompiledGraph subGraph = mock(CompiledGraph.class);
        when(subAgent.getGraph()).thenReturn(subGraph);

        Node failedNode = Node.of("fetch_docs", NodeAction.of(state -> Map.of()), "Fetch docs");
        NodeOutput failedOutput = NodeOutput.failed("fetch_docs", failedNode, "tool execution failed", Instant.now());
        GraphResponse<NodeOutput> failedResponse = GraphResponse.of("fetch_docs", failedOutput);
        when(subGraph.stream(any(Map.class), any(RunnableConfig.class))).thenReturn(Flux.just(failedResponse));

        TaskTool tool = new TaskTool(Map.of("worker", subAgent), reporter);
        TaskTool.TaskRequest request = new TaskTool.TaskRequest("fail me", "worker");

        String result = tool.task(request, toolContext);
        assertTrue(result.contains("tool execution failed"));

        SubAgentProgressEvent failedStepEvent = emittedEvents.stream()
                .filter(event -> "SUBAGENT_PROGRESS".equals(event.eventType()))
                .filter(event -> "failed".equals(String.valueOf(event.metadata().get("phase"))))
                .findFirst()
                .orElse(null);

        assertNotNull(failedStepEvent);
        assertEquals("fetch_docs", failedStepEvent.metadata().get("failedNodeId"));
        assertEquals("custom", failedStepEvent.metadata().get("failedNodeType"));
        assertEquals("tool execution failed", failedStepEvent.metadata().get("errorMessage"));
    }
}
