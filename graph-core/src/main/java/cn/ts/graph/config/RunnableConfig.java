package cn.ts.graph.config;

import cn.ts.graph.GraphResult;
import cn.ts.graph.event.ExecutionEvent;
import cn.ts.graph.hook.JumpTo;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 图运行配置
 * <p>
 * 用于配置图执行时的各种行为参数。
 * 本类是线程安全的，可以在多线程环境下安全使用。
 * </p>
 *
 * @author tianshuo
 */
public class RunnableConfig {

    private final Duration timeout;
    private final Duration nodeTimeout;
    private final int maxIterations;
    private final boolean interruptOnError;
    private final Consumer<GraphResult.NodeExecution> onNodeStart;
    private final Consumer<GraphResult.NodeExecution> onNodeComplete;
    private final Consumer<GraphResult> onComplete;
    private final Consumer<GraphResult> onError;
    private final boolean debugMode;
    private final Consumer<ExecutionEvent> eventSink;
    private final boolean streamEnabled;
    private final String executionId;
    private final String threadId;
    private final String checkpointId;
    private final JumpTo jumpTo;
    private final Map<String, Object> feedbackData;
    private final String startNode;

    private RunnableConfig(Builder builder) {
        this.timeout = builder.timeout;
        this.nodeTimeout = builder.nodeTimeout;
        this.maxIterations = builder.maxIterations;
        this.interruptOnError = builder.interruptOnError;
        this.onNodeStart = builder.onNodeStart;
        this.onNodeComplete = builder.onNodeComplete;
        this.onComplete = builder.onComplete;
        this.onError = builder.onError;
        this.debugMode = builder.debugMode;
        this.eventSink = builder.eventSink;
        this.streamEnabled = builder.streamEnabled;
        this.executionId = builder.executionId;
        this.threadId = builder.threadId;
        this.checkpointId = builder.checkpointId;
        this.jumpTo = builder.jumpTo;
        this.feedbackData = builder.feedbackData;
        this.startNode = builder.startNode;
    }

    public Duration timeout() {
        return timeout;
    }

    public Duration nodeTimeout() {
        return nodeTimeout;
    }

    public int maxIterations() {
        return maxIterations;
    }

    public boolean interruptOnError() {
        return interruptOnError;
    }

    public Consumer<GraphResult.NodeExecution> onNodeStart() {
        return onNodeStart;
    }

    public Consumer<GraphResult.NodeExecution> onNodeComplete() {
        return onNodeComplete;
    }

    public Consumer<GraphResult> onComplete() {
        return onComplete;
    }

    public Consumer<GraphResult> onError() {
        return onError;
    }

    public boolean debugMode() {
        return debugMode;
    }

    /**
     * 获取事件接收器
     * <p>
     * 用于在流式执行模式下发射执行事件
     * </p>
     *
     * @return 事件接收器，可能为 null
     */
    public Consumer<ExecutionEvent> eventSink() {
        return eventSink;
    }

    /**
     * 是否启用流式执行
     *
     * @return true 如果启用流式执行
     */
    public boolean streamEnabled() {
        return streamEnabled;
    }

    /**
     * 获取执行ID
     * <p>
     * 用于标识一次图执行，便于追踪和调试
     * </p>
     *
     * @return 执行ID，可能为 null
     */
    public String executionId() {
        return executionId;
    }

    /**
     * 获取会话ID
     * <p>
     * 用于标识一个会话，支持连续对话和状态恢复
     * </p>
     *
     * @return 会话ID，可能为 null
     */
    public String threadId() {
        return threadId;
    }

    /**
     * 获取检查点ID
     * <p>
     * 用于从指定检查点恢复执行
     * </p>
     *
     * @return 检查点ID，可能为 null
     */
    public String checkpointId() {
        return checkpointId;
    }

    /**
     * 获取跳转目标
     * <p>
     * 用于 Hook 跳转控制，跳转到指定位置
     * </p>
     *
     * @return 跳转目标，可能为 null
     */
    public JumpTo jumpTo() {
        return jumpTo;
    }

    /**
     * 获取反馈数据
     * <p>
     * 用于从中断恢复时传递用户反馈数据
     * </p>
     *
     * @return 反馈数据，可能为 null
     */
    public Map<String, Object> feedbackData() {
        return feedbackData;
    }

    /**
     * 获取起始节点
     * <p>
     * 用于从指定节点开始执行（常用于从检查点恢复）
     * 如果为 null，则使用图的默认入口点
     * </p>
     *
     * @return 起始节点ID，可能为 null
     */
    public String startNode() {
        return startNode;
    }

