package cn.ts.agent.extension.tools;

import cn.ts.agent.core.ReactAgent;
import cn.ts.graph.CompiledGraph;
import cn.ts.graph.GraphResponse;
import cn.ts.graph.NodeOutput;
import cn.ts.graph.StreamingOutput;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.node.Node;
import cn.ts.graph.node.NodeAction;
import cn.ts.graph.state.MapState;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskToolTest {

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
}
