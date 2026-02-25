package cn.ts.web.tool.local;

import cn.ts.agent.tool.shell.ShellToolConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "agent.shelltool")
public class ShellToolProperties {

    private boolean enabled = true;
    private int defaultTimeoutSeconds = 30;
    private int maxTimeoutSeconds = 120;
    private int maxOutputLines = 400;
    private int maxOutputBytes = 131072;
    private long idleTtlSeconds = 900;
    private Duration cleanupInterval = Duration.ofSeconds(60);
    private boolean autoRestartOnTimeout = true;
    private List<String> allowedWorkingDirectories = new ArrayList<>();
    private List<String> blockedCommandPatterns = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    public int getMaxTimeoutSeconds() {
        return maxTimeoutSeconds;
    }

    public void setMaxTimeoutSeconds(int maxTimeoutSeconds) {
        this.maxTimeoutSeconds = maxTimeoutSeconds;
    }

    public int getMaxOutputLines() {
        return maxOutputLines;
    }

    public void setMaxOutputLines(int maxOutputLines) {
        this.maxOutputLines = maxOutputLines;
    }

    public int getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(int maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public long getIdleTtlSeconds() {
        return idleTtlSeconds;
    }

    public void setIdleTtlSeconds(long idleTtlSeconds) {
        this.idleTtlSeconds = idleTtlSeconds;
    }

    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }

    public boolean isAutoRestartOnTimeout() {
        return autoRestartOnTimeout;
    }

    public void setAutoRestartOnTimeout(boolean autoRestartOnTimeout) {
        this.autoRestartOnTimeout = autoRestartOnTimeout;
    }

    public List<String> getAllowedWorkingDirectories() {
        return allowedWorkingDirectories;
    }

    public void setAllowedWorkingDirectories(List<String> allowedWorkingDirectories) {
        this.allowedWorkingDirectories = allowedWorkingDirectories;
    }

    public List<String> getBlockedCommandPatterns() {
        return blockedCommandPatterns;
    }

    public void setBlockedCommandPatterns(List<String> blockedCommandPatterns) {
        this.blockedCommandPatterns = blockedCommandPatterns;
    }

    public ShellToolConfig toCoreConfig() {
        return ShellToolConfig.builder()
                .defaultTimeoutSeconds(defaultTimeoutSeconds)
                .maxTimeoutSeconds(maxTimeoutSeconds)
                .maxOutputLines(maxOutputLines)
                .maxOutputBytes(maxOutputBytes)
                .idleTtlSeconds(idleTtlSeconds)
                .autoRestartOnTimeout(autoRestartOnTimeout)
                .allowedWorkingDirectories(allowedWorkingDirectories)
                .blockedCommandPatterns(blockedCommandPatterns)
                .build();
    }
}

