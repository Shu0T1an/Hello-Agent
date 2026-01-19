package cn.ts.graph;

/**
 * Graph 框架异常基类
 * <p>
 * 定义 Graph 框架中可能出现的各种异常情况
 * </p>
 *
 * @author tianshuo
 */
public class GraphException extends RuntimeException {

    /**
     * 构造一个新的 Graph 异常
     *
     * @param message 异常消息
     */
    public GraphException(String message) {
        super(message);
    }

    /**
     * 构造一个新的 Graph 异常
     *
     * @param message 异常消息
     * @param cause   原因异常
     */
    public GraphException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 节点未找到异常
     */
    public static class NodeNotFoundException extends GraphException {
        public NodeNotFoundException(String nodeId) {
            super("Node not found: " + nodeId);
        }
    }

    /**
     * 边配置错误异常
     */
    public static class EdgeConfigurationException extends GraphException {
        public EdgeConfigurationException(String message) {
            super("Edge configuration error: " + message);
        }
    }

    /**
     * 图编译异常
     */
    public static class GraphCompileException extends GraphException {
        public GraphCompileException(String message) {
            super("Graph compile error: " + message);
        }

        public GraphCompileException(String message, Throwable cause) {
            super("Graph compile error: " + message, cause);
        }
    }

    /**
     * 节点执行异常
     */
    public static class NodeExecutionException extends GraphException {
        public NodeExecutionException(String nodeId, Throwable cause) {
            super("Node execution error in '" + nodeId + "': " + cause.getMessage(), cause);
        }
    }
}
