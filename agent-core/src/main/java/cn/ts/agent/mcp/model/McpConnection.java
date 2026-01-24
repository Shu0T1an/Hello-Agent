package cn.ts.agent.mcp.model;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP 连接包装类
 * <p>
 * 包含 MCP 连接的配置、客户端实例、状态和统计信息
 * </p>
 *
 * @author tianshuo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpConnection {

    /**
     * 连接名称
     */
    private String name;

    /**
     * 连接配置
     */
    private McpConnectionConfig config;

    /**
     * MCP 同步客户端
     */
    private McpSyncClient client;

    /**
     * 连接状态
     */
    @Builder.Default
    private McpConnectionStatus status = McpConnectionStatus.DISCONNECTED;

    /**
     * 连接时间
     */
    private Instant connectedAt;

    /**
     * 错误信息（如果状态为 ERROR）
     */
    private String errorMessage;

    /**
     * 统计信息
     */
    @Builder.Default
    private ConnectionStatistics statistics = new ConnectionStatistics();

    /**
     * 连接统计信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionStatistics {
        /**
         * 总调用次数
         */
        private AtomicLong totalCalls = new AtomicLong(0);

        /**
         * 成功调用次数
         */
        private AtomicLong successfulCalls = new AtomicLong(0);

        /**
         * 失败调用次数
         */
        private AtomicLong failedCalls = new AtomicLong(0);

        /**
         * 最后调用时间
         */
        private Instant lastCallTime;

        /**
         * 总响应时间（毫秒）
         */
        private AtomicLong totalResponseTime = new AtomicLong(0);

        /**
         * 记录成功调用
         */
        public void recordSuccess(long responseTimeMs) {
            totalCalls.incrementAndGet();
            successfulCalls.incrementAndGet();
            totalResponseTime.addAndGet(responseTimeMs);
            lastCallTime = Instant.now();
        }

        /**
         * 记录失败调用
         */
        public void recordFailure() {
            totalCalls.incrementAndGet();
            failedCalls.incrementAndGet();
            lastCallTime = Instant.now();
        }

        /**
         * 获取平均响应时间
         */
        public double getAverageResponseTime() {
            long successCount = successfulCalls.get();
            if (successCount == 0) {
                return 0;
            }
            return (double) totalResponseTime.get() / successCount;
        }

        /**
         * 重置统计信息
         */
        public void reset() {
            totalCalls.set(0);
            successfulCalls.set(0);
            failedCalls.set(0);
            totalResponseTime.set(0);
            lastCallTime = null;
        }
    }
}
