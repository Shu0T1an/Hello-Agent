package cn.ts.graph.record;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 统一的节点执行记录接口
 * <p>
 * 定义所有节点执行记录的通用字段和行为
 * </p>
 *
 * @author tianshuo
 */
public interface ExecutionRecord {

    /**
     * 节点类型枚举
     */
    enum NodeType {
        LLM("llm"),
        TOOL("tool"),
        CUSTOM("custom"),
        END("end");

        private final String value;

        NodeType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        /**
         * 从字符串值获取节点类型
         */
        public static NodeType fromValue(String value) {
            for (NodeType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown node type: " + value);
        }
    }

    /**
     * 获取节点类型
     */
    NodeType getNodeType();

    /**
     * 获取节点ID
     */
    String getNodeId();

    /**
     * 获取开始时间（ISO 8601格式字符串）
     */
    String getStartTime();

    /**
     * 获取结束时间（ISO 8601格式字符串）
     */
    String getEndTime();

    /**
     * 获取执行时长（毫秒）
     */
    long getDuration();

    /**
     * 是否执行成功
     */
    boolean isSuccess();

    /**
     * 获取错误消息（失败时）
     */
    Optional<String> getErrorMessage();

    /**
     * 转换为Map用于序列化
     */
    Map<String, Object> toMap();

    /**
     * 从Map反序列化为ExecutionRecord
     */
    static Optional<ExecutionRecord> fromMap(Map<String, Object> map) {
        String nodeTypeValue = (String) map.get("nodeType");
        if (nodeTypeValue == null) {
            return Optional.empty();
        }

        NodeType nodeType = NodeType.fromValue(nodeTypeValue);
        if (nodeType == NodeType.LLM) {
            return LLMExecutionRecord.fromMap(map).map(r -> r);
        } else if (nodeType == NodeType.TOOL) {
            return ToolExecutionRecord.fromMap(map).map(r -> r);
        } else {
            // 对于 CUSTOM 和 END 类型，返回基础记录
            return Optional.of(new BaseExecutionRecord(
                    nodeType,
                    (String) map.get("nodeId"),
                    (String) map.get("startTime"),
                    (String) map.get("endTime"),
                    (Boolean) map.getOrDefault("success", true),
                    (String) map.get("errorMessage")
            ));
        }
    }
}
