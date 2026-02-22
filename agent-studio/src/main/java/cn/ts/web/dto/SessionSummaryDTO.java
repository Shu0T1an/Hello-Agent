package cn.ts.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * 会话摘要 DTO
 * <p>
 * 包含会话的基础统计信息、工具调用统计和 LLM 调用详情
 * </p>
 *
 * @author tianshuo
 */
public class SessionSummaryDTO {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 基础统计信息
     */
    private BasicStats basicStats;

    /**
     * 工具调用统计列表
     */
    private List<ToolStats> toolStats;

    /**
     * LLM 调用详情列表
     */
    private List<LLMCallStats> llmCalls;

    public SessionSummaryDTO() {
    }

    public SessionSummaryDTO(String sessionId, String title, BasicStats basicStats, List<ToolStats> toolStats, List<LLMCallStats> llmCalls) {
        this.sessionId = sessionId;
        this.title = title;
        this.basicStats = basicStats;
        this.toolStats = toolStats;
        this.llmCalls = llmCalls;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BasicStats getBasicStats() {
        return basicStats;
    }

    public void setBasicStats(BasicStats basicStats) {
        this.basicStats = basicStats;
    }

    public List<ToolStats> getToolStats() {
        return toolStats;
    }

    public void setToolStats(List<ToolStats> toolStats) {
        this.toolStats = toolStats;
    }

    public List<LLMCallStats> getLlmCalls() {
        return llmCalls;
    }

    public void setLlmCalls(List<LLMCallStats> llmCalls) {
        this.llmCalls = llmCalls;
    }

    /**
     * 基础统计信息
     */
    public static class BasicStats {

        /**
         * 总 Token 数
         */
        private Long totalTokens;

        /**
         * 工具调用总次数
         */
        private Integer totalToolCalls;

        /**
         * 执行总时长（毫秒）
         */
        private Long totalDuration;

        /**
         * 总迭代次数
         */
        private Integer totalIterations;

        /**
         * LLM 调用次数
         */
        private Integer llmCallCount;

        /**
         * 会话开始时间
         */
        private Instant startTime;

        /**
         * 会话结束时间
         */
        private Instant endTime;

        public BasicStats() {
        }

        public BasicStats(Long totalTokens, Integer totalToolCalls, Long totalDuration, Integer totalIterations, Integer llmCallCount, Instant startTime, Instant endTime) {
            this.totalTokens = totalTokens;
            this.totalToolCalls = totalToolCalls;
            this.totalDuration = totalDuration;
            this.totalIterations = totalIterations;
            this.llmCallCount = llmCallCount;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public Long getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Long totalTokens) {
            this.totalTokens = totalTokens;
        }

        public Integer getTotalToolCalls() {
            return totalToolCalls;
        }

        public void setTotalToolCalls(Integer totalToolCalls) {
            this.totalToolCalls = totalToolCalls;
        }

        public Long getTotalDuration() {
            return totalDuration;
        }

        public void setTotalDuration(Long totalDuration) {
            this.totalDuration = totalDuration;
        }

        public Integer getTotalIterations() {
            return totalIterations;
        }

        public void setTotalIterations(Integer totalIterations) {
            this.totalIterations = totalIterations;
        }

        public Integer getLlmCallCount() {
            return llmCallCount;
        }

        public void setLlmCallCount(Integer llmCallCount) {
            this.llmCallCount = llmCallCount;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public void setStartTime(Instant startTime) {
            this.startTime = startTime;
        }

        public Instant getEndTime() {
            return endTime;
        }

        public void setEndTime(Instant endTime) {
            this.endTime = endTime;
        }
    }

    /**
     * 工具调用统计
     */
    public static class ToolStats {

        /**
         * 工具名称
         */
        private String toolName;

        /**
         * 调用次数
         */
        private Integer callCount;

        /**
         * 成功次数
         */
        private Integer successCount;

        /**
         * 失败次数
         */
        private Integer failureCount;

        /**
         * 成功率（百分比）
         */
        private Double successRate;

        /**
         * 总耗时（毫秒）
         */
        private Long totalDuration;

        /**
         * 平均耗时（毫秒）
         */
        private Double avgDuration;

        public ToolStats() {
        }

        public ToolStats(String toolName, Integer callCount, Integer successCount, Integer failureCount, Double successRate, Long totalDuration, Double avgDuration) {
            this.toolName = toolName;
            this.callCount = callCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.successRate = successRate;
            this.totalDuration = totalDuration;
            this.avgDuration = avgDuration;
        }

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }

        public Integer getCallCount() {
            return callCount;
        }

        public void setCallCount(Integer callCount) {
            this.callCount = callCount;
        }

        public Integer getSuccessCount() {
            return successCount;
        }

        public void setSuccessCount(Integer successCount) {
            this.successCount = successCount;
        }

        public Integer getFailureCount() {
            return failureCount;
        }

        public void setFailureCount(Integer failureCount) {
            this.failureCount = failureCount;
        }

        public Double getSuccessRate() {
            return successRate;
        }

        public void setSuccessRate(Double successRate) {
            this.successRate = successRate;
        }

        public Long getTotalDuration() {
            return totalDuration;
        }

        public void setTotalDuration(Long totalDuration) {
            this.totalDuration = totalDuration;
        }

        public Double getAvgDuration() {
            return avgDuration;
        }

        public void setAvgDuration(Double avgDuration) {
            this.avgDuration = avgDuration;
        }
    }

    /**
     * LLM 调用详情
     */
    public static class LLMCallStats {

        /**
         * 节点ID
         */
        private String nodeId;

        /**
         * 迭代次数
         */
        private Integer iteration;

        /**
         * 输入 Token 数
         */
        private Long promptTokens;

        /**
         * 输出 Token 数
         */
        private Long completionTokens;

        /**
         * 总 Token 数
         */
        private Long totalTokens;

        /**
         * 耗时（毫秒）
         */
        private Long duration;

        /**
         * 调用时间
         */
        private Instant timestamp;

        /**
         * 触发的工具调用列表
         */
        private List<String> toolCalls;

        public LLMCallStats() {
        }

        public LLMCallStats(String nodeId, Integer iteration, Long promptTokens, Long completionTokens, Long totalTokens, Long duration, Instant timestamp, List<String> toolCalls) {
            this.nodeId = nodeId;
            this.iteration = iteration;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
            this.duration = duration;
            this.timestamp = timestamp;
            this.toolCalls = toolCalls;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public Integer getIteration() {
            return iteration;
        }

        public void setIteration(Integer iteration) {
            this.iteration = iteration;
        }

        public Long getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(Long promptTokens) {
            this.promptTokens = promptTokens;
        }

        public Long getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(Long completionTokens) {
            this.completionTokens = completionTokens;
        }

        public Long getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Long totalTokens) {
            this.totalTokens = totalTokens;
        }

        public Long getDuration() {
            return duration;
        }

        public void setDuration(Long duration) {
            this.duration = duration;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
        }

        public List<String> getToolCalls() {
            return toolCalls;
        }

        public void setToolCalls(List<String> toolCalls) {
            this.toolCalls = toolCalls;
        }
    }
}
