package cn.ts.graph.record;

import java.util.Map;

/**
 * Token 使用统计
 *
 * @param promptTokens     输入token数
 * @param completionTokens 输出token数
 * @param totalTokens      总token数
 * @author tianshuo
 */
public record TokenUsage(
        long promptTokens,
        long completionTokens,
        long totalTokens
) {
    /**
     * 转换为Map
     */
    public Map<String, Object> toMap() {
        return Map.of(
                "promptTokens", promptTokens,
                "completionTokens", completionTokens,
                "totalTokens", totalTokens
        );
    }

    /**
     * 从Map创建
     */
    public static TokenUsage fromMap(Map<String, Object> map) {
        return new TokenUsage(
                ((Number) map.getOrDefault("promptTokens", 0)).longValue(),
                ((Number) map.getOrDefault("completionTokens", 0)).longValue(),
                ((Number) map.getOrDefault("totalTokens", 0)).longValue()
        );
    }

    /**
     * 空的Token使用统计
     */
    public static TokenUsage empty() {
        return new TokenUsage(0, 0, 0);
    }
}
