package cn.ts.agent.extension.progress;

import cn.ts.graph.GraphResponse;
import cn.ts.graph.NodeOutput;
import cn.ts.graph.NodeStatus;
import cn.ts.graph.StreamingOutput;
import cn.ts.graph.constant.GraphConstants;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps subagent graph stream responses to progress metadata for timeline rendering.
 */
public class SubAgentStreamMapper {

    private static final int MAX_STACK_TRACE_CHARS = 12 * 1024;
    private static final String PHASE_PLANNING = "planning";
    private static final String PHASE_RUNNING = "running";
    private static final String PHASE_TOOL_CALL = "tool_call";
    private static final String PHASE_TOOL_RESULT = "tool_result";
    private static final String PHASE_SYNTHESIZING = "synthesizing";
    private static final String PHASE_DONE = "done";
    private static final String PHASE_FAILED = "failed";
    private static final String PHASE_UNKNOWN = "running";

    public MappedProgress map(GraphResponse<NodeOutput> response, long seq) {
        if (response == null) {
            return null;
        }
        if (response.hasError()) {
            String message = response.getError() != null ? response.getError().getMessage() : "Subagent stream error";
            String nodeId = response.getNodeId();
            String nodeType = resolveNodeType(nodeId);
            String stackTrace = buildStackTrace(response.getError());
            return MappedProgress.failed(seq, message, nodeId, nodeType, stackTrace);
        }

        String phase = resolvePhase(response);
        String summary = resolveSummary(response);
        String toolName = resolveToolName(response);
        String nodeId = resolveNodeId(response);
        String nodeType = resolveNodeType(nodeId);
        String errorMessage = resolveErrorMessage(response, phase);

        return new MappedProgress(phase, summary, toolName, seq, nodeId, nodeType, errorMessage, null);
    }

    private String resolvePhase(GraphResponse<NodeOutput> response) {
        if (response.type() == GraphResponse.ResponseType.INTERRUPTION) {
            return PHASE_FAILED;
        }
        if (response.isComplete() && response.getNodeId() == null) {
            return PHASE_DONE;
        }

        NodeOutput output = response.getData();
        if (output == null) {
            return PHASE_UNKNOWN;
        }

        if (output.getStatus() == NodeStatus.FAILED) {
            return PHASE_FAILED;
        }
        if (GraphConstants.AGENT_TOOL.equals(output.getNodeId())) {
            return PHASE_TOOL_RESULT;
        }

        Map<String, Object> stateData = output.getState() != null ? output.getState().data() : Map.of();
        Object executionRecordObj = stateData.get("execution_record");
        if (executionRecordObj instanceof Map<?, ?> executionRecord) {
            Object toolCallsObj = executionRecord.get("toolCalls");
            if (toolCallsObj instanceof List<?> toolCalls && !toolCalls.isEmpty()) {
                return PHASE_TOOL_CALL;
            }
            Object executionsObj = executionRecord.get("executions");
            if (executionsObj instanceof List<?> executions && !executions.isEmpty()) {
                return PHASE_TOOL_RESULT;
            }
        }

        if (GraphConstants.AGENT_MODEL.equals(output.getNodeId())) {
            if (output instanceof StreamingOutput<?> streamingOutput
                    && streamingOutput.getOutputType() == cn.ts.graph.OutputType.COMPLETE) {
                return PHASE_SYNTHESIZING;
            }
            if (output.getStatus() == NodeStatus.STARTING) {
                return PHASE_PLANNING;
            }
            return PHASE_RUNNING;
        }
        if (output.getStatus() == NodeStatus.COMPLETED) {
            return PHASE_SYNTHESIZING;
        }
        if (output.getStatus() == NodeStatus.STARTING) {
            return PHASE_PLANNING;
        }
        return PHASE_RUNNING;
    }

    private String resolveSummary(GraphResponse<NodeOutput> response) {
        NodeOutput output = response.getData();
        if (response.hasError()) {
            return response.getError() != null ? response.getError().getMessage() : null;
        }
        if (output == null) {
            return null;
        }
        if (output.getErrorMessage() != null && !output.getErrorMessage().isBlank()) {
            return output.getErrorMessage();
        }
        if (output instanceof StreamingOutput<?> streamingOutput) {
            String chunk = streamingOutput.getChunk();
            if (chunk != null && !chunk.isBlank()) {
                return chunk;
            }
        }
        Object resultValue = output.getResultValue();
        if (resultValue instanceof String text && !text.isBlank()) {
            return text;
        }
        return null;
    }

