package cn.ts.web.config;

import cn.ts.agent.mcp.model.McpConnectionConfig;
import cn.ts.agent.mcp.model.McpConnectionType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务器配置类
 * 从配置文件读取 MCP 服务器连接配置
 *
 * @author tianshuo
 */
@Configuration
@ConfigurationProperties(prefix = "mcp.servers")
@Data
public class McpServerConfig {

    /**
     * MCP 服务器列表
     */
    private List<ServerConfig> servers;

    /**
     * 单个 MCP 服务器配置
     */
    @Data
    public static class ServerConfig {
        /**
         * 服务器名称
         */
        private String name;

        /**
         * 连接类型（STDIO/SSE）
         */
        private McpConnectionType type;

        /**
         * 服务器描述
         */
        private String description;

        /**
         * STDIO 连接配置
         */
        private StdioConfigProperties stdio;

        /**
         * 连接超时时间（秒）
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
         * STDIO 配置属性
         */
        @Data
        public static class StdioConfigProperties {
            /**
             * 执行命令路径
             */
            private String command;

            /**
             * 命令参数
             */
            private List<String> args;

            /**
             * 环境变量
             */
            private Map<String, String> env;
        }

        /**
         * 转换为 McpConnectionConfig
         */
        public McpConnectionConfig toMcpConnectionConfig() {
            var builder = McpConnectionConfig.builder()
                    .name(this.name)
                    .type(this.type)
                    .description(this.description);

            // 配置 STDIO
            if (this.stdio != null) {
                McpConnectionConfig.StdioConfig stdioConfig = McpConnectionConfig.StdioConfig.builder()
                        .command(this.stdio.getCommand())
                        .args(this.stdio.getArgs())
                        .env(this.stdio.getEnv())
                        .build();
                builder.stdioConfig(stdioConfig);
            }

            // 配置超时和重试
            if (this.timeoutSeconds != null) {
                builder.timeout(Duration.ofSeconds(this.timeoutSeconds));
            }
            if (this.autoReconnect != null) {
                builder.autoReconnect(this.autoReconnect);
            }
            if (this.maxRetries != null) {
                builder.maxRetries(this.maxRetries);
            }
            if (this.retryIntervalSeconds != null) {
                builder.retryInterval(Duration.ofSeconds(this.retryIntervalSeconds));
            }

            return builder.build();
        }
    }
}
