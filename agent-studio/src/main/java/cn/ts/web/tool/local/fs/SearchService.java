package cn.ts.web.tool.local.fs;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SearchService {

    private static final Set<String> OUTPUT_MODES = Set.of("content", "files_with_matches", "count");
    private static final Map<String, String> OUTPUT_MODE_ALIASES;
    private static final String OUTPUT_MODE_HINT =
            "Supported output_mode: content(matches,match,lines,text,standard,normal,detailed), files_with_matches(files), count";
    private static final Map<String, String> TYPE_EXTENSION_MAP;

    static {
        Map<String, String> outputAliases = new HashMap<>();
        outputAliases.put("content", "content");
        outputAliases.put("matches", "content");
        outputAliases.put("match", "content");
        outputAliases.put("lines", "content");
        outputAliases.put("text", "content");
        outputAliases.put("standard", "content");
        outputAliases.put("normal", "content");
        outputAliases.put("detailed", "content");
        outputAliases.put("files_with_matches", "files_with_matches");
        outputAliases.put("files", "files_with_matches");
        outputAliases.put("count", "count");
        OUTPUT_MODE_ALIASES = Collections.unmodifiableMap(outputAliases);

        Map<String, String> map = new HashMap<>();
        map.put("java", ".java");
        map.put("ts", ".ts");
        map.put("js", ".js");
        map.put("json", ".json");
        map.put("yaml", ".yaml");
        map.put("yml", ".yml");
        map.put("md", ".md");
        map.put("txt", ".txt");
        map.put("xml", ".xml");
        map.put("html", ".html");
        map.put("css", ".css");
        map.put("go", ".go");
        map.put("py", ".py");
        TYPE_EXTENSION_MAP = Collections.unmodifiableMap(map);
    }

    private final PathPolicyService pathPolicyService;
    private final FileOpsService fileOpsService;
    private final FileToolProperties properties;

    public SearchService(PathPolicyService pathPolicyService, FileOpsService fileOpsService, FileToolProperties properties) {
        this.pathPolicyService = pathPolicyService;
        this.fileOpsService = fileOpsService;
        this.properties = properties;
    }

    public Map<String, Object> glob(String pattern, String path) {
        if (pattern == null || pattern.isBlank()) {
            throw new FileToolException(ToolErrorCodes.INVALID_ARGUMENT, "pattern is required");
        }
        PathMatcher matcher = compileGlobMatcher(pattern);
        Path base = resolveBase(path);
        if (!Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileToolException(ToolErrorCodes.NOT_A_DIRECTORY, "path is not a directory: " + base);
        }

        List<String> results = new ArrayList<>();
        walkFiles(base, file -> {
            Path relative = base.relativize(file);
            if (matcher.matches(relative)) {
                results.add(toRepoRelative(file));
            }
            return results.size() < properties.getMaxSearchResults();
        });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("basePath", toRepoRelative(base));
        data.put("pattern", pattern);
        data.put("total", results.size());
        data.put("paths", results);
        return data;
    }

    public Map<String, Object> grep(
            String pattern,
            String path,
            String glob,
            String outputMode,
            String type,
            Boolean ignoreCase,
            Integer context,
            Integer afterContext,
            Integer beforeContext,
            Boolean multiline
    ) {
        if (pattern == null || pattern.isBlank()) {
            throw new FileToolException(ToolErrorCodes.INVALID_ARGUMENT, "pattern is required");
        }
        String normalizedOutputMode = normalizeOutputMode(outputMode);
        Pattern compiledPattern = compileRegex(pattern, Boolean.TRUE.equals(ignoreCase), Boolean.TRUE.equals(multiline));
        Path base = resolveBase(path);
        PathMatcher globMatcher = glob == null || glob.isBlank() ? null : compileGlobMatcher(glob);
        String typeSuffix = normalizeType(type);
        int before = context != null ? context : (beforeContext == null ? 0 : Math.max(0, beforeContext));
        int after = context != null ? context : (afterContext == null ? 0 : Math.max(0, afterContext));

        List<Path> candidates = collectCandidates(base);

        List<Map<String, Object>> matches = new ArrayList<>();
        Set<String> filesWithMatches = new LinkedHashSet<>();
        Map<String, Integer> countByFile = new LinkedHashMap<>();
        int totalCount = 0;

        for (Path file : candidates) {
            if (!matchesType(file, typeSuffix)) {
                continue;
            }
            if (!matchesGlob(file, base, globMatcher)) {
                continue;
            }

            String text;
            try {
                text = fileOpsService.readUtf8Text(file);
            } catch (FileToolException ex) {
                if (ToolErrorCodes.BINARY_FILE_NOT_SUPPORTED.equals(ex.getErrorCode())) {
                    continue;
                }
                throw ex;
            }

            GrepResult grepResult = Boolean.TRUE.equals(multiline)
                    ? grepMultiline(text, compiledPattern, before, after)
                    : grepPerLine(text, compiledPattern, before, after);

            if (grepResult.matchCount() == 0) {
                continue;
            }

            String relativePath = toRepoRelative(file);
            filesWithMatches.add(relativePath);
            countByFile.put(relativePath, grepResult.matchCount());
            totalCount += grepResult.matchCount();

            if ("content".equals(normalizedOutputMode)) {
                for (Map<String, Object> item : grepResult.items()) {
                    item.put("path", relativePath);
                    matches.add(item);
                    if (matches.size() >= properties.getMaxSearchResults()) {
                        break;
                    }
                }
                if (matches.size() >= properties.getMaxSearchResults()) {
                    break;
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("outputMode", normalizedOutputMode);
        data.put("pattern", pattern);
        data.put("basePath", toRepoRelative(base));
        data.put("totalMatches", totalCount);
        data.put("totalFiles", filesWithMatches.size());
        if ("content".equals(normalizedOutputMode)) {
            data.put("matches", matches);
        } else if ("files_with_matches".equals(normalizedOutputMode)) {
            data.put("files", new ArrayList<>(filesWithMatches));
        } else {
            data.put("counts", countByFile);
        }
        return data;
    }

    private String normalizeOutputMode(String outputMode) {
        String requested = outputMode == null || outputMode.isBlank() ? "content" : outputMode.toLowerCase(Locale.ROOT);
        String mode = OUTPUT_MODE_ALIASES.get(requested);
        if (mode == null || !OUTPUT_MODES.contains(mode)) {
            throw new FileToolException(
                    ToolErrorCodes.UNSUPPORTED_OUTPUT_MODE,
                    "Unsupported output_mode: " + outputMode + ". " + OUTPUT_MODE_HINT
            );
        }
        return mode;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String key = type.toLowerCase(Locale.ROOT);
        String suffix = TYPE_EXTENSION_MAP.get(key);
        if (suffix == null) {
            throw new FileToolException(ToolErrorCodes.UNSUPPORTED_FILE_TYPE, "Unsupported type: " + type);
        }
        return suffix;
    }

    private PathMatcher compileGlobMatcher(String pattern) {
        try {
            return FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        } catch (IllegalArgumentException ex) {
            throw new FileToolException(ToolErrorCodes.INVALID_GLOB_PATTERN, "Invalid glob pattern: " + pattern, ex);
        }
    }

    private Pattern compileRegex(String pattern, boolean ignoreCase, boolean multiline) {
        int flags = 0;
        if (ignoreCase) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        if (multiline) {
            flags |= Pattern.DOTALL;
        }
        try {
            return Pattern.compile(pattern, flags);
        } catch (IllegalArgumentException ex) {
            throw new FileToolException(ToolErrorCodes.INVALID_REGEX, "Invalid regex: " + pattern, ex);
        }
    }

    private Path resolveBase(String path) {
        Path base = pathPolicyService.resolveSearchBasePath(path);
        if (!Files.exists(base, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileToolException(ToolErrorCodes.FILE_NOT_FOUND, "Path not found: " + base);
        }
        return base;
    }

    private List<Path> collectCandidates(Path base) {
        if (Files.isRegularFile(base, LinkOption.NOFOLLOW_LINKS)) {
            return List.of(base);
        }
        if (!Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileToolException(ToolErrorCodes.NOT_A_DIRECTORY, "Path is not a directory: " + base);
        }
        List<Path> files = new ArrayList<>();
        walkFiles(base, file -> {
            files.add(file);
            return files.size() < properties.getMaxSearchResults();
        });
        return files;
    }

    private void walkFiles(Path base, FileConsumer consumer) {
        try {
            Files.walkFileTree(base, new FileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        return consumer.accept(file) ? FileVisitResult.CONTINUE : FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            throw new FileToolException(ToolErrorCodes.IO_ERROR, "Failed to walk files under: " + base, ex);
        }
    }

    private boolean matchesType(Path file, String typeSuffix) {
        return typeSuffix == null || file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(typeSuffix);
    }

    private boolean matchesGlob(Path file, Path base, PathMatcher matcher) {
        if (matcher == null) {
            return true;
        }
        Path relative = Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS) ? base.relativize(file) : file.getFileName();
        return matcher.matches(relative);
    }

    private String toRepoRelative(Path path) {
        Path root = pathPolicyService.getDefaultReadRoot();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (normalizedPath.startsWith(root)) {
            String relative = root.relativize(normalizedPath).toString().replace('\\', '/');
            return relative.isBlank() ? "." : relative;
        }
        return normalizedPath.toString().replace('\\', '/');
    }

    private GrepResult grepPerLine(String text, Pattern pattern, int before, int after) {
        List<String> lines = text.lines().toList();
        List<Map<String, Object>> items = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = pattern.matcher(lines.get(i));
            if (!matcher.find()) {
                continue;
            }
            count++;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("line", i + 1);
            item.put("content", lines.get(i));
            item.put("before", readContext(lines, i - before, i));
            item.put("after", readContext(lines, i + 1, i + 1 + after));
            items.add(item);
        }
        return new GrepResult(count, items);
    }

    private GrepResult grepMultiline(String text, Pattern pattern, int before, int after) {
        List<String> lines = text.lines().toList();
        Matcher matcher = pattern.matcher(text);
        List<Map<String, Object>> items = new ArrayList<>();
        int count = 0;
        while (matcher.find()) {
            count++;
            int lineNumber = countLine(text, matcher.start());
            int idx = Math.max(0, lineNumber - 1);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("line", lineNumber);
            item.put("content", lines.isEmpty() ? "" : lines.get(Math.min(idx, lines.size() - 1)));
            item.put("before", readContext(lines, idx - before, idx));
            item.put("after", readContext(lines, idx + 1, idx + 1 + after));
            items.add(item);
        }
        return new GrepResult(count, items);
    }

    private List<String> readContext(List<String> lines, int startInclusive, int endExclusive) {
        if (lines.isEmpty()) {
            return List.of();
        }
        int start = Math.max(0, startInclusive);
        int end = Math.min(lines.size(), Math.max(start, endExclusive));
        if (start >= end) {
            return List.of();
        }
        return new ArrayList<>(lines.subList(start, end));
    }

    private int countLine(String text, int endOffsetExclusive) {
        int line = 1;
        for (int i = 0; i < Math.max(0, endOffsetExclusive) && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private record GrepResult(int matchCount, List<Map<String, Object>> items) {
    }

    @FunctionalInterface
    private interface FileConsumer {
        boolean accept(Path file);
    }
}
