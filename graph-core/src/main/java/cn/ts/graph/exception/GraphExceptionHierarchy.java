package cn.ts.graph.exception;

import cn.ts.graph.GraphException;

/**
 * Graph 框架异常层次结构
 * <p>
 * 扩展基础的 GraphException，提供更细粒度的异常分类
 * 便于错误处理和日志记录
 * </p>
 *
 * @author tianshuo
 */
public final class GraphExceptionHierarchy {

    private GraphExceptionHierarchy() {
        // 防止实例化
    }

    /**
     * 状态异常
     * <p>
     * 当状态操作失败时抛出，如状态键不存在、状态合并失败等
     * </p>
     */
    public static class StateException extends GraphException {
        public StateException(String message) {
            super("State error: " + message);
        }

        public StateException(String message, Throwable cause) {
            super("State error: " + message, cause);
        }
    }

    /**
     * 检查点异常
     * <p>
     * 当检查点操作失败时抛出
     * </p>
     */
    public static class CheckpointException extends GraphException {
        public CheckpointException(String message) {
            super("Checkpoint error: " + message);
        }

        public CheckpointException(String message, Throwable cause) {
            super("Checkpoint error: " + message, cause);
        }
    }

    /**
     * 配置异常
     * <p>
     * 当配置无效时抛出
     * </p>
     */
    public static class ConfigurationException extends GraphException {
        public ConfigurationException(String message) {
            super("Configuration error: " + message);
        }

        public ConfigurationException(String message, Throwable cause) {
            super("Configuration error: " + message, cause);
        }
    }

    /**
     * 超时异常
     * <p>
     * 当操作超时时抛出
     * </p>
     */
    public static class TimeoutException extends GraphException {
        private final long timeoutMillis;

        public TimeoutException(String message, long timeoutMillis) {
            super(message);
            this.timeoutMillis = timeoutMillis;
        }

        public TimeoutException(String message, long timeoutMillis, Throwable cause) {
            super(message, cause);
            this.timeoutMillis = timeoutMillis;
        }

        public long getTimeoutMillis() {
            return timeoutMillis;
        }
    }

    /**
     * 验证异常
     * <p>
     * 当输入验证失败时抛出
     * </p>
     */
    public static class ValidationException extends GraphException {
        public ValidationException(String message) {
            super("Validation error: " + message);
        }

        public ValidationException(String message, Throwable cause) {
            super("Validation error: " + message, cause);
        }
    }

    /**
     * 中断异常
     * <p>
     * 当执行被中断时抛出
     * </p>
     */
    public static class InterruptionException extends GraphException {
        private final String checkpointId;

        public InterruptionException(String message, String checkpointId) {
            super(message);
            this.checkpointId = checkpointId;
        }

        public String getCheckpointId() {
            return checkpointId;
        }
    }
}
