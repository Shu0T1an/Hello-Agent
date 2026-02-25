package cn.ts.agent.tool.shell;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ShellToolConfig {

    private final int defaultTimeoutSeconds;
    private final int maxTimeoutSeconds;
    private final int maxOutputLines;
    private final int maxOutputBytes;
    private final long idleTtlSeconds;
    private final boolean autoRestartOnTimeout;
    private final List<String> allowedWorkingDirectories;
    private final List<String> blockedCommandPatterns;

    private ShellToolConfig(Builder builder) {
        this.defaultTimeoutSeconds = builder.defaultTimeoutSeconds;
        this.maxTimeoutSeconds = builder.maxTimeoutSeconds;
        this.maxOutputLines = builder.maxOutputLines;
        this.maxOutputBytes = builder.maxOutputBytes;
        this.idleTtlSeconds = builder.idleTtlSeconds;
        this.autoRestartOnTimeout = builder.autoRestartOnTimeout;
        this.allowedWorkingDirectories = Collections.unmodifiableList(new ArrayList<>(builder.allowedWorkingDirectories));
        this.blockedCommandPatterns = Collections.unmodifiableList(new ArrayList<>(builder.blockedCommandPatterns));
    }

    public int defaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    public int maxTimeoutSeconds() {
        return maxTimeoutSeconds;
    }

    public int maxOutputLines() {
        return maxOutputLines;
    }

    public int maxOutputBytes() {
        return maxOutputBytes;
    }

    public long idleTtlSeconds() {
        return idleTtlSeconds;
    }

    public boolean autoRestartOnTimeout() {
        return autoRestartOnTimeout;
    }

    public List<String> allowedWorkingDirectories() {
        return allowedWorkingDirectories;
    }

    public List<String> blockedCommandPatterns() {
        return blockedCommandPatterns;
    }

    public List<Path> normalizedAllowedRoots() {
        return allowedWorkingDirectories.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Path::of)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .toList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int defaultTimeoutSeconds = 30;
        private int maxTimeoutSeconds = 120;
        private int maxOutputLines = 400;
        private int maxOutputBytes = 128 * 1024;
        private long idleTtlSeconds = 900;
        private boolean autoRestartOnTimeout = true;
        private List<String> allowedWorkingDirectories = new ArrayList<>();
        private List<String> blockedCommandPatterns = new ArrayList<>();

        public Builder defaultTimeoutSeconds(int defaultTimeoutSeconds) {
            this.defaultTimeoutSeconds = defaultTimeoutSeconds;
            return this;
        }

        public Builder maxTimeoutSeconds(int maxTimeoutSeconds) {
            this.maxTimeoutSeconds = maxTimeoutSeconds;
            return this;
        }

        public Builder maxOutputLines(int maxOutputLines) {
            this.maxOutputLines = maxOutputLines;
            return this;
        }

        public Builder maxOutputBytes(int maxOutputBytes) {
            this.maxOutputBytes = maxOutputBytes;
            return this;
        }

        public Builder idleTtlSeconds(long idleTtlSeconds) {
            this.idleTtlSeconds = idleTtlSeconds;
            return this;
        }

        public Builder autoRestartOnTimeout(boolean autoRestartOnTimeout) {
            this.autoRestartOnTimeout = autoRestartOnTimeout;
            return this;
        }

        public Builder allowedWorkingDirectories(String... roots) {
            this.allowedWorkingDirectories = roots == null ? new ArrayList<>() : new ArrayList<>(List.of(roots));
            return this;
        }

        public Builder allowedWorkingDirectories(List<String> roots) {
            this.allowedWorkingDirectories = roots == null ? new ArrayList<>() : new ArrayList<>(roots);
            return this;
        }

        public Builder blockedCommandPatterns(List<String> patterns) {
            this.blockedCommandPatterns = patterns == null ? new ArrayList<>() : new ArrayList<>(patterns);
            return this;
        }

        public ShellToolConfig build() {
            return new ShellToolConfig(this);
        }
    }
}

