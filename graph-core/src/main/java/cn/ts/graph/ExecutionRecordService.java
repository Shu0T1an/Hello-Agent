package cn.ts.graph;

import cn.ts.graph.record.ExecutionRecord;
import cn.ts.graph.record.ExecutionRecordManager;
import cn.ts.graph.record.ExecutionRecords;
import cn.ts.graph.record.InputMessage;
import cn.ts.graph.record.TokenUsage;
import cn.ts.graph.record.ToolCallInfo;
import cn.ts.graph.record.ToolExecutionRecord;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

class ExecutionRecordService {

    private final ExecutionRecordManager recordManager;

    ExecutionRecordService(ExecutionRecordManager recordManager) {
        this.recordManager = recordManager;
    }

    @SuppressWarnings("unchecked")
    Optional<Map<String, Object>> extractExecutionInfo(Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return Optional.empty();
        }
        Object execInfo = updates.get("__execution_info__");
        if (execInfo instanceof Map<?, ?> map) {
            return Optional.of((Map<String, Object>) map);
        }
        return Optional.empty();
    }

    void saveIfPresent(String nodeId, Optional<Map<String, Object>> execInfo, GraphRunnerContext context) {
        if (execInfo.isEmpty()) {
            return;
        }
        ExecutionRecord record = createRecordFromInfo(nodeId, execInfo.get());
        saveRecord(record, context);
    }

    void saveRecord(ExecutionRecord record, GraphRunnerContext context) {
        recordManager.saveRecord(record, context.getOverallState());
    }

    ExecutionRecord buildLlmRecord(
            String nodeId,
            GraphRunnerContext context,
            ConcurrentLinkedQueue<ChatResponse> responsesQueue,
            String fullContent,
            Instant startTime) {
        List<InputMessage> inputMessages = extractInputMessages(context);
        List<ToolCallInfo> toolCalls = extractToolCallsFromResponses(responsesQueue);
        TokenUsage usage = aggregateUsage(responsesQueue);
        return ExecutionRecords.llmSuccess(
                nodeId,
                startTime,
                Instant.now(),
                inputMessages,
                fullContent,
                toolCalls,
                usage
        );
    }

    @SuppressWarnings("unchecked")
    private ExecutionRecord createRecordFromInfo(String nodeId, Map<String, Object> execInfo) {
        String nodeType = (String) execInfo.get("nodeType");
        Instant startTime = Instant.parse((String) execInfo.get("startTime"));

        if ("tool".equals(nodeType)) {
            List<Map<String, Object>> executions = (List<Map<String, Object>>) execInfo.getOrDefault("executions", List.of());
            List<ToolExecutionRecord.ToolExecution> toolExecutions = new ArrayList<>();
            for (Map<String, Object> execMap : executions) {
                toolExecutions.add(ToolExecutionRecord.ToolExecution.fromMap(execMap));
            }
            return ExecutionRecords.toolSuccess(nodeId, startTime, Instant.now(), toolExecutions);
        }

        return ExecutionRecords.success(
                ExecutionRecord.NodeType.fromValue(nodeType),
                nodeId,
                startTime,
                Instant.now()
        );
    }

    private List<InputMessage> extractInputMessages(GraphRunnerContext context) {
        List<InputMessage> result = new ArrayList<>();
        List<Message> messages = context.getOverallState()
                .<List<Message>>value("messages")
                .orElse(new ArrayList<>());

        for (Message message : messages) {
            String role = message.getMessageType().getValue();
            String content = null;
            if (message instanceof AssistantMessage am) {
                content = am.getText();
            } else if (message instanceof UserMessage um) {
                content = um.getText();
            } else if (message instanceof SystemMessage sm) {
                content = sm.getText();
            } else if (message instanceof ToolResponseMessage tm) {
                content = tm.getResponses().toString();
            }

            if (content != null) {
                result.add(new InputMessage(role, content));
            }
        }
        return result;
    }

    private List<ToolCallInfo> extractToolCallsFromResponses(ConcurrentLinkedQueue<ChatResponse> responses) {
        List<ToolCallInfo> toolCalls = new ArrayList<>();
        for (ChatResponse response : responses) {
            var output = response.getResult() != null ? response.getResult().getOutput() : null;
            if (output != null && output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                for (AssistantMessage.ToolCall tc : output.getToolCalls()) {
                    toolCalls.add(new ToolCallInfo(tc.id(), tc.name(), tc.arguments()));
                }
            }
        }
        return toolCalls;
    }

    private TokenUsage aggregateUsage(ConcurrentLinkedQueue<ChatResponse> responses) {
        long promptTokens = 0;
        long completionTokens = 0;
        long totalTokens = 0;

        for (ChatResponse response : responses) {
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                if (usage != null && usage.getTotalTokens() > 0) {
                    promptTokens = usage.getPromptTokens();
                    completionTokens = usage.getCompletionTokens();
                    totalTokens = usage.getTotalTokens();
                }
            }
        }

        return new TokenUsage(promptTokens, completionTokens, totalTokens);
    }
}
