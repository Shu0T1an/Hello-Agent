package cn.ts.agent.extension.tools;

import cn.ts.agent.api.AgentResult;
import cn.ts.agent.core.ReactAgent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskToolTest {

    @Test
    void routesToSpecifiedSubagentType() {
        ReactAgent subAgent = mock(ReactAgent.class);
        when(subAgent.invoke("summarize this")).thenReturn(AgentResult.success("done", null));

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
        when(subAgent.invoke("fail me")).thenReturn(AgentResult.failure(new RuntimeException("boom")));

        TaskTool tool = new TaskTool(Map.of("worker", subAgent));
        TaskTool.TaskRequest request = new TaskTool.TaskRequest("fail me", "worker");

        String result = tool.task(request, null);
        assertTrue(result.contains("boom"));
    }
}
