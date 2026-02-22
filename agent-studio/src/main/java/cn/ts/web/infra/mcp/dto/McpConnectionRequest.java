package cn.ts.web.infra.mcp.dto;

import cn.ts.agent.mcp.model.McpConnectionConfig;
import cn.ts.agent.mcp.model.McpConnectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 连接请求 DTO
 *
 * @author tianshuo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpConnectionRequest {

    /**
     * 连接名称（唯一标识）
     */
    @NotBlank(message = "连接名称不能为空")
    private String name;

    /**
     * 连接类型：STDIO, SSE, HTTP
     */
    @NotNull(message = "连接类型不能为空")
    private McpConnectionType type;

    /**
     * 连接描述
     */
    private String description;

    // STDIO 配置
    private String command;
    private List<String> args;
    private Map<String, String> env = new HashMap<>();
    private String workingDir;

    // SSE 配置
    private String sseUrl;
    private Map<String, String> sseHeaders = new HashMap<>();

    // HTTP 配置
    private String httpUrl;
    private Map<String, String> httpHeaders = new HashMap<>();
    private String httpMethod = "POST";

    /**
     * 连接超时时间（秒）
     */
    @Builder.Default
    private Integer timeoutSeconds = 30;

    /**
     * 是否启用自动重连
     */
    @Builder.Default
    private Boolean autoReconnect = true;

    /**
     * 最大重试次数
     */
    @Builder.Default
    private Integer maxRetries = 3;

    /**
     * 重试间隔（秒）
     */
    @Builder.Default
    private Integer retryIntervalSeconds = 5;

    /**
     * 转换为 McpConnectionConfig
     */
    public McpConnectionConfig toConfig() {
        McpConnectionConfig.McpConnectionConfigBuilder builder = McpConnectionConfig.builder()
                .name(name)
                .type(type)
                .description(description)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .autoReconnect(autoReconnect)
                .maxRetries(maxRetries)
                .retryInterval(Duration.ofSeconds(retryIntervalSeconds));

        // 根据类型设置对应的配置
        switch (type) {
            case STDIO -> builder.stdioConfig(McpConnectionConfig.StdioConfig.builder()
                    .command(command)
                    .args(args)
                    .env(env)
                    .workingDir(workingDir)
                    .build());

            case SSE -> builder.sseConfig(McpConnectionConfig.SseConfig.builder()
                    .url(sseUrl)
                    .headers(sseHeaders)
                    .build());

            case HTTP -> builder.httpConfig(McpConnectionConfig.HttpConfig.builder()
                    .url(httpUrl)
                    .headers(httpHeaders)
                    .method(httpMethod)
                    .build());
        }

        return builder.build();
    }
}
