package cn.ts.web.tool.local;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ShellToolCleanupScheduler {

    private final ShellToolProperties properties;
    private final ShellToolRuntime shellToolRuntime;

    public ShellToolCleanupScheduler(ShellToolProperties properties, ShellToolRuntime shellToolRuntime) {
        this.properties = properties;
        this.shellToolRuntime = shellToolRuntime;
    }

    @Scheduled(fixedDelayString = "#{@shellToolProperties.cleanupInterval.toMillis()}")
    public void cleanupIdleSessions() {
        if (!properties.isEnabled()) {
            return;
        }
        shellToolRuntime.sessionManager().cleanupIdleSessions();
    }
}

