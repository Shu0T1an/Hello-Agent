package cn.ts.agent.tool.shell;

public record ShellCommandResult(
        boolean success,
        int exitCode,
        boolean timedOut,
        boolean truncated,
        String stdout,
        String stderr,
        long durationMs,
        boolean sessionRestarted,
        String errorCode
) {
}

