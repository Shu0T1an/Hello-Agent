package cn.ts.web.agent.dto;

/**
 * MCP 连接类型枚举
 */
public enum ConnectionType {
    /**
     * 标准输入输出连接（适用于 npx 包）
     */
    STDIO,

    /**
     * 服务器发送事件连接
     */
    SSE
}
