package cn.ts.web.memory.service;

import cn.ts.web.memory.config.MemoryProperties;
import cn.ts.web.memory.spi.MemoryPayload;
import cn.ts.web.memory.spi.MemoryProvider;
import cn.ts.web.memory.spi.MemoryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

@Component
public class MarkdownMemoryProvider implements MemoryProvider {

    private static final Logger logger = LoggerFactory.getLogger(MarkdownMemoryProvider.class);

    private final MemoryProperties properties;

    private Path cachedPath;
    private long cachedLastModified;
    private long cachedSize;
    private Optional<MemoryPayload> cachedPayload = Optional.empty();

    public MarkdownMemoryProvider(MemoryProperties properties) {
        this.properties = properties;
    }

    @Override
    public String providerName() {
        return "markdown-file";
    }

    @Override
    public synchronized Optional<MemoryPayload> load(MemoryRequest request) {
        Path file = resolveFilePath();
        if (file == null) {
            resetCache();
            return Optional.empty();
        }
        if (!Files.isRegularFile(file)) {
            resetCache();
            return Optional.empty();
        }

        try {
            long modified = Files.getLastModifiedTime(file).toMillis();
            long size = Files.size(file);
            if (isCacheHit(file, modified, size)) {
                return cachedPayload;
            }

            String raw = Files.readString(file, StandardCharsets.UTF_8);
            Optional<MemoryPayload> payload = buildPayload(raw, file);

            cachedPath = file;
            cachedLastModified = modified;
            cachedSize = size;
            cachedPayload = payload;
            return payload;
        } catch (IOException ex) {
            logger.warn("Failed to read memory file {}: {}", file, ex.getMessage());
            resetCache();
            return Optional.empty();
        }
    }

    private boolean isCacheHit(Path path, long modified, long size) {
        return path.equals(cachedPath)
                && modified == cachedLastModified
                && size == cachedSize;
    }

    private Optional<MemoryPayload> buildPayload(String raw, Path file) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        int limit = Math.max(1, properties.getMaxChars());
        boolean truncated = raw.length() > limit;
        String content = truncated ? raw.substring(0, limit) : raw;
        return Optional.of(new MemoryPayload(
                content,
                file.getFileName().toString(),
                truncated,
                Map.of(
                        "provider", providerName(),
                        "path", file.toString()
                )
        ));
    }

    private Path resolveFilePath() {
        String configured = properties.getFile();
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Path path;
        try {
            path = Path.of(configured);
        } catch (Exception ex) {
            logger.warn("Invalid memory file path '{}': {}", configured, ex.getMessage());
            return null;
        }
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private void resetCache() {
        cachedPath = null;
        cachedLastModified = 0L;
        cachedSize = 0L;
        cachedPayload = Optional.empty();
    }
}
