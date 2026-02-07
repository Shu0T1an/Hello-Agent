package cn.ts.graph.record;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LLM 节点执行记录
 * <p>
 * 记录 LLM 调用的详细信息，包括输入输出、工具调用、Token 使用等
 * </p>
 *
 * @author tianshuo
 */
public class LLMExecutionRecord extends AbstractExecutionRecord {

    private final List<InputMessage> inputMessages;
    private final String output;
    private final List<ToolCallInfo> toolCalls;
    private final TokenUsage usage;

    /**
     * 构造函数
     */
    public LLMExecutionRecord(
            String nodeId,
            Instant startTime,
            Instant endTime,
            List<InputMessage> inputMessages,
            String output,
            List<ToolCallInfo> toolCalls,
            TokenUsage usage,
            boolean success,
            String errorMessage) {
        super(NodeType.LLM, nodeId, startTime, endTime, success, errorMessage);
        this.inputMessages = inputMessages != null ? inputMessages : List.of();
        this.output = output != null ? output : "";
        this.toolCalls = toolCalls != null ? toolCalls : List.of();
        this.usage = usage != null ? usage : TokenUsage.empty();
    }

    public List<InputMessage> getInputMessages() {
        return inputMessages;
    }

    public String getOutput() {
        return output;
    }

    public List<ToolCallInfo> getToolCalls() {
        return toolCalls;
    }

    public TokenUsage getUsage() {
        return usage;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = super.toMap();

        // 添加 input 字段
        List<Map<String, Object>> inputList = new ArrayList<>();
        for (InputMessage msg : inputMessages) {
            inputList.add(msg.toMap());
        }
        map.put("input", inputList);

        // 添加 output 字段
        map.put("output", output);

        // 添加 toolCalls 字段（如果有）
        if (!toolCalls.isEmpty()) {
            List<Map<String, Object>> toolCallsList = new ArrayList<>();
            for (ToolCallInfo tc : toolCalls) {
                toolCallsList.add(tc.toMap());
            }
            map.put("toolCalls", toolCallsList);
        }

        // 添加 usage 字段（如果有有效数据）
        if (usage.totalTokens() > 0) {
            map.put("usage", usage.toMap());
        }

        return map;
    }

    /**
     * 从Map反序列化
     */
    public static Optional<LLMExecutionRecord> fromMap(Map<String, Object> map) {
        try {
            String nodeId = (String) map.get("nodeId");
            String startTime = (String) map.get("startTime");
            String endTime = (String) map.get("endTime");
            Boolean success = (Boolean) map.getOrDefault("success", true);
            String errorMessage = (String) map.get("errorMessage");

            // 解析 input
            List<InputMessage> inputMessages = List.of();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> inputList = (List<Map<String, Object>>) map.get("input");
            if (inputList != null) {
                inputMessages = new ArrayList<>();
                for (Map<String, Object> msgMap : inputList) {
                    inputMessages.add(InputMessage.fromMap(msgMap));
                }
            }

            // 解析 output
            String output = (String) map.getOrDefault("output", "");

            // 解析 toolCalls
            List<ToolCallInfo> toolCalls = List.of();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCallsList = (List<Map<String, Object>>) map.get("toolCalls");
            if (toolCallsList != null) {
                toolCalls = new ArrayList<>();
                for (Map<String, Object> tcMap : toolCallsList) {
                    toolCalls.add(ToolCallInfo.fromMap(tcMap));
                }
            }

            // 解析 usage
            TokenUsage usage = TokenUsage.empty();
            @SuppressWarnings("unchecked")
            Map<String, Object> usageMap = (Map<String, Object>) map.get("usage");
            if (usageMap != null) {
                usage = TokenUsage.fromMap(usageMap);
            }

            return Optional.of(new LLMExecutionRecord(
                    nodeId,
                    Instant.parse(startTime),
                    Instant.parse(endTime),
                    inputMessages,
                    output,
                    toolCalls,
                    usage,
                    success,
                    errorMessage
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
