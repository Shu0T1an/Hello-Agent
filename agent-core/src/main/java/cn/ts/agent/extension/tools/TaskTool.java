package cn.ts.agent.extension.tools;

import cn.ts.agent.api.AgentResult;
import cn.ts.agent.Tool.ToolContextConstants;
import cn.ts.agent.core.ReactAgent;
import cn.ts.agent.extension.progress.SubAgentProgressEvent;
import cn.ts.agent.extension.progress.SubAgentProgressReporter;
import cn.ts.graph.state.State;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Semaphore;

/**
 * Tool used by the main agent to delegate isolated tasks to subagents.
 */
public class TaskTool {

    private static final AtomicLong TASK_SEQUENCE = new AtomicLong(0);
    private static final String EVENT_SUBAGENT_STARTED = "SUBAGENT_STARTED";
    private static final String EVENT_SUBAGENT_PROGRESS = "SUBAGENT_PROGRESS";
    private static final String EVENT_SUBAGENT_COMPLETED = "SUBAGENT_COMPLETED";
    private static final String EVENT_SUBAGENT_FAILED = "SUBAGENT_FAILED";
    private static final String NODE_TYPE_SUBAGENT = "subagent";
    private static final int DEFAULT_MAX_PARALLEL_SUBAGENTS = 3;

    private final Map<String, ReactAgent> subAgents;
    private final SubAgentProgressReporter progressReporter;
    private final Semaphore concurrencySemaphore;

    public TaskTool(Map<String, ReactAgent> subAgents) {
        this(subAgents, SubAgentProgressReporter.noop(), DEFAULT_MAX_PARALLEL_SUBAGENTS);
    }

    public TaskTool(Map<String, ReactAgent> subAgents, SubAgentProgressReporter progressReporter) {
        this(subAgents, progressReporter, DEFAULT_MAX_PARALLEL_SUBAGENTS);
    }

    public TaskTool(
            Map<String, ReactAgent> subAgents,
            SubAgentProgressReporter progressReporter,
            int maxParallelSubagents) {
        this.subAgents = subAgents;
        this.progressReporter = progressReporter != null ? progressReporter : SubAgentProgressReporter.noop();
        int permits = maxParallelSubagents > 0 ? maxParallelSubagents : DEFAULT_MAX_PARALLEL_SUBAGENTS;
        this.concurrencySemaphore = new Semaphore(permits, true);
    }

    @Tool(name = "task", description = "Delegate an isolated task to a subagent by subagent_type.")
    public String task(
            @ToolParam(description = "Task payload containing description and subagent_type")
            TaskRequest request,
            ToolContext toolContext) {
        TaskExecutionContext taskContext = resolveTaskExecutionContext(toolContext, request);

        if (request == null || request.description == null || request.description.isBlank()) {
            emitFailure(taskContext, "description is required");
            return "Error: description is required";
        }
        if (request.subagentType == null || request.subagentType.isBlank()) {
            emitFailure(taskContext, "subagent_type is required");
            return "Error: subagent_type is required";
        }
        if (subAgents == null || !subAgents.containsKey(request.subagentType)) {
            emitFailure(taskContext, "unknown subagent_type '" + request.subagentType + "'");
            return "Error: unknown subagent_type '" + request.subagentType + "', available: "
                    + (subAgents == null ? "[]" : subAgents.keySet());
        }

        ReactAgent subAgent = subAgents.get(request.subagentType);
        emitEvent(taskContext, EVENT_SUBAGENT_STARTED, "started", 0, null, null, null);
        emitEvent(taskContext, EVENT_SUBAGENT_PROGRESS, "running", 50, null, null, null);

        long startNs = System.nanoTime();
        boolean acquired = false;
        try {
            acquired = tryAcquirePermit(taskContext);
            if (!acquired) {
                return "Error executing subagent task: interrupted while waiting for execution slot";
            }
            AgentResult result = subAgent.invoke(request.description);
            long durationMs = nanosToMillis(startNs);
            if (result == null) {
                emitEvent(taskContext, EVENT_SUBAGENT_FAILED, "failed", 100, durationMs, "SUBAGENT_EMPTY_RESULT", "empty result");
                return "Error executing subagent task: empty result";
            }
            if (result.isSuccess()) {
                String output = result.getOutput();
                emitEvent(taskContext, EVENT_SUBAGENT_COMPLETED, "done", 100, durationMs, null, output);
                return result.getOutput();
            }
            Throwable error = result.getError();
            String errorMessage = error != null ? error.getMessage() : "unknown error";
            emitEvent(taskContext, EVENT_SUBAGENT_FAILED, "failed", 100, durationMs, "SUBAGENT_EXECUTION_ERROR", errorMessage);
            return "Error executing subagent task: " + (error != null ? error.getMessage() : "unknown error");
        } catch (Exception e) {
            long durationMs = nanosToMillis(startNs);
            emitEvent(taskContext, EVENT_SUBAGENT_FAILED, "failed", 100, durationMs, "SUBAGENT_EXCEPTION", e.getMessage());
            return "Error executing subagent task: " + e.getMessage();
        } finally {
            if (acquired) {
                concurrencySemaphore.release();
            }
        }
    }

