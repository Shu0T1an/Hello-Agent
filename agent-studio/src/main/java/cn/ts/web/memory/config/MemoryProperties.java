package cn.ts.web.memory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent.memory")
public class MemoryProperties {

    private boolean enabled = true;
    private String file = "memory.md";
    private int maxChars = 12000;
    private boolean injectHeader = true;
    private boolean failOpen = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public int getMaxChars() {
        return maxChars;
    }

    public void setMaxChars(int maxChars) {
        this.maxChars = maxChars;
    }

    public boolean isInjectHeader() {
        return injectHeader;
    }

    public void setInjectHeader(boolean injectHeader) {
        this.injectHeader = injectHeader;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }
}
