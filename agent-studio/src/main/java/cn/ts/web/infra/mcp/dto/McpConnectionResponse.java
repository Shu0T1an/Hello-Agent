package cn.ts.web.infra.mcp.dto;

import cn.ts.agent.mcp.model.McpConnectionStatus;
import cn.ts.agent.mcp.model.McpConnectionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * MCP 连接响应 DTO
 *
 * @author tianshuo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpConnectionResponse {

    /**
     * 连接名称
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
     * 连接状态
     */
    private McpConnectionStatus status;

    /**
     * 连接时间
     */
    private Instant connectedAt;

    /**
     * 错误信息（如果状态为 ERROR）
     */
    private String errorMessage;

    /**
     * 配置信息摘要
     */
    private ConfigSummary config;

    /**
     * 统计信息
     */
    private StatisticsSummary statistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigSummary {
        private String command;
        private String url;
        private Integer timeoutSeconds;
        private Boolean autoReconnect;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticsSummary {
        private Long totalCalls;
        private Long successfulCalls;
        private Long failedCalls;
        private Double averageResponseTime;
        private Instant lastCallTime;
    }
}