    private boolean tryAcquirePermit(TaskExecutionContext taskContext) {
        if (concurrencySemaphore.tryAcquire()) {
            return true;
        }

        emitEvent(taskContext, EVENT_SUBAGENT_PROGRESS, "queued", 0, null, null, null);
        try {
            concurrencySemaphore.acquire();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitEvent(taskContext, EVENT_SUBAGENT_FAILED, "failed", 100, null, "SUBAGENT_INTERRUPTED", e.getMessage());
            return false;
        }
    }

    private TaskExecutionContext resolveTaskExecutionContext(ToolContext toolContext, TaskRequest request) {
        String executionId = null;
        String toolCallId = null;
        if (toolContext != null && toolContext.getContext() != null) {
            Map<String, Object> contextMap = toolContext.getContext();
            Object stateObj = contextMap.get(ToolContextConstants.TOOL_STATE_CONTEXT_KEY);
            Map<String, Object> stateData = extractStateData(stateObj);
            executionId = asString(stateData.get("executionId"));
            toolCallId = asString(contextMap.get(ToolContextConstants.TOOL_CALL_ID_CONTEXT_KEY));
        }
        String subagentType = request != null ? request.subagentType : null;
        String subagentTaskId = buildSubagentTaskId(executionId, toolCallId);
        return new TaskExecutionContext(executionId, toolCallId, subagentType, subagentTaskId);
    }

    private Map<String, Object> extractStateData(Object stateObj) {
        if (stateObj == null) {
            return Map.of();
        }
        if (stateObj instanceof State state) {
            return state.data();
        }
        if (stateObj instanceof Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    copied.put(key, entry.getValue());
                }
            }
            return copied;
        }
        return Map.of();
    }

    private String buildSubagentTaskId(String executionId, String toolCallId) {
        long seq = TASK_SEQUENCE.incrementAndGet();
        String safeExecutionId = executionId != null && !executionId.isBlank() ? executionId : "unknown";
        String safeToolCallId = toolCallId != null && !toolCallId.isBlank() ? toolCallId : "tool-call";
        return safeExecutionId + ":" + safeToolCallId + ":" + seq;
    }

    private void emitFailure(TaskExecutionContext context, String errorMessage) {
        emitEvent(context, EVENT_SUBAGENT_FAILED, "failed", 100, null, "SUBAGENT_INPUT_ERROR", errorMessage);
    }

    private void emitEvent(
            TaskExecutionContext context,
            String eventType,
            String phase,
            int progress,
            Long durationMs,
            String errorCode,
            String summary) {
        if (context.executionId() == null || context.executionId().isBlank()) {
            return;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("subagentTaskId", context.subagentTaskId());
        metadata.put("subagentType", context.subagentType());
        metadata.put("parentToolCallId", context.parentToolCallId());
        metadata.put("parentExecutionId", context.executionId());
        metadata.put("phase", phase);
        metadata.put("progress", progress);
        if (durationMs != null) {
            metadata.put("durationMs", durationMs);
        }
        if (summary != null && !summary.isBlank()) {
            metadata.put("summary", summary.length() > 600 ? summary.substring(0, 600) : summary);
        }
        if (errorCode != null && !errorCode.isBlank()) {
            metadata.put("errorCode", errorCode);
        }
        if (EVENT_SUBAGENT_FAILED.equals(eventType) && summary != null && !summary.isBlank()) {
            metadata.put("errorMessage", summary);
        }

        String nodeId = "subagent:" + (context.subagentType() != null ? context.subagentType() : "unknown");
        String message = switch (eventType) {
            case EVENT_SUBAGENT_STARTED -> "Subagent started";
            case EVENT_SUBAGENT_PROGRESS -> "Subagent running";
            case EVENT_SUBAGENT_COMPLETED -> "Subagent completed";
            case EVENT_SUBAGENT_FAILED -> "Subagent failed";
            default -> "Subagent event";
        };

        progressReporter.emit(
                context.executionId(),
                new SubAgentProgressEvent(
                        eventType,
                        nodeId,
                        NODE_TYPE_SUBAGENT,
                        message,
                        Instant.now(),
                        metadata
                )
        );
    }

    private long nanosToMillis(long startNs) {
        return Math.max(0L, (System.nanoTime() - startNs) / 1_000_000L);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record TaskExecutionContext(
            String executionId,
            String parentToolCallId,
            String subagentType,
            String subagentTaskId
    ) {
    }

    public static class TaskRequest {

        @JsonProperty(required = true)
        @JsonPropertyDescription("Detailed description of the task to be performed by the subagent")
        public String description;

        @JsonProperty(required = true, value = "subagent_type")
        @JsonPropertyDescription("The type of subagent to use for this task")
        public String subagentType;

        public TaskRequest() {
        }

        public TaskRequest(String description, String subagentType) {
            this.description = description;
            this.subagentType = subagentType;
        }
    }
}
