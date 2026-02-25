package cn.ts.web.tool.local.fs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "agent.filetool")
public class FileToolProperties {

    private boolean enabled = true;
    private int maxReadBytes = 2 * 1024 * 1024;
    private int maxReadLines = 2000;
    private int maxSearchResults = 5000;

    private List<Path> readAllowedRoots = new ArrayList<>(List.of(
            Path.of("D:/JavaProject/Hello-Agent")
    ));

    private List<Path> writeAllowedRoots = new ArrayList<>(List.of(
            Path.of("D:/JavaProject/Hello-Agent/docs"),
            Path.of("D:/JavaProject/Hello-Agent/uploads")
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxReadBytes() {
        return maxReadBytes;
    }

    public void setMaxReadBytes(int maxReadBytes) {
        this.maxReadBytes = maxReadBytes;
    }

    public int getMaxReadLines() {
        return maxReadLines;
    }

    public void setMaxReadLines(int maxReadLines) {
        this.maxReadLines = maxReadLines;
    }

    public int getMaxSearchResults() {
        return maxSearchResults;
    }

    public void setMaxSearchResults(int maxSearchResults) {
        this.maxSearchResults = maxSearchResults;
    }

    public List<Path> getReadAllowedRoots() {
        return readAllowedRoots;
    }

    public void setReadAllowedRoots(List<Path> readAllowedRoots) {
        this.readAllowedRoots = readAllowedRoots;
    }

    public List<Path> getWriteAllowedRoots() {
        return writeAllowedRoots;
    }

    public void setWriteAllowedRoots(List<Path> writeAllowedRoots) {
        this.writeAllowedRoots = writeAllowedRoots;
    }
}

