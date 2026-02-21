package cn.ts.agent.extension.progress;

import java.time.Instant;
import java.util.Map;

/**
 * Structured progress payload emitted by subagent task executions.
 */
public record SubAgentProgressEvent(
        String eventType,
        String nodeId,
        String nodeType,
        String message,
        Instant timestamp,
        Map<String, Object> metadata
) {
}
