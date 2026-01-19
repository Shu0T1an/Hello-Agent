package cn.ts.graph;

import cn.ts.graph.state.State;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 图执行结果
 * <p>
 * 封装图执行后的结果信息，包括最终状态和执行历史
 * </p>
 *
 * @author tianshuo
 */
public class GraphResult {

    private final State finalState;
    private final List<NodeExecution> executionHistory;
    private final Instant startTime;
    private final Instant endTime;
    private final boolean success;
    private final Throwable error;

    private GraphResult(Builder builder) {
        this.finalState = builder.finalState;
        this.executionHistory = builder.executionHistory;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.success = builder.success;
        this.error = builder.error;
    }

    /**
     * 创建一个成功的执行结果
     *
     * @param finalState        最终状态
     * @param executionHistory  执行历史
     * @param startTime         开始时间
     * @param endTime           结束时间
     * @return 执行结果
     */
    public static GraphResult success(State finalState, List<NodeExecution> executionHistory, Instant startTime, Instant endTime) {
        return new Builder()
                .finalState(finalState)
                .executionHistory(executionHistory)
                .startTime(startTime)
                .endTime(endTime)
                .success(true)
                .build();
    }

    /**
     * 创建一个失败的执行结果
     *
     * @param error             错误
     * @param executionHistory  执行历史
     * @param startTime         开始时间
     * @param endTime           结束时间
     * @return 执行结果
     */
    public static GraphResult failure(Throwable error, List<NodeExecution> executionHistory, Instant startTime, Instant endTime) {
        return new Builder()
                .error(error)
                .executionHistory(executionHistory)
                .startTime(startTime)
                .endTime(endTime)
                .success(false)
                .build();
    }

    /**
     * 获取最终状态
     *
     * @return 最终状态
     */
    public State finalState() {
        return finalState;
    }

    /**
     * 获取执行历史
     *
     * @return 执行历史列表
     */
    public List<NodeExecution> executionHistory() {
        return executionHistory;
    }

    /**
     * 获取执行是否成功
     *
     * @return 如果成功返回 true，否则返回 false
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取执行是否失败
     *
     * @return 如果失败返回 true，否则返回 false
     */
    public boolean isFailure() {
        return !success;
    }

    /**
     * 获取错误（仅当执行失败时）
     *
     * @return 错误对象，如果成功则返回 null
     */
    public Throwable error() {
        return error;
    }

    /**
     * 获取执行时长
     *
     * @return 执行时长
     */
    public Duration duration() {
        return Duration.between(startTime, endTime);
    }

    /**
     * 获取开始时间
     *
     * @return 开始时间
     */
    public Instant startTime() {
        return startTime;
    }

    /**
     * 获取结束时间
     *
     * @return 结束时间
     */
    public Instant endTime() {
        return endTime;
    }

    /**
     * 获取执行的节点数量
     *
     * @return 节点数量
     */
    public int executedNodeCount() {
        return executionHistory.size();
    }

    @Override
    public String toString() {
        if (success) {
            return "GraphResult{" +
                    "success=" + success +
                    ", executedNodes=" + executedNodeCount() +
                    ", duration=" + duration().toMillis() + "ms" +
                    '}';
        } else {
            return "GraphResult{" +
                    "success=" + success +
                    ", error='" + error.getMessage() + '\'' +
                    ", executedNodes=" + executedNodeCount() +
                    ", duration=" + duration().toMillis() + "ms" +
                    '}';
        }
    }

    /**
     * 节点执行记录
     */
    public static class NodeExecution {
        private final String nodeId;
        private final Instant startTime;
        private final Instant endTime;
        private final Duration duration;
        private final Throwable error;

        public NodeExecution(String nodeId, Instant startTime, Instant endTime, Throwable error) {
            this.nodeId = nodeId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.duration = Duration.between(startTime, endTime);
            this.error = error;
        }

        public NodeExecution(String nodeId, Instant startTime, Instant endTime) {
            this(nodeId, startTime, endTime, null);
        }

        public String nodeId() {
            return nodeId;
        }

        public Instant startTime() {
            return startTime;
        }

        public Instant endTime() {
            return endTime;
        }

        public Duration duration() {
            return duration;
        }

        public boolean hasError() {
            return error != null;
        }

        public Throwable error() {
            return error;
        }

        @Override
        public String toString() {
            if (hasError()) {
                return "NodeExecution{" +
                        "nodeId='" + nodeId + '\'' +
                        ", duration=" + duration.toMillis() + "ms" +
                        ", error='" + error.getMessage() + '\'' +
                        '}';
            }
            return "NodeExecution{" +
                    "nodeId='" + nodeId + '\'' +
                    ", duration=" + duration.toMillis() + "ms" +
                    '}';
        }
    }

    /**
     * Builder 模式
     */
    public static class Builder {
        private State finalState;
        private List<NodeExecution> executionHistory = new ArrayList<>();
        private Instant startTime;
        private Instant endTime;
        private boolean success;
        private Throwable error;

        public Builder finalState(State finalState) {
            this.finalState = finalState;
            return this;
        }

        public Builder executionHistory(List<NodeExecution> executionHistory) {
            this.executionHistory = executionHistory != null ? executionHistory : new ArrayList<>();
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

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder error(Throwable error) {
            this.error = error;
            return this;
        }

        public GraphResult build() {
            return new GraphResult(this);
        }
    }
}
