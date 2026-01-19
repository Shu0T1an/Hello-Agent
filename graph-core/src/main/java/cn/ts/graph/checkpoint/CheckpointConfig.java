package cn.ts.graph.checkpoint;

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 检查点配置
 * <p>
 * 用于配置检查点的行为，包括策略、节点、历史大小和 TTL
 * 参考 Spring AI Alibaba 的 CheckpointConfig 设计
 * </p>
 *
 * @author tianshuo
 */
public class CheckpointConfig {

    private final CheckpointStrategy strategy;
    private final Set<String> checkpointNodes;
    private final int maxHistorySize;
    private final Duration ttl;

    private CheckpointConfig(Builder builder) {
        this.strategy = builder.strategy;
        this.checkpointNodes = Set.copyOf(builder.checkpointNodes);
        this.maxHistorySize = builder.maxHistorySize;
        this.ttl = builder.ttl;
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
     * 获取检查点策略
     *
     * @return 检查点策略
     */
    public CheckpointStrategy getStrategy() {
        return strategy;
    }

    /**
     * 获取需要创建检查点的节点集合
     *
     * @return 节点ID集合
     */
    public Set<String> getCheckpointNodes() {
        return checkpointNodes;
    }

    /**
     * 获取最大历史记录数
     *
     * @return 最大历史记录数
     */
    public int getMaxHistorySize() {
        return maxHistorySize;
    }

    /**
     * 获取 TTL（生存时间）
     *
     * @return TTL
     */
    public Duration getTtl() {
        return ttl;
    }

    /**
     * 检查是否应该为指定节点创建检查点
     *
     * @param nodeId 节点ID
     * @return true 如果应该创建检查点
     */
    public boolean shouldCheckpoint(String nodeId) {
        return switch (strategy) {
            case ALWAYS -> true;
            case ON_SPECIFIC_NODES -> checkpointNodes.contains(nodeId);
            case MANUAL, ERROR -> false;
        };
    }

    /**
     * 检查是否应该在错误时创建检查点
     *
     * @return true 如果应该在错误时创建检查点
     */
    public boolean shouldCheckpointOnError() {
        return strategy == CheckpointStrategy.ERROR || strategy == CheckpointStrategy.ALWAYS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckpointConfig that = (CheckpointConfig) o;
        return maxHistorySize == that.maxHistorySize
                && strategy == that.strategy
                && Objects.equals(checkpointNodes, that.checkpointNodes)
                && Objects.equals(ttl, that.ttl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(strategy, checkpointNodes, maxHistorySize, ttl);
    }

    @Override
    public String toString() {
        return "CheckpointConfig{" +
                "strategy=" + strategy +
                ", checkpointNodes=" + checkpointNodes +
                ", maxHistorySize=" + maxHistorySize +
                ", ttl=" + ttl +
                '}';
    }

    /**
     * 检查点策略
     */
    public enum CheckpointStrategy {
        /**
         * 每个节点后自动检查点
         */
        ALWAYS,
        /**
         * 指定节点检查点
         */
        ON_SPECIFIC_NODES,
        /**
         * 仅手动检查点
         */
        MANUAL,
        /**
         * 仅错误时检查点
         */
        ERROR
    }

    /**
     * 构建器
     */
    public static class Builder {
        private CheckpointStrategy strategy = CheckpointStrategy.ALWAYS;
        private Set<String> checkpointNodes = new HashSet<>();
        private int maxHistorySize = 100;
        private Duration ttl = Duration.ofDays(7);

        /**
         * 设置检查点策略
         *
         * @param strategy 检查点策略
         * @return this
         */
        public Builder strategy(CheckpointStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        /**
         * 设置需要创建检查点的节点
         *
         * @param nodes 节点ID集合
         * @return this
         */
        public Builder checkpointNodes(Set<String> nodes) {
            this.checkpointNodes = new HashSet<>(nodes);
            return this;
        }

        /**
         * 设置最大历史记录数
         *
         * @param maxHistorySize 最大历史记录数
         * @return this
         */
        public Builder maxHistorySize(int maxHistorySize) {
            this.maxHistorySize = maxHistorySize;
            return this;
        }

        /**
         * 设置 TTL
         *
         * @param ttl TTL
         * @return this
         */
        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        /**
         * 构建 CheckpointConfig
         *
         * @return CheckpointConfig 实例
         */
        public CheckpointConfig build() {
            return new CheckpointConfig(this);
        }
    }
}
