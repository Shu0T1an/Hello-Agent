package cn.ts.agent.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP 统计信息 DTO
 * <p>
 * 提供整个 MCP 管理器的统计信息摘要
 * </p>
 *
 * @author tianshuo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpStatistics {

    /**
     * 总连接数
     */
    private int totalConnections;

    /**
     * 已连接数量
     */
    private int connectedCount;

    /**
     * 连接中数量
     */
    private int connectingCount;

    /**
     * 错误连接数量
     */
    private int errorCount;

    /**
     * 断开连接数量
     */
    private int disconnectedCount;

    /**
     * 总工具数量
     */
    private int totalTools;

    /**
     * 总调用次数
     */
    private long totalCalls;

    /**
     * 成功调用次数
     */
    private long successfulCalls;

    /**
     * 失败调用次数
     */
    private long failedCalls;

    /**
     * 平均响应时间（毫秒）
     */
    private double averageResponseTime;

    /**
     * 统计时间
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * 各连接的详细统计信息
     */
    @Builder.Default
    private Map<String, ConnectionStatistics> connectionStatistics = new HashMap<>();

    /**
     * 连接统计信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionStatistics {
        /**
         * 连接名称
         */
        private String name;

        /**
         * 连接状态
         */
        private McpConnectionStatus status;

        /**
         * 调用次数
         */
        private long totalCalls;

        /**
         * 成功调用次数
         */
        private long successfulCalls;

        /**
         * 失败调用次数
         */
        private long failedCalls;

        /**
         * 平均响应时间
         */
        private double averageResponseTime;

        /**
         * 工具数量
         */
        private int toolCount;

        /**
         * 最后调用时间
         */
        private Instant lastCallTime;
    }
}
