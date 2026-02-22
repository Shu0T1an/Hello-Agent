package cn.ts.web.infra.mcp.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * MCP 连接配置实体类
 */
@Data
public class McpConnectionConfigEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 连接名称（唯一标识）
     */
    private String connectionName;

    /**
     * 连接描述
     */
    private String description;

    /**
     * 连接类型（STDIO/SSE）
     */
    private String connectionType;

    /**
     * 命令（STDIO类型）
     */
    private String command;

    /**
     * 参数列表（JSON格式）
     */
    private String args;

    /**
     * 环境变量（JSON格式）
     */
    private String env;

    /**
     * URL（SSE类型）
     */
    private String url;

    /**
     * 超时时间（秒）
     */
    private Integer timeoutSeconds;

    /**
     * 是否自动重连
     */
    private Boolean autoReconnect;

    /**
     * 最大重试次数
     */
    private Integer maxRetries;

    /**
     * 重试间隔（秒）
     */
    private Integer retryIntervalSeconds;

    /**
     * 是否激活
     */
    private Boolean isActive;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;
}