    private String resolveErrorMessage(GraphResponse<NodeOutput> response, String phase) {
        if (!PHASE_FAILED.equals(phase) || response == null) {
            return null;
        }
        if (response.hasError()) {
            Throwable throwable = response.getError();
            return throwable != null ? throwable.getMessage() : null;
        }
        NodeOutput output = response.getData();
        if (output == null) {
            return null;
        }
        String errorMessage = output.getErrorMessage();
        if (errorMessage != null && !errorMessage.isBlank()) {
            return errorMessage;
        }
        Object result = output.getResultValue();
        if (result instanceof String resultText && !resultText.isBlank()) {
            return resultText;
        }
        return null;
    }

    private String resolveNodeId(GraphResponse<NodeOutput> response) {
        if (response == null) {
            return null;
        }
        NodeOutput output = response.getData();
        if (output != null && output.getNodeId() != null && !output.getNodeId().isBlank()) {
            return output.getNodeId();
        }
        return response.getNodeId();
    }

    private String resolveNodeType(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "unknown";
        }
        return switch (nodeId) {
            case GraphConstants.AGENT_MODEL -> "llm";
            case GraphConstants.AGENT_TOOL -> "tool";
            default -> "custom";
        };
    }

    private String buildStackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        String stackTrace = writer.toString();
        if (stackTrace.length() <= MAX_STACK_TRACE_CHARS) {
            return stackTrace;
        }
        return stackTrace.substring(0, MAX_STACK_TRACE_CHARS) + "\n...[truncated]";
    }

    private String resolveToolName(GraphResponse<NodeOutput> response) {
        NodeOutput output = response.getData();
        if (output == null || output.getState() == null) {
            return null;
        }
        Object executionRecordObj = output.getState().data().get("execution_record");
        if (!(executionRecordObj instanceof Map<?, ?> executionRecord)) {
            return null;
        }
        Object toolCallsObj = executionRecord.get("toolCalls");
        String fromToolCalls = firstToolName(toolCallsObj);
        if (fromToolCalls != null) {
            return fromToolCalls;
        }
        Object executionsObj = executionRecord.get("executions");
        return firstToolName(executionsObj);
    }

    private String firstToolName(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> map)) {
            return null;
        }
        Object name = map.get("name");
        if (name == null) {
            return null;
        }
        String text = String.valueOf(name);
        return text.isBlank() ? null : text;
    }

    public Map<String, Object> enrichMetadata(
            Map<String, Object> baseMetadata,
            MappedProgress mapped,
            String stepIdPrefix) {
        Map<String, Object> metadata = new LinkedHashMap<>(baseMetadata != null ? baseMetadata : Map.of());
        metadata.put("phase", mapped.phase());
        metadata.put("seq", mapped.seq());
        metadata.put("stepId", stepIdPrefix + ":" + mapped.seq());
        metadata.put("stepTitle", buildStepTitle(mapped));
        if (mapped.toolName() != null) {
            metadata.put("toolName", mapped.toolName());
        }
        if (mapped.summary() != null && !mapped.summary().isBlank()) {
            metadata.put("summary", mapped.summary());
        }
        if (mapped.nodeId() != null && !mapped.nodeId().isBlank()) {
            metadata.put("stepNodeId", mapped.nodeId());
        }
        if (mapped.nodeType() != null && !mapped.nodeType().isBlank()) {
            metadata.put("stepNodeType", mapped.nodeType());
        }
        if (mapped.errorMessage() != null && !mapped.errorMessage().isBlank()) {
            metadata.put("errorMessage", mapped.errorMessage());
        }
        if (mapped.stackTrace() != null && !mapped.stackTrace().isBlank()) {
            metadata.put("stackTrace", mapped.stackTrace());
        }
        return metadata;
    }

    private String buildStepTitle(MappedProgress mapped) {
        if (mapped == null) {
            return "subagent_step";
        }
        return switch (mapped.phase()) {
            case PHASE_PLANNING -> "Planning";
            case PHASE_TOOL_CALL -> mapped.toolName() != null ? "Call Tool: " + mapped.toolName() : "Tool Call";
            case PHASE_TOOL_RESULT -> mapped.toolName() != null ? "Tool Result: " + mapped.toolName() : "Tool Result";
            case PHASE_SYNTHESIZING -> "Synthesizing";
            case PHASE_DONE -> "Completed";
            case PHASE_FAILED -> "Failed";
            default -> "Running";
        };
    }

    public record MappedProgress(
            String phase,
            String summary,
            String toolName,
            long seq,
            String nodeId,
            String nodeType,
            String errorMessage,
            String stackTrace
    ) {
        public static MappedProgress failed(long seq, String message, String nodeId, String nodeType, String stackTrace) {
            return new MappedProgress(PHASE_FAILED, message, null, seq, nodeId, nodeType, message, stackTrace);
        }
    }
}
