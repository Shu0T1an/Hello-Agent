package cn.ts.web.tool.local.fs;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class PathPolicyService {

    private final FileToolProperties properties;

    public PathPolicyService(FileToolProperties properties) {
        this.properties = properties;
    }

    public Path resolveReadPath(String rawPath) {
        Path path = normalizeInput(rawPath);
        Path resolved = resolveRealPathForRead(path);
        assertAllowed(resolved, normalizeRoots(properties.getReadAllowedRoots()), ToolErrorCodes.PATH_NOT_ALLOWED);
        return resolved;
    }

    public Path resolveWritePath(String rawPath) {
        Path path = normalizeInput(rawPath);
        Path resolved = resolveRealPathForWrite(path);
        assertAllowed(resolved, normalizeRoots(properties.getWriteAllowedRoots()), ToolErrorCodes.PATH_NOT_ALLOWED);
        return resolved;
    }

    public Path resolveSearchBasePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return getDefaultReadRoot();
        }
        Path readPath = resolveReadPath(rawPath);
        if (Files.isDirectory(readPath, LinkOption.NOFOLLOW_LINKS)) {
            return readPath;
        }
        return readPath;
    }

    public Path getDefaultReadRoot() {
        List<Path> roots = normalizeRoots(properties.getReadAllowedRoots());
        if (roots.isEmpty()) {
            throw new FileToolException(ToolErrorCodes.INVALID_ARGUMENT, "Read allowed roots are not configured");
        }
        return roots.get(0);
    }

    private Path normalizeInput(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new FileToolException(ToolErrorCodes.INVALID_ARGUMENT, "file_path is required");
        }
        try {
            return Path.of(rawPath).toAbsolutePath().normalize();
        } catch (Exception ex) {
            throw new FileToolException(ToolErrorCodes.INVALID_ARGUMENT, "Invalid path: " + rawPath, ex);
        }
    }

    private Path resolveRealPathForRead(Path path) {
        try {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileToolException(ToolErrorCodes.FILE_NOT_FOUND, "File not found: " + path);
            }
            return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (FileToolException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new FileToolException(ToolErrorCodes.IO_ERROR, "Failed to resolve read path: " + path, ex);
        }
    }

    private Path resolveRealPathForWrite(Path path) {
        try {
            Path absolute = path.toAbsolutePath().normalize();
            Path current = absolute;
            List<String> missingSegments = new ArrayList<>();

            while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (current.getFileName() != null) {
                    missingSegments.add(current.getFileName().toString());
                }
                current = current.getParent();
            }

            if (current == null) {
                throw new FileToolException(ToolErrorCodes.INVALID_ARGUMENT, "Path parent is required: " + path);
            }

            Path resolved = current.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Collections.reverse(missingSegments);
            for (String segment : missingSegments) {
                resolved = resolved.resolve(segment);
            }
            return resolved.normalize();
        } catch (FileToolException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new FileToolException(ToolErrorCodes.IO_ERROR, "Failed to resolve write path: " + path, ex);
        }
    }

    private List<Path> normalizeRoots(List<Path> roots) {
        return roots.stream()
                .map(root -> root.toAbsolutePath().normalize())
                .toList();
    }

    private void assertAllowed(Path path, List<Path> roots, String errorCode) {
        boolean allowed = roots.stream().anyMatch(path::startsWith);
        if (!allowed) {
            throw new FileToolException(errorCode, "Path is outside allowed roots: " + path);
        }
    }
}
