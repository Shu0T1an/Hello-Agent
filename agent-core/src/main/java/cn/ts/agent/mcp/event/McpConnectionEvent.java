package cn.ts.agent.mcp.event;

import cn.ts.agent.mcp.model.McpConnectionStatus;
import org.springframework.context.ApplicationEvent;

/**
 * MCP 连接事件
 * <p>
 * 当 MCP 连接状态发生变化时发布此事件
 * </p>
 *
 * @author tianshuo
 */
public class McpConnectionEvent extends ApplicationEvent {

    private final String connectionName;
    private final McpConnectionStatus status;
    private final String errorMessage;

    /**
     * 创建 MCP 连接事件
     *
     * @param source          事件源
     * @param connectionName  连接名称
     * @param status          连接状态
     * @param errorMessage    错误信息（如果状态为 ERROR）
     */
    public McpConnectionEvent(Object source, String connectionName,
                              McpConnectionStatus status, String errorMessage) {
        super(source);
        this.connectionName = connectionName;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    /**
     * 创建成功连接事件
     */
    public static McpConnectionEvent connected(Object source, String connectionName) {
        return new McpConnectionEvent(source, connectionName, McpConnectionStatus.CONNECTED, null);
    }

    /**
     * 创建断开连接事件
     */
    public static McpConnectionEvent disconnected(Object source, String connectionName) {
        return new McpConnectionEvent(source, connectionName, McpConnectionStatus.DISCONNECTED, null);
    }

    /**
     * 创建错误事件
     */
    public static McpConnectionEvent error(Object source, String connectionName, String errorMessage) {
        return new McpConnectionEvent(source, connectionName, McpConnectionStatus.ERROR, errorMessage);
    }

    public String getConnectionName() {
        return connectionName;
    }

    public McpConnectionStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "McpConnectionEvent{" +
                "connectionName='" + connectionName + '\'' +
                ", status=" + status +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
