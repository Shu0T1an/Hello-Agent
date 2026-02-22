package cn.ts.web.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 执行事件 DTO
 * <p>
 * 用于向前端传输图执行事件的 DTO
 * </p>
 *
 * @author tianshuo
 */
public class ExecutionEventDTO {

    private String eventType;
    private String nodeId;
    private Map<String, Object> stateData;
    private String message;
    private Instant timestamp;
    private String executionId;
    private String error;
    private Map<String, Object> metadata;

    public ExecutionEventDTO() {
    }

    public ExecutionEventDTO(String eventType, String nodeId, Map<String, Object> stateData,
                            String message, Instant timestamp, String executionId,
                            String error, Map<String, Object> metadata) {
        this.eventType = eventType;
        this.nodeId = nodeId;
        this.stateData = stateData;
        this.message = message;
        this.timestamp = timestamp;
        this.executionId = executionId;
        this.error = error;
        this.metadata = metadata;
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String eventType;
        private String nodeId;
        private Map<String, Object> stateData;
        private String message;
        private Instant timestamp;
        private String executionId;
        private String error;
        private Map<String, Object> metadata;

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
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

        public ExecutionEventDTO build() {
            return new ExecutionEventDTO(eventType, nodeId, stateData, message,
                    timestamp, executionId, error, metadata);
        }
    }
}
