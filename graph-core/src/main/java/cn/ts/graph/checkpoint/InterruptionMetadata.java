package cn.ts.graph.checkpoint;

import cn.ts.graph.state.State;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 中断元数据
 * <p>
 * 包含执行中断时的状态信息，用于后续恢复执行
 * </p>
 *
 * @author tianshuo
 */
public class InterruptionMetadata {

    private final String nodeId;
    private final State state;
    private final Map<String, Object> customData;
    private final Instant timestamp;
    private final String message;

    private InterruptionMetadata(Builder builder) {
        this.nodeId = builder.nodeId;
        this.state = builder.state;
        this.customData = builder.customData != null ? Map.copyOf(builder.customData) : Map.of();
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.message = builder.message;
    }

    /**
     * 创建构建器
     *
     * @param nodeId 节点ID
     * @param state  当前状态
     * @return Builder 实例
     */
    public static Builder builder(String nodeId, State state) {
        return new Builder(nodeId, state);
    }

    public String getNodeId() {
        return nodeId;
    }

    public State getState() {
        return state;
    }

    public Map<String, Object> getCustomData() {
        return customData;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InterruptionMetadata that = (InterruptionMetadata) o;
        return Objects.equals(nodeId, that.nodeId)
                && Objects.equals(state, that.state)
                && Objects.equals(customData, that.customData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, state, customData);
    }

    @Override
    public String toString() {
        return "InterruptionMetadata{" +
                "nodeId='" + nodeId + '\'' +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    /**
     * 构建器
     */
    public static class Builder {
        private final String nodeId;
        private final State state;
        private Map<String, Object> customData;
        private Instant timestamp;
        private String message;

        private Builder(String nodeId, State state) {
            this.nodeId = Objects.requireNonNull(nodeId, "nodeId cannot be null");
            this.state = Objects.requireNonNull(state, "state cannot be null");
        }

        public Builder customData(Map<String, Object> customData) {
            this.customData = customData;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public InterruptionMetadata build() {
            return new InterruptionMetadata(this);
        }
    }
}
