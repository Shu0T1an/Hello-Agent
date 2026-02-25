package cn.ts.web.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Agent 执行配置属性
 * <p>
 * 使用标准类 + @ConfigurationProperties 实现配置外部化
 * </p>
 *
 * @author tianshuo
 */
@Component
@ConfigurationProperties(prefix = "agent.execution")
public class AgentExecutionConfig {

    private Duration timeout = Duration.ofSeconds(300);
    private Duration heartbeatInterval = Duration.ofSeconds(30);
    private int maxTitleLength = 15;
    private int defaultMaxIterations = 100;
    private boolean debugMode = false;
    private RetryConfig retry = new RetryConfig();
    private ContextPromptConfig contextPrompt = new ContextPromptConfig();

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public int getMaxTitleLength() {
        return maxTitleLength;
    }

    public void setMaxTitleLength(int maxTitleLength) {
        this.maxTitleLength = maxTitleLength;
    }

    public int getDefaultMaxIterations() {
        return defaultMaxIterations;
    }

    public void setDefaultMaxIterations(int defaultMaxIterations) {
        this.defaultMaxIterations = defaultMaxIterations;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public RetryConfig getRetry() {
        return retry;
    }

    public void setRetry(RetryConfig retry) {
        this.retry = retry;
    }

    public ContextPromptConfig getContextPrompt() {
        return contextPrompt;
    }

    public void setContextPrompt(ContextPromptConfig contextPrompt) {
        this.contextPrompt = contextPrompt;
    }

    /**
     * 重试配置类
     */
    public static class RetryConfig {
        private boolean enabled = true;
        private int maxRetries = 3;
        private Duration initialBackoff = Duration.ofSeconds(1);
        private double backoffMultiplier = 2.0;
        private Duration maxBackoff = Duration.ofSeconds(30);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }
    }

    /**
     * Runtime context prompt config.
     */
    public static class ContextPromptConfig {
        private boolean enabled = true;
        private boolean includeTime = true;
        private boolean includeCapabilities = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isIncludeTime() {
            return includeTime;
        }

        public void setIncludeTime(boolean includeTime) {
            this.includeTime = includeTime;
        }

        public boolean isIncludeCapabilities() {
            return includeCapabilities;
        }

        public void setIncludeCapabilities(boolean includeCapabilities) {
            this.includeCapabilities = includeCapabilities;
        }
    }
}
