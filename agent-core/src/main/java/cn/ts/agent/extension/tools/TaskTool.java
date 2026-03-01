package cn.ts.agent.extension.tools;

import cn.ts.agent.api.AgentConfig;
import cn.ts.agent.tool.ToolContextConstants;
import cn.ts.agent.core.ReactAgent;
import cn.ts.agent.extension.progress.SubAgentProgressEvent;
import cn.ts.agent.extension.progress.SubAgentProgressReporter;
import cn.ts.agent.extension.progress.SubAgentStreamMapper;
import cn.ts.graph.GraphResponse;
import cn.ts.graph.NodeOutput;
import cn.ts.graph.StreamingOutput;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.state.State;
import cn.ts.graph.util.StateTemplates;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final SubAgentStreamMapper streamMapper;

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
        this.streamMapper = new SubAgentStreamMapper();
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
        emitEvent(taskContext, EVENT_SUBAGENT_STARTED, "planning", 0, null, null, "Subagent accepted task");

        long startNs = System.nanoTime();
        boolean acquired = false;
        try {
            acquired = tryAcquirePermit(taskContext);
            if (!acquired) {
                return "Error executing subagent task: interrupted while waiting for execution slot";
            }
            emitEvent(taskContext, EVENT_SUBAGENT_PROGRESS, "planning", 5, null, null, "Subagent execution started");

            SubAgentExecutionResult streamResult = executeSubAgentStreaming(subAgent, request.description, taskContext);
            long durationMs = nanosToMillis(startNs);

            if (!streamResult.success()) {
                emitEvent(taskContext, EVENT_SUBAGENT_FAILED, "failed", 100, durationMs, streamResult.errorCode(), streamResult.errorMessage());
                return "Error executing subagent task: " + streamResult.errorMessage();
            }
            if (streamResult.output() == null || streamResult.output().isBlank()) {
                emitEvent(taskContext, EVENT_SUBAGENT_FAILED, "failed", 100, durationMs, "SUBAGENT_EMPTY_RESULT", "empty result");
                return "Error executing subagent task: empty result";
            }
            emitEvent(taskContext, EVENT_SUBAGENT_COMPLETED, "done", 100, durationMs, null, streamResult.output());
            return streamResult.output();
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

    private SubAgentExecutionResult executeSubAgentStreaming(
            ReactAgent subAgent,
            String description,
            TaskExecutionContext taskContext) {
        AgentConfig defaultConfig = AgentConfig.defaultConfig();
        State initialState = StateTemplates.createAgentInitialState(description, defaultConfig.getMaxIterations());
        Map<String, Object> stateData = new LinkedHashMap<>(initialState.data());
        stateData.put("executionId", taskContext.executionId());
        // Ensure subagent first-turn request always starts with a concrete user message.
        stateData.put("messages", new ArrayList<>(List.of(new UserMessage(description))));

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .executionId(taskContext.executionId() + ":sub:" + taskContext.subagentTaskId())
                .build();
        Flux<GraphResponse<NodeOutput>> stream = subAgent.getGraph().stream(stateData, runnableConfig);

        final StringBuilder outputBuilder = new StringBuilder();
        final AtomicLong seq = new AtomicLong(0);
        final List<String> errorMessages = new ArrayList<>();
        final StringBuilder finalStateOutput = new StringBuilder();

        stream.toIterable().forEach(response -> {
            long stepSeq = seq.incrementAndGet();
            SubAgentStreamMapper.MappedProgress mapped = streamMapper.map(response, stepSeq);
            if (mapped == null) {
                return;
            }
            emitMappedProgress(taskContext, mapped);
            appendOutputChunk(outputBuilder, response);
            if ("failed".equals(mapped.phase())) {
                String stepErrorMessage = mapped.errorMessage();
                if (stepErrorMessage == null || stepErrorMessage.isBlank()) {
                    stepErrorMessage = mapped.summary();
                }
                if (stepErrorMessage != null && !stepErrorMessage.isBlank()) {
                    errorMessages.add(stepErrorMessage);
                }
            }
            appendOutputFromState(finalStateOutput, response);
        });

        if (!errorMessages.isEmpty()) {
            String errorMessage = String.join("; ", errorMessages);
            return SubAgentExecutionResult.failed("SUBAGENT_EXECUTION_ERROR", errorMessage);
        }
        String output = outputBuilder.toString().trim();
        if (output.isEmpty()) {
            output = finalStateOutput.toString().trim();
        }
        return SubAgentExecutionResult.success(output);
    }

    private void emitMappedProgress(
            TaskExecutionContext taskContext,
            SubAgentStreamMapper.MappedProgress mapped) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("subagentTaskId", taskContext.subagentTaskId());
        metadata.put("subagentType", taskContext.subagentType());
        metadata.put("parentToolCallId", taskContext.parentToolCallId());
        metadata.put("parentExecutionId", taskContext.executionId());

        Map<String, Object> enriched = streamMapper.enrichMetadata(metadata, mapped, taskContext.subagentTaskId());
        int progress = estimateProgress(mapped.phase());
        enriched.put("progress", progress);
        if ("failed".equals(mapped.phase())) {
            enriched.put("errorCode", "SUBAGENT_STREAM_FAILED");
            String errorMessage = mapped.errorMessage() != null && !mapped.errorMessage().isBlank()
                    ? mapped.errorMessage()
                    : mapped.summary();
            if (errorMessage != null && !errorMessage.isBlank()) {
                enriched.put("errorMessage", errorMessage);
            }
            if (mapped.nodeId() != null && !mapped.nodeId().isBlank()) {
                enriched.put("failedNodeId", mapped.nodeId());
            }
            if (mapped.nodeType() != null && !mapped.nodeType().isBlank()) {
                enriched.put("failedNodeType", mapped.nodeType());
            }
            if (mapped.stackTrace() != null && !mapped.stackTrace().isBlank()) {
                enriched.put("stackTrace", mapped.stackTrace());
            }
        }
        String message = "Subagent " + mapped.phase();
        progressReporter.emit(
                taskContext.executionId(),
                new SubAgentProgressEvent(
                        EVENT_SUBAGENT_PROGRESS,
                        "subagent:" + (taskContext.subagentType() != null ? taskContext.subagentType() : "unknown"),
                        NODE_TYPE_SUBAGENT,
                        message,
                        Instant.now(),
                        enriched
                )
        );
    }

    private int estimateProgress(String phase) {
        return switch (phase) {
            case "queued" -> 0;
            case "planning" -> 10;
            case "tool_call" -> 35;
            case "tool_result" -> 65;
            case "synthesizing" -> 85;
            case "done", "failed" -> 100;
            default -> 50;
        };
    }

    private void appendOutputChunk(StringBuilder outputBuilder, GraphResponse<NodeOutput> response) {
        if (response == null || response.hasError()) {
            return;
        }
        NodeOutput output = response.getData();
        if (!(output instanceof StreamingOutput<?> streamingOutput)) {
            return;
        }
        String chunk = streamingOutput.getChunk();
        if (chunk == null || chunk.isBlank()) {
            return;
        }
        outputBuilder.append(chunk);
    }

    private void appendOutputFromState(StringBuilder outputBuilder, GraphResponse<NodeOutput> response) {
        if (response == null || response.getData() == null || response.getData().getState() == null) {
            return;
        }
        Object messagesObj = response.getData().getState().data().get("messages");
        if (!(messagesObj instanceof List<?> messages) || messages.isEmpty()) {
            return;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Object message = messages.get(i);
            if (message instanceof AssistantMessage assistant) {
                String text = assistant.getText();
                if (text != null && !text.isBlank()) {
                    outputBuilder.setLength(0);
                    outputBuilder.append(text);
                }
                return;
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

    private record SubAgentExecutionResult(
            boolean success,
            String output,
            String errorCode,
            String errorMessage
    ) {
        static SubAgentExecutionResult success(String output) {
            return new SubAgentExecutionResult(true, output, null, null);
        }

        static SubAgentExecutionResult failed(String errorCode, String errorMessage) {
            String normalized = (errorMessage == null || errorMessage.isBlank()) ? "unknown error" : errorMessage;
            return new SubAgentExecutionResult(false, null, errorCode, normalized);
        }
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
