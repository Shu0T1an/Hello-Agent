package cn.ts.web.tool.local;

import cn.ts.agent.tool.shell.ShellCommandResult;
import cn.ts.agent.tool.shell.ShellSessionManager;
import cn.ts.agent.tool.shell.ShellToolException;
import cn.ts.graph.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

import static cn.ts.agent.tool.ToolContextConstants.TOOL_STATE_CONTEXT_KEY;

@Component
public class ShellCommandTool {

    private static final Logger logger = LoggerFactory.getLogger(ShellCommandTool.class);

    private final ShellToolProperties properties;
    private final ShellSessionManager sessionManager;

    public ShellCommandTool(ShellToolProperties properties, ShellToolRuntime runtime) {
        this.properties = properties;
        this.sessionManager = runtime.sessionManager();
    }

    @Tool(
            name = "execute_shell_command",
            description = """
                    Execute shell command in a persistent session.
                    Supports restart, timeout override and optional working directory.
                    """
    )
    public String executeShellCommand(
            @ToolParam(description = "Shell command request")
            ShellCommandRequest request,
            ToolContext toolContext) {
        if (!properties.isEnabled()) {
            return "Error: [SHELL_DISABLED] shell tool is disabled by configuration";
        }

        String command = request == null ? null : request.command();
        boolean restart = request != null && Boolean.TRUE.equals(request.restart());
        Integer timeoutSeconds = request == null ? null : request.timeoutSeconds();
        String workingDirectory = request == null ? null : request.workingDirectory();

        String sessionKey = resolveSessionKey(toolContext);
        try {
            ShellCommandResult result = sessionManager.executeCommand(
                    sessionKey,
                    command,
                    timeoutSeconds,
                    workingDirectory,
                    restart
            );
            return formatResult(result, sessionKey);
        } catch (ShellToolException e) {
            logger.warn("Shell command rejected, session={}, code={}, message={}", sessionKey, e.getErrorCode(), e.getMessage());
            return formatError(sessionKey, e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            logger.error("Shell command execution failed unexpectedly, session={}", sessionKey, e);
            return formatError(sessionKey, "SHELL_EXEC_FAILED", e.getMessage());
        }
    }

    private String resolveSessionKey(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return "global";
        }
        Map<String, Object> context = toolContext.getContext();
        Object stateObject = context.get(TOOL_STATE_CONTEXT_KEY);
        if (stateObject instanceof State state) {
            String session = asNonBlank(state.value("sessionId").orElse(null));
            if (session != null) {
                return session;
            }
            String thread = asNonBlank(state.value("threadId").orElse(null));
            if (thread != null) {
                return thread;
            }
            String execution = asNonBlank(state.value("executionId").orElse(null));
            if (execution != null) {
                return execution;
            }
        }
        if (stateObject instanceof Map<?, ?> map) {
            String session = asNonBlank(map.get("sessionId"));
            if (session != null) {
                return session;
            }
            String thread = asNonBlank(map.get("threadId"));
            if (thread != null) {
                return thread;
            }
            String execution = asNonBlank(map.get("executionId"));
            if (execution != null) {
                return execution;
            }
        }
        String execution = asNonBlank(context.get("executionId"));
        return execution != null ? execution : "global";
    }

    private String asNonBlank(Object value) {
        if (!(value instanceof String str)) {
            return null;
        }
        String trimmed = str.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatResult(ShellCommandResult result, String sessionKey) {
        StringBuilder out = new StringBuilder();
        out.append("status=").append(result.success() ? "ok" : "error")
                .append(", session=").append(sessionKey)
                .append(", exitCode=").append(result.exitCode())
                .append(", durationMs=").append(result.durationMs())
                .append(", timedOut=").append(result.timedOut())
                .append(", truncated=").append(result.truncated())
                .append(", restarted=").append(result.sessionRestarted());
        if (result.errorCode() != null && !result.errorCode().isBlank()) {
            out.append(", errorCode=").append(result.errorCode());
        }

        if (result.stdout() != null && !result.stdout().isBlank()) {
            out.append("\n\n[stdout]\n").append(result.stdout().trim());
        }
        if (result.stderr() != null && !result.stderr().isBlank()) {
            out.append("\n\n[stderr]\n").append(result.stderr().trim());
        }
        return out.toString();
    }

    private String formatError(String sessionKey, String errorCode, String message) {
        StringBuilder out = new StringBuilder();
        out.append("status=error")
                .append(", session=").append(sessionKey)
                .append(", exitCode=-1")
                .append(", timedOut=false")
                .append(", truncated=false")
                .append(", restarted=false");
        if (errorCode != null && !errorCode.isBlank()) {
            out.append(", errorCode=").append(errorCode);
        }
        if (message != null && !message.isBlank()) {
            out.append("\n\n[stderr]\n").append(message.trim());
        }
        return out.toString();
    }

    public record ShellCommandRequest(
            String command,
            Boolean restart,
            Integer timeoutSeconds,
            String workingDirectory
    ) {
    }
}
