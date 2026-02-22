package cn.ts.web.agent.dto;

/**
 * 工具类型枚举
 */
public enum ToolType {
    /**
     * 本地工具（Spring AI @Tool 注解）
     */
    LOCAL,

    /**
     * MCP 工具（Model Context Protocol）
     */
    MCP
}
