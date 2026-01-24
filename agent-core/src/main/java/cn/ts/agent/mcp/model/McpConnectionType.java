package cn.ts.agent.mcp.model;

/**
 * MCP 连接类型枚举
 * <p>
 * 定义支持的 MCP Server 连接类型
 * </p>
 *
 * @author tianshuo
 */
public enum McpConnectionType {
    /**
     * 本地进程连接（标准输入输出）
     */
    STDIO,

    /**
     * 服务器发送事件连接
     */
    SSE,

    /**
     * HTTP 连接
     */
    HTTP
}
