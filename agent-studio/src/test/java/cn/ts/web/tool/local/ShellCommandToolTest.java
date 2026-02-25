package cn.ts.web.tool.local;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.ts.agent.tool.ToolContextConstants.TOOL_STATE_CONTEXT_KEY;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellCommandToolTest {

    @Test
    void returnsDisabledMessageWhenFeatureIsOff() {
        ShellToolProperties properties = new ShellToolProperties();
        properties.setEnabled(false);
        ShellToolRuntime runtime = new ShellToolRuntime(properties);
        ShellCommandTool tool = new ShellCommandTool(properties, runtime);

        String result = tool.executeShellCommand(
                new ShellCommandTool.ShellCommandRequest("echo hi", false, null, null),
                new ToolContext(new HashMap<>())
        );
        runtime.sessionManager().closeAll();

        assertTrue(result.contains("SHELL_DISABLED"));
    }

    @Test
    void usesExecutionIdAsSessionKeyWhenPresent() {
        ShellToolProperties properties = new ShellToolProperties();
        properties.setEnabled(true);
        properties.setAllowedWorkingDirectories(List.of(System.getProperty("user.dir")));
        ShellToolRuntime runtime = new ShellToolRuntime(properties);
        ShellCommandTool tool = new ShellCommandTool(properties, runtime);

        Map<String, Object> state = new HashMap<>();
        state.put("executionId", "exec-test-1");
        Map<String, Object> context = new HashMap<>();
        context.put(TOOL_STATE_CONTEXT_KEY, state);

        String result = tool.executeShellCommand(
                new ShellCommandTool.ShellCommandRequest("echo hi", false, null, null),
                new ToolContext(context)
        );
        runtime.sessionManager().closeAll();

        assertTrue(result.contains("session=exec-test-1"));
    }
}

