package cn.ts.web.config;

import cn.ts.agent.mcp.McpManager;
import cn.ts.agent.mcp.McpManagerImpl;
import cn.ts.agent.mcp.config.McpManagerConfig;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 管理器自动配置类
 * <p>
 * 负责 McpManager Bean 的创建和生命周期管理
 * </p>
 *
 * @author tianshuo
 */
@Configuration
@EnableConfigurationProperties(McpManagerConfig.class)
public class McpManagerAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(McpManagerAutoConfiguration.class);

    private final McpManagerConfig config;
    private McpManager mcpManager;

    public McpManagerAutoConfiguration(McpManagerConfig config) {
        this.config = config;
    }

    /**
     * 创建 McpManager Bean
     */
    @Bean
    public McpManager mcpManager() {
        logger.info("初始化 MCP 管理器...");
        logger.info("健康检查间隔: {}", config.getHealthCheckInterval());
        logger.info("默认超时时间: {}", config.getDefaultTimeout());
        logger.info("启动时自动连接: {}", config.isAutoConnectOnStartup());
        logger.info("启用健康检查: {}", config.isEnableHealthCheck());

        McpManager manager = new McpManagerImpl(config);

        // 如果配置了启动时自动连接，则启动管理器
        if (config.isAutoConnectOnStartup()) {
            manager.start();
        }

        this.mcpManager = manager;
        logger.info("MCP 管理器初始化完成");
        return manager;
    }

    /**
     * 应用关闭时停止 MCP 管理器
     */
    @PreDestroy
    public void destroy() {
        if (mcpManager != null && mcpManager.isRunning()) {
            logger.info("停止 MCP 管理器...");
            mcpManager.stop();
            logger.info("MCP 管理器已停止");
        }
    }
}
