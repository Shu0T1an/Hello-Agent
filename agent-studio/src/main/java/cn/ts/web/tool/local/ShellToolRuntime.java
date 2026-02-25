package cn.ts.web.tool.local;

import cn.ts.agent.tool.shell.ShellSessionManager;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class ShellToolRuntime {

    private final ShellSessionManager sessionManager;

    public ShellToolRuntime(ShellToolProperties properties) {
        this.sessionManager = new ShellSessionManager(properties.toCoreConfig());
    }

    public ShellSessionManager sessionManager() {
        return sessionManager;
    }

    @PreDestroy
    public void shutdown() {
        sessionManager.closeAll();
    }
}

