package cn.ts.graph.record;

import java.time.Instant;
import java.util.List;

import static cn.ts.graph.record.ExecutionRecord.NodeType;
import static cn.ts.graph.record.ToolExecutionRecord.ToolExecution;

/**
 * 执行记录工厂工具类
 * <p>
 * 提供便捷的方法创建各类执行记录
 * </p>
 *
 * @author tianshuo
 */
public final class ExecutionRecords {

    private ExecutionRecords() {
        // 工具类，禁止实例化
    }

    /**
     * 创建 LLM 执行记录
     */
    public static LLMExecutionRecord llm(
            String nodeId,
            Instant startTime,
            Instant endTime,
            List<InputMessage> inputMessages,
            String output,
            List<ToolCallInfo> toolCalls,
            TokenUsage usage,
            boolean success,
            String errorMessage) {
        return new LLMExecutionRecord(
                nodeId, startTime, endTime,
                inputMessages, output, toolCalls, usage,
                success, errorMessage);
    }

    /**
     * 创建成功的 LLM 执行记录（简化版）
     */
    public static LLMExecutionRecord llmSuccess(
            String nodeId,
            Instant startTime,
            Instant endTime,
            List<InputMessage> inputMessages,
            String output,
            TokenUsage usage) {
        return llm(nodeId, startTime, endTime,
                inputMessages, output, List.of(), usage, true, null);
    }

    /**
     * 创建失败的 LLM 执行记录
     */
    public static LLMExecutionRecord llmFailure(
            String nodeId,
            Instant startTime,
            Instant endTime,
            String errorMessage) {
        return llm(nodeId, startTime, endTime,
                List.of(), "", List.of(), null, false, errorMessage);
    }

    /**
     * 创建 Tool 执行记录
     */
    public static ToolExecutionRecord tool(
            String nodeId,
            Instant startTime,
            Instant endTime,
            List<ToolExecution> executions,
            boolean success,
            String errorMessage) {
        return new ToolExecutionRecord(
                nodeId, startTime, endTime,
                executions, success, errorMessage);
    }

    /**
     * 创建成功的 Tool 执行记录（简化版）
     */
    public static ToolExecutionRecord toolSuccess(
            String nodeId,
            Instant startTime,
            Instant endTime,
            List<ToolExecution> executions) {
        return tool(nodeId, startTime, endTime, executions, true, null);
    }

    /**
     * 创建失败的 Tool 执行记录
     */
    public static ToolExecutionRecord toolFailure(
            String nodeId,
            Instant startTime,
            Instant endTime,
            String errorMessage) {
        return tool(nodeId, startTime, endTime, List.of(), false, errorMessage);
    }

    /**
     * 创建失败记录（通用，用于 CUSTOM 或 END 节点）
     */
    public static ExecutionRecord failure(
            NodeType nodeType,
            String nodeId,
            Instant startTime,
            String errorMessage) {
        return new BaseExecutionRecord(
                nodeType, nodeId,
                startTime.toString(), Instant.now().toString(),
                false, errorMessage);
    }

    /**
     * 创建成功记录（通用，用于 CUSTOM 或 END 节点）
     */
    public static ExecutionRecord success(
            NodeType nodeType,
            String nodeId,
            Instant startTime,
            Instant endTime) {
        return new BaseExecutionRecord(
                nodeType, nodeId,
                startTime.toString(), endTime.toString(),
                true, null);
    }
}
