package cn.ts.agent.mcp.model;

/**
 * MCP 连接状态枚举
 * <p>
 * 定义 MCP Server 连接的生命周期状态
 * </p>
 *
 * @author tianshuo
 */
public enum McpConnectionStatus {
    /**
     * 已连接
     */
    CONNECTED,

    /**
     * 已断开
     */
    DISCONNECTED,

    /**
     * 连接中
     */
    CONNECTING,

    /**
     * 连接错误
     */
    ERROR
}