    /**
     * 创建带有跳转目标的新配置
     *
     * @param jumpTo 跳转目标
     * @return 新的 RunnableConfig
     */
    public RunnableConfig withJumpTo(JumpTo jumpTo) {
        return new Builder(this).jumpTo(jumpTo).build();
    }

    /**
     * 创建带有反馈数据的新配置
     *
     * @param feedbackData 反馈数据
     * @return 新的 RunnableConfig
     */
    public RunnableConfig withFeedbackData(Map<String, Object> feedbackData) {
        return new Builder(this).feedbackData(feedbackData).build();
    }

    /**
     * 创建一个新的 Builder
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder(null);
    }

    /**
     * 获取默认配置
     *
     * @return 默认的 RunnableConfig
     */
    public static RunnableConfig defaultConfig() {
        return new Builder(null).build();
    }

    public static class Builder {
        private Duration timeout = null;
        private Duration nodeTimeout = Duration.ofSeconds(30);
        private int maxIterations = 1000;
        private boolean interruptOnError = true;
        private Consumer<GraphResult.NodeExecution> onNodeStart = null;
        private Consumer<GraphResult.NodeExecution> onNodeComplete = null;
        private Consumer<GraphResult> onComplete = null;
        private Consumer<GraphResult> onError = null;
        private boolean debugMode = false;
        private Consumer<ExecutionEvent> eventSink = null;
        private boolean streamEnabled = false;
        private String executionId = null;
        private String threadId = null;
        private String checkpointId = null;
        private JumpTo jumpTo = null;
        private Map<String, Object> feedbackData = null;
        private String startNode = null;

        /**
         * 从现有配置创建 Builder
         *
         * @param config 现有配置（可以为 null）
         */
        private Builder(RunnableConfig config) {
            if (config != null) {
                this.timeout = config.timeout;
                this.nodeTimeout = config.nodeTimeout;
                this.maxIterations = config.maxIterations;
                this.interruptOnError = config.interruptOnError;
                this.onNodeStart = config.onNodeStart;
                this.onNodeComplete = config.onNodeComplete;
                this.onComplete = config.onComplete;
                this.onError = config.onError;
                this.debugMode = config.debugMode;
                this.eventSink = config.eventSink;
                this.streamEnabled = config.streamEnabled;
                this.executionId = config.executionId;
                this.threadId = config.threadId;
                this.checkpointId = config.checkpointId;
                this.jumpTo = config.jumpTo;
                this.feedbackData = config.feedbackData;
                this.startNode = config.startNode;
            }
            // 如果 config 为 null，使用默认值
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder nodeTimeout(Duration nodeTimeout) {
            this.nodeTimeout = nodeTimeout;
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public Builder interruptOnError(boolean interruptOnError) {
            this.interruptOnError = interruptOnError;
            return this;
        }

        public Builder onNodeStart(Consumer<GraphResult.NodeExecution> callback) {
            this.onNodeStart = callback;
            return this;
        }

        public Builder onNodeComplete(Consumer<GraphResult.NodeExecution> callback) {
            this.onNodeComplete = callback;
            return this;
        }

        public Builder onComplete(Consumer<GraphResult> callback) {
            this.onComplete = callback;
            return this;
        }

        public Builder onError(Consumer<GraphResult> callback) {
            this.onError = callback;
            return this;
        }

        public Builder debugMode(boolean debugMode) {
            this.debugMode = debugMode;
            return this;
        }

        /**
         * 设置事件接收器
         *
         * @param eventSink 事件接收器
         * @return this
         */
        public Builder eventSink(Consumer<ExecutionEvent> eventSink) {
            this.eventSink = eventSink;
            return this;
        }

        /**
         * 启用流式执行
         *
         * @param streamEnabled 是否启用流式执行
         * @return this
         */
        public Builder streamEnabled(boolean streamEnabled) {
            this.streamEnabled = streamEnabled;
            return this;
        }

        /**
         * 设置执行ID
         *
         * @param executionId 执行ID
         * @return this
         */
        public Builder executionId(String executionId) {
            this.executionId = executionId;
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
         * 设置跳转目标
         *
         * @param jumpTo 跳转目标
         * @return this
         */
        public Builder jumpTo(JumpTo jumpTo) {
            this.jumpTo = jumpTo;
            return this;
        }

        /**
         * 设置反馈数据
         *
         * @param feedbackData 反馈数据
         * @return this
         */
        public Builder feedbackData(Map<String, Object> feedbackData) {
            this.feedbackData = feedbackData;
            return this;
        }

        /**
         * 设置起始节点
         *
         * @param startNode 起始节点ID
         * @return this
         */
        public Builder startNode(String startNode) {
            this.startNode = startNode;
            return this;
        }

        public RunnableConfig build() {
            return new RunnableConfig(this);
        }
    }
}
