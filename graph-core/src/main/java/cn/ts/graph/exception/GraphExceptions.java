package cn.ts.graph.exception;

import cn.ts.graph.GraphException;

/**
 * Graph 框架统一异常类
 * <p>
 * 提供统一的异常处理机制，定义标准错误响应格式
 * </p>
 *
 * @author tianshuo
 */
public final class GraphExceptions {

    private GraphExceptions() {
        // 防止实例化
    }

    /**
     * 节点执行异常
     */
    public static class NodeExecutionException extends GraphException {
        private final String nodeName;
        private final String executionId;

        public NodeExecutionException(String nodeName, String message) {
            super(message);
            this.nodeName = nodeName;
            this.executionId = null;
        }

        public NodeExecutionException(String nodeName, String executionId, String message) {
            super(String.format("[%s][%s] %s", nodeName, executionId, message));
            this.nodeName = nodeName;
            this.executionId = executionId;
        }

        public NodeExecutionException(String nodeName, String executionId, String message, Throwable cause) {
            super(String.format("[%s][%s] %s", nodeName, executionId, message), cause);
            this.nodeName = nodeName;
            this.executionId = executionId;
        }

        public String getNodeName() {
            return nodeName;
        }

        public String getExecutionId() {
            return executionId;
        }
    }

    /**
     * 状态异常
     */
    public static class StateException extends GraphException {
        private final String stateKey;

        public StateException(String stateKey, String message) {
            super(String.format("State key '%s': %s", stateKey, message));
            this.stateKey = stateKey;
        }

        public StateException(String stateKey, String message, Throwable cause) {
            super(String.format("State key '%s': %s", stateKey, message), cause);
            this.stateKey = stateKey;
        }

        public String getStateKey() {
            return stateKey;
        }
    }

    /**
     * 路由异常
     */
    public static class RoutingException extends GraphException {
        private final String fromNode;
        private final String condition;

        public RoutingException(String fromNode, String condition, String message) {
            super(String.format("Routing from '%s' (condition: %s): %s", fromNode, condition, message));
            this.fromNode = fromNode;
            this.condition = condition;
        }

        public String getFromNode() {
            return fromNode;
        }

        public String getCondition() {
            return condition;
        }
    }

    /**
     * 检查点异常
     */
    public static class CheckpointException extends GraphException {
        private final String threadId;
        private final String checkpointId;

        public CheckpointException(String threadId, String message) {
            super(String.format("Thread '%s': %s", threadId, message));
            this.threadId = threadId;
            this.checkpointId = null;
        }

        public CheckpointException(String threadId, String checkpointId, String message) {
            super(String.format("Thread '%s', Checkpoint '%s': %s", threadId, checkpointId, message));
            this.threadId = threadId;
            this.checkpointId = checkpointId;
        }

        public CheckpointException(String threadId, String checkpointId, String message, Throwable cause) {
            super(String.format("Thread '%s', Checkpoint '%s': %s", threadId, checkpointId, message), cause);
            this.threadId = threadId;
            this.checkpointId = checkpointId;
        }

        public String getThreadId() {
            return threadId;
        }

        public String getCheckpointId() {
            return checkpointId;
        }
    }

    /**
     * 配置异常
     */
    public static class ConfigurationException extends GraphException {
        private final String configKey;

        public ConfigurationException(String configKey, String message) {
            super(String.format("Configuration '%s': %s", configKey, message));
            this.configKey = configKey;
        }

        public ConfigurationException(String configKey, String message, Throwable cause) {
            super(String.format("Configuration '%s': %s", configKey, message), cause);
            this.configKey = configKey;
        }

        public String getConfigKey() {
            return configKey;
        }
    }

    /**
     * 创建节点执行异常的工厂方法
     */
    public static NodeExecutionException nodeExecutionError(String nodeName, String message) {
        return new NodeExecutionException(nodeName, message);
    }

    public static NodeExecutionException nodeExecutionError(String nodeName, String executionId, String message) {
        return new NodeExecutionException(nodeName, executionId, message);
    }

    public static NodeExecutionException nodeExecutionError(String nodeName, String executionId, String message, Throwable cause) {
        return new NodeExecutionException(nodeName, executionId, message, cause);
    }

    /**
     * 创建状态异常的工厂方法
     */
    public static StateException stateError(String stateKey, String message) {
        return new StateException(stateKey, message);
    }

    public static StateException stateError(String stateKey, String message, Throwable cause) {
        return new StateException(stateKey, message, cause);
    }

    /**
     * 创建路由异常的工厂方法
     */
    public static RoutingException routingError(String fromNode, String condition, String message) {
        return new RoutingException(fromNode, condition, message);
    }

    /**
     * 创建检查点异常的工厂方法
     */
    public static CheckpointException checkpointError(String threadId, String message) {
        return new CheckpointException(threadId, message);
    }

    public static CheckpointException checkpointError(String threadId, String checkpointId, String message) {
        return new CheckpointException(threadId, checkpointId, message);
    }

    public static CheckpointException checkpointError(String threadId, String checkpointId, String message, Throwable cause) {
        return new CheckpointException(threadId, checkpointId, message, cause);
    }

    /**
     * 创建配置异常的工厂方法
     */
    public static ConfigurationException configurationError(String configKey, String message) {
        return new ConfigurationException(configKey, message);
    }

    public static ConfigurationException configurationError(String configKey, String message, Throwable cause) {
        return new ConfigurationException(configKey, message, cause);
    }
}
