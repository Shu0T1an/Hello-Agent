package cn.ts.web.memory.service;

import cn.ts.web.memory.config.MemoryProperties;
import cn.ts.web.memory.dto.MemoryDocumentDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class MemoryFileService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryFileService.class);

    private final MemoryProperties properties;

    public MemoryFileService(MemoryProperties properties) {
        this.properties = properties;
    }

    public synchronized MemoryDocumentDTO readDocument() {
        Path path = resolveFilePath();
        if (!Files.isRegularFile(path)) {
            return new MemoryDocumentDTO(
                    path.toString(),
                    false,
                    "",
                    0,
                    properties.getMaxChars()
            );
        }

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return new MemoryDocumentDTO(
                    path.toString(),
                    true,
                    content,
                    content.length(),
                    properties.getMaxChars()
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read memory file: " + path, ex);
        }
    }

    public synchronized MemoryDocumentDTO writeDocument(String content) {
        Path path = resolveFilePath();
        String safeContent = content == null ? "" : content;
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, safeContent, StandardCharsets.UTF_8);
            return new MemoryDocumentDTO(
                    path.toString(),
                    true,
                    safeContent,
                    safeContent.length(),
                    properties.getMaxChars()
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write memory file: " + path, ex);
        }
    }

    private Path resolveFilePath() {
        String configured = properties.getFile();
        if (configured == null || configured.isBlank()) {
            return defaultMemoryPath();
        }

        Path path;
        try {
            path = Path.of(configured);
        } catch (Exception ex) {
            logger.warn("Invalid memory file path '{}', fallback to default memory.md: {}", configured, ex.getMessage());
            return defaultMemoryPath();
        }

        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private Path defaultMemoryPath() {
        return Path.of(System.getProperty("user.dir"))
                .resolve("memory.md")
                .toAbsolutePath()
                .normalize();
    }
}
