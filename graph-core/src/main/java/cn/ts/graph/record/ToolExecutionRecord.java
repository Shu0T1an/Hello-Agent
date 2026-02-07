package cn.ts.graph.record;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tool 节点执行记录
 * <p>
 * 记录工具节点执行的详细信息，包括所有工具调用的结果
 * </p>
 *
 * @author tianshuo
 */
public class ToolExecutionRecord extends AbstractExecutionRecord {

    private final List<ToolExecution> executions;

    /**
     * 构造函数
     */
    public ToolExecutionRecord(
            String nodeId,
            Instant startTime,
            Instant endTime,
            List<ToolExecution> executions,
            boolean success,
            String errorMessage) {
        super(NodeType.TOOL, nodeId, startTime, endTime, success, errorMessage);
        this.executions = executions != null ? executions : List.of();
    }

    public List<ToolExecution> getExecutions() {
        return executions;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = super.toMap();

        // 添加 executions 字段
        List<Map<String, Object>> executionsList = new ArrayList<>();
        for (ToolExecution execution : executions) {
            executionsList.add(execution.toMap());
        }
        map.put("executions", executionsList);

        return map;
    }

    /**
     * 从Map反序列化
     */
    public static Optional<ToolExecutionRecord> fromMap(Map<String, Object> map) {
        try {
            String nodeId = (String) map.get("nodeId");
            String startTime = (String) map.get("startTime");
            String endTime = (String) map.get("endTime");
            Boolean success = (Boolean) map.getOrDefault("success", true);
            String errorMessage = (String) map.get("errorMessage");

            // 解析 executions
            List<ToolExecution> executions = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> executionsList = (List<Map<String, Object>>) map.get("executions");
            if (executionsList != null) {
                for (Map<String, Object> execMap : executionsList) {
                    executions.add(ToolExecution.fromMap(execMap));
                }
            }

            return Optional.of(new ToolExecutionRecord(
                    nodeId,
                    Instant.parse(startTime),
                    Instant.parse(endTime),
                    executions,
                    success,
                    errorMessage
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 单个工具执行记录
     *
     * @param id        工具调用ID
     * @param name      工具名称
     * @param arguments 工具参数
     * @param result    执行结果
     * @param success   是否成功
     * @param duration  执行时长（毫秒）
     */
    public record ToolExecution(
            String id,
            String name,
            String arguments,
            String result,
            boolean success,
            long duration
    ) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "id", id,
                    "name", name,
                    "arguments", arguments,
                    "result", result,
                    "success", success,
                    "duration", duration
            );
        }

        public static ToolExecution fromMap(Map<String, Object> map) {
            return new ToolExecution(
                    (String) map.get("id"),
                    (String) map.get("name"),
                    (String) map.get("arguments"),
                    (String) map.get("result"),
                    (Boolean) map.getOrDefault("success", true),
                    ((Number) map.getOrDefault("duration", 0)).longValue()
            );
        }
    }
}
