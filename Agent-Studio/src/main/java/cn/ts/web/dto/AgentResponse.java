package cn.ts.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Agent 响应 DTO
 * <p>
 * 用于 SSE 流式输出的统一响应格式
 * Spring WebFlux 会自动序列化为 JSON 并包装为 SSE 事件
 * </p>
 *
 * @author tianshuo
 */
public class AgentResponse {

    private String eventType;
    private String nodeId;
    private String nodeType;  // 节点类型: 'llm', 'tool', 'custom'
    private Map<String, Object> stateData;
    private String message;
    private Instant timestamp;
    private String executionId;
    private String error;
    private Map<String, Object> metadata;

    // 节点状态相关字段
    private String nodeStatus;         // 对应 NodeStatus.getCode()
    private String title;              // 节点标题
    private Instant startTime;         // 开始时间
    private Instant endTime;           // 结束时间
    private List<String> logs;         // 日志列表
    private String nodeErrorMessage;   // 节点错误信息

    public AgentResponse() {
    }

    public AgentResponse(String eventType, String nodeId, Map<String, Object> stateData,
                         String message, Instant timestamp, String executionId,
                         String error, Map<String, Object> metadata,
                         String nodeStatus, String title, Instant startTime, Instant endTime,
                         List<String> logs, String nodeErrorMessage, String nodeType) {
        this.eventType = eventType;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.stateData = stateData;
        this.message = message;
        this.timestamp = timestamp;
        this.executionId = executionId;
        this.error = error;
        this.metadata = metadata;
        this.nodeStatus = nodeStatus;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.logs = logs;
        this.nodeErrorMessage = nodeErrorMessage;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public Map<String, Object> getStateData() {
        return stateData;
    }

    public void setStateData(Map<String, Object> stateData) {
        this.stateData = stateData;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // ============ 节点状态相关 getter/setter 方法 ============

    public String getNodeStatus() {
        return nodeStatus;
    }

    public void setNodeStatus(String nodeStatus) {
        this.nodeStatus = nodeStatus;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public List<String> getLogs() {
        return logs;
    }

    public void setLogs(List<String> logs) {
        this.logs = logs;
    }

    public String getNodeErrorMessage() {
        return nodeErrorMessage;
    }

    public void setNodeErrorMessage(String nodeErrorMessage) {
        this.nodeErrorMessage = nodeErrorMessage;
    }

    /**
     * 从 ExecutionEventDTO 转换为 AgentResponse
     *
     * @param dto 源 ExecutionEventDTO
     * @return AgentResponse 实例
     */
    public static AgentResponse from(ExecutionEventDTO dto) {
        return AgentResponse.builder()
                .eventType(dto.getEventType())
                .nodeId(dto.getNodeId())
                .stateData(dto.getStateData())
                .message(dto.getMessage())
                .timestamp(dto.getTimestamp())
                .executionId(dto.getExecutionId())
                .error(dto.getError())
                .metadata(dto.getMetadata())
                .build();
    }

    /**
     * 创建错误响应
     *
     * @param executionId 执行 ID
     * @param errorMessage 错误消息
     * @return AgentResponse 实例
     */
    public static AgentResponse error(String executionId, String errorMessage) {
        return AgentResponse.builder()
                .eventType("ERROR")
                .message(errorMessage)
                .timestamp(Instant.now())
                .executionId(executionId)
                .error(errorMessage)
                .build();
    }

    /**
     * 创建心跳响应
     *
     * @param sequence 序列号
     * @return AgentResponse 实例
     */
    public static AgentResponse heartbeat(long sequence) {
        return AgentResponse.builder()
                .eventType("HEARTBEAT")
                .message("ping")
                .timestamp(Instant.now())
                .executionId(String.valueOf(sequence))
                .metadata(Map.of("sequence", sequence))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String eventType;
        private String nodeId;
        private String nodeType;
        private Map<String, Object> stateData;
        private String message;
        private Instant timestamp;
        private String executionId;
        private String error;
        private Map<String, Object> metadata;

        // 节点状态相关字段
        private String nodeStatus;
        private String title;
        private Instant startTime;
        private Instant endTime;
        private List<String> logs;
        private String nodeErrorMessage;

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder nodeType(String nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        public Builder stateData(Map<String, Object> stateData) {
            this.stateData = stateData;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder nodeStatus(String nodeStatus) {
            this.nodeStatus = nodeStatus;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder logs(List<String> logs) {
            this.logs = logs;
            return this;
        }

        public Builder nodeErrorMessage(String nodeErrorMessage) {
            this.nodeErrorMessage = nodeErrorMessage;
            return this;
        }

        public AgentResponse build() {
            return new AgentResponse(eventType, nodeId, stateData, message,
                    timestamp, executionId, error, metadata,
                    nodeStatus, title, startTime, endTime, logs, nodeErrorMessage, nodeType);
        }
    }
}
