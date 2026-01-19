package cn.ts.graph.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 图执行事件
 * <p>
 * 表示图执行过程中的一个事件，用于实时通知前端执行状态
 * </p>
 *
 * @author tianshuo
 */
public class ExecutionEvent {

    private final ExecutionEventType eventType;
    private final String nodeId;
    private final Map<String, Object> stateData;
    private final String message;
    private final Instant timestamp;
    private final String executionId;
    private final Throwable error;
    private final Map<String, Object> metadata;

    private ExecutionEvent(Builder builder) {
        this.eventType = builder.eventType;
        this.nodeId = builder.nodeId;
        this.stateData = builder.stateData;
        this.message = builder.message;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.executionId = builder.executionId;
        this.error = builder.error;
        this.metadata = builder.metadata;
    }

    public ExecutionEventType eventType() {
        return eventType;
    }

    public String nodeId() {
        return nodeId;
    }

    public Map<String, Object> stateData() {
        return stateData;
    }

    public String message() {
        return message;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String executionId() {
        return executionId;
    }

    public Throwable error() {
        return error;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ExecutionEventType eventType) {
        return new Builder().eventType(eventType);
    }

    public static class Builder {
        private ExecutionEventType eventType;
        private String nodeId;
        private Map<String, Object> stateData;
        private String message;
        private Instant timestamp;
        private String executionId;
        private Throwable error;
        private Map<String, Object> metadata;

        public Builder eventType(ExecutionEventType eventType) {
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

        public Builder error(Throwable error) {
            this.error = error;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ExecutionEvent build() {
            Objects.requireNonNull(eventType, "EventType cannot be null");
            return new ExecutionEvent(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionEvent that = (ExecutionEvent) o;
        return eventType == that.eventType &&
                Objects.equals(nodeId, that.nodeId) &&
                Objects.equals(executionId, that.executionId) &&
                Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType, nodeId, executionId, timestamp);
    }

    @Override
    public String toString() {
        return "ExecutionEvent{" +
                "eventType=" + eventType +
                ", nodeId='" + nodeId + '\'' +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                ", executionId='" + executionId + '\'' +
                '}';
    }
}
