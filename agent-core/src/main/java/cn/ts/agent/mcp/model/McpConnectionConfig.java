package cn.ts.agent.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 连接配置类
 * <p>
 * 包含创建 MCP 连接所需的所有配置信息
 * </p>
 *
 * @author tianshuo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpConnectionConfig {

    /**
     * 连接名称（唯一标识）
     */
    private String name;

    /**
     * 连接类型
     */
    private McpConnectionType type;

    /**
     * 连接描述
     */
    private String description;

    /**
     * STDIO 连接配置
     */
    private StdioConfig stdioConfig;

    /**
     * SSE 连接配置
     */
    private SseConfig sseConfig;

    /**
     * HTTP 连接配置
     */
    private HttpConfig httpConfig;

    /**
     * 连接超时时间
     */
    @Builder.Default
    private Duration timeout = Duration.ofSeconds(30);

    /**
     * 是否启用自动重连
     */
    @Builder.Default
    private boolean autoReconnect = true;

    /**
     * 最大重试次数
     */
    @Builder.Default
    private int maxRetries = 3;

    /**
     * 重试间隔
     */
    @Builder.Default
    private Duration retryInterval = Duration.ofSeconds(5);

    /**
     * STDIO 连接配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StdioConfig {
        /**
         * 要执行的命令
         */
        private String command;

        /**
         * 命令参数列表
         */
        private List<String> args;

        /**
         * 环境变量
         */
        @Builder.Default
        private Map<String, String> env = new HashMap<>();

        /**
         * 工作目录
         */
        private String workingDir;
    }

    /**
     * SSE 连接配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SseConfig {
        /**
         * SSE 服务端点 URL
         */
        private String url;

        /**
         * 请求头
         */
        @Builder.Default
        private Map<String, String> headers = new HashMap<>();
    }

    /**
     * HTTP 连接配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HttpConfig {
        /**
         * HTTP 服务端点 URL
         */
        private String url;

        /**
         * 请求头
         */
        @Builder.Default
        private Map<String, String> headers = new HashMap<>();

        /**
         * HTTP 方法
         */
        @Builder.Default
        private String method = "POST";
    }
}
