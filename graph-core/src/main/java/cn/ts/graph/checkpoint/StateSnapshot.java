package cn.ts.graph.checkpoint;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 状态快照
 * <p>
 * 包含图执行过程中的完整状态副本
 * 参考 Spring AI Alibaba 的 StateSnapshot 设计
 * </p>
 *
 * @author tianshuo
 */
public class StateSnapshot {

    private final String checkpointId;
    private final String threadId;
    private final String nodeId;
    private final Map<String, Object> state;
    private final CheckpointMetadata metadata;
    private final Instant timestamp;
    private final int iteration;

    private StateSnapshot(Builder builder) {
        this.checkpointId = builder.checkpointId;
        this.threadId = builder.threadId;
        this.nodeId = builder.nodeId;
        this.state = builder.state != null ? Map.copyOf(builder.state) : Map.of();
        this.metadata = builder.metadata;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.iteration = builder.iteration;
    }

    /**
     * 创建一个新的构建器
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取检查点ID
     *
     * @return 检查点ID
     */
    public String getCheckpointId() {
        return checkpointId;
    }

    /**
     * 获取会话ID
     *
     * @return 会话ID
     */
    public String getThreadId() {
        return threadId;
    }

    /**
     * 获取当前节点ID
     *
     * @return 当前节点ID
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * 获取完整状态副本
     *
     * @return 状态数据
     */
    public Map<String, Object> getState() {
        return state;
    }

    /**
     * 获取元数据
     *
     * @return 元数据
     */
    public CheckpointMetadata getMetadata() {
        return metadata;
    }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * 获取迭代次数
     *
     * @return 迭代次数
     */
    public int getIteration() {
        return iteration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StateSnapshot that = (StateSnapshot) o;
        return iteration == that.iteration
                && Objects.equals(checkpointId, that.checkpointId)
                && Objects.equals(threadId, that.threadId)
                && Objects.equals(nodeId, that.nodeId)
                && Objects.equals(state, that.state)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(checkpointId, threadId, nodeId, state, metadata, timestamp, iteration);
    }

    @Override
    public String toString() {
        return "StateSnapshot{" +
                "checkpointId='" + checkpointId + '\'' +
                ", threadId='" + threadId + '\'' +
                ", nodeId='" + nodeId + '\'' +
                ", state=" + state +
                ", metadata=" + metadata +
                ", timestamp=" + timestamp +
                ", iteration=" + iteration +
                '}';
    }

    /**
     * 构建器
     */
    public static class Builder {
        private String checkpointId = UUID.randomUUID().toString();
        private String threadId;
        private String nodeId;
        private Map<String, Object> state;
        private CheckpointMetadata metadata;
        private Instant timestamp;
        private int iteration = 0;

        /**
         * 设置检查点ID
         *
         * @param checkpointId 检查点ID
         * @return this
         */
        public Builder checkpointId(String checkpointId) {
            this.checkpointId = checkpointId;
            return this;
        }

        /**
         * 设置会话ID
         *
         * @param threadId 会话ID
         * @return this
         */
        public Builder threadId(String threadId) {
            this.threadId = threadId;
            return this;
        }

        /**
         * 设置当前节点ID
         *
         * @param nodeId 当前节点ID
         * @return this
         */
        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        /**
         * 设置状态数据
         *
         * @param state 状态数据
         * @return this
         */
        public Builder state(Map<String, Object> state) {
            this.state = state;
            return this;
        }

        /**
         * 设置元数据
         *
         * @param metadata 元数据
         * @return this
         */
        public Builder metadata(CheckpointMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * 设置创建时间
         *
         * @param timestamp 创建时间
         * @return this
         */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * 设置迭代次数
         *
         * @param iteration 迭代次数
         * @return this
         */
        public Builder iteration(int iteration) {
            this.iteration = iteration;
            return this;
        }

        /**
         * 构建 StateSnapshot
         *
         * @return StateSnapshot 实例
         */
        public StateSnapshot build() {
            if (threadId == null) {
                throw new IllegalArgumentException("ThreadId cannot be null");
            }
            return new StateSnapshot(this);
        }
    }
}
