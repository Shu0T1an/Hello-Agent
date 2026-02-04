package cn.ts.agent.retry;

import lombok.Data;

import java.time.Duration;

/**
 * 重试配置类
 * 用于配置 LLM 调用失败时的重试策略
 */
@Data
public class RetryConfig {
    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 初始退避时间
     */
    private Duration initialBackoff = Duration.ofSeconds(1);

    /**
     * 退避时间倍数（指数退避）
     */
    private double backoffMultiplier = 2.0;

    /**
     * 最大退避时间
     */
    private Duration maxBackoff = Duration.ofSeconds(30);

    /**
     * 默认配置实例
     */
    public static RetryConfig getDefault() {
        return new RetryConfig();
    }

    /**
     * 创建自定义配置
     */
    public static RetryConfig of(int maxRetries, Duration initialBackoff, double multiplier, Duration maxBackoff) {
        RetryConfig config = new RetryConfig();
        config.setMaxRetries(maxRetries);
        config.setInitialBackoff(initialBackoff);
        config.setBackoffMultiplier(multiplier);
        config.setMaxBackoff(maxBackoff);
        return config;
    }
}
