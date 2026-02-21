package cn.ts.agent.extension.progress;

/**
 * Reporter interface for subagent task progress events.
 */
public interface SubAgentProgressReporter {

    void emit(String executionId, SubAgentProgressEvent event);

    static SubAgentProgressReporter noop() {
        return (executionId, event) -> {
        };
    }
}
