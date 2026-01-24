package cn.ts.agent.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * MCP 管理器配置属性类
 * <p>
 * 从 application.yml 中读取配置，格式为 mcp.manager.*
 * </p>
 *
 * @author tianshuo
 */
@Data
@ConfigurationProperties(prefix = "mcp.manager")
public class McpManagerConfig {

    /**
     * 健康检查间隔
     */
    private Duration healthCheckInterval = Duration.ofMinutes(1);

    /**
     * 默认连接超时时间
     */
    private Duration defaultTimeout = Duration.ofSeconds(30);

    /**
     * 应用启动时是否自动连接
     */
    private boolean autoConnectOnStartup = true;

    /**
     * 默认最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 默认重试间隔
     */
    private Duration retryInterval = Duration.ofSeconds(5);

    /**
     * 是否启用健康检查
     */
    private boolean enableHealthCheck = true;

    /**
     * 健康检查超时时间
     */
    private Duration healthCheckTimeout = Duration.ofSeconds(10);
}
