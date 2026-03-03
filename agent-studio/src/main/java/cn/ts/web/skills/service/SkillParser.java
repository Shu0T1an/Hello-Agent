package cn.ts.web.skills.service;

import cn.ts.web.skills.model.SkillReference;
import cn.ts.web.skills.model.SkillSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class SkillParser {

    private static final Logger logger = LoggerFactory.getLogger(SkillParser.class);
    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile("^---\\R(.*?)\\R---\\R?(.*)$", Pattern.DOTALL);
    private static final Pattern SECTION_HEADING_PATTERN = Pattern.compile("(?m)^(#{1,6})\\s+(.+)$");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[[^\\]]*]\\(([^)]+)\\)");
    private static final List<String> CONVENTION_DIRS = List.of("references", "scripts", "assets");

    ParsedSkill parse(Path root, Path skillFile) throws IOException {
        String content = Files.readString(skillFile, StandardCharsets.UTF_8);
        FrontMatterResult frontMatterResult = parseFrontMatter(content);
        Map<String, Object> frontMatter = frontMatterResult.frontMatter();
        String body = frontMatterResult.body();
        Path skillDir = skillFile.getParent();

        String relativeSkillFile = normalizeSeparators(root.relativize(skillFile).toString());
        String id = sha1(root.toString() + ":" + relativeSkillFile);
        String name = readString(frontMatter, "name", skillDir.getFileName().toString());
        String description = readString(frontMatter, "description", firstParagraph(body));
        String triggerSummary = detectTriggerSummary(body, description);
        Instant lastModified = Files.getLastModifiedTime(skillFile).toInstant();

        List<SkillSection> sections = parseSections(body);
        List<SkillReferenceWithPath> references = parseReferences(id, skillDir, body);

        return new ParsedSkill(
                id,
                name,
                description,
                triggerSummary,
                relativeSkillFile,
                skillFile,
                skillDir,
                lastModified,
                frontMatter,
                sections,
                references
        );
    }

    private FrontMatterResult parseFrontMatter(String content) {
        if (content == null || content.isBlank()) {
            return new FrontMatterResult(Map.of(), "");
        }
        Matcher matcher = FRONT_MATTER_PATTERN.matcher(content);
        if (!matcher.find()) {
            return new FrontMatterResult(Map.of(), content);
        }

        String yamlText = matcher.group(1);
        String body = matcher.group(2);
        Map<String, Object> parsed = parseYaml(yamlText);
        return new FrontMatterResult(parsed, body != null ? body : "");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYaml(String yamlText) {
        if (yamlText == null || yamlText.isBlank()) {
            return Map.of();
        }
        try {
            Object loaded = new Yaml().load(yamlText);
            if (loaded instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return result;
            }
        } catch (Exception e) {
            logger.warn("Failed to parse skill YAML front matter: {}", e.getMessage());
        }
        return Map.of();
    }

    private String readString(Map<String, Object> map, String key, String fallback) {
        if (map != null) {
            Object value = map.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        return fallback;
    }

    private String firstParagraph(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String[] chunks = body.split("\\R\\R+");
        for (String chunk : chunks) {
            String text = chunk.replaceAll("\\R", " ").trim();
            if (!text.isBlank() && !text.startsWith("#")) {
                return text;
            }
        }
        return "";
    }

    private String detectTriggerSummary(String body, String fallback) {
        if (body == null || body.isBlank()) {
            return fallback != null ? fallback : "";
        }
        String[] lines = body.split("\\R");
        for (String line : lines) {
            String normalized = line.trim();
            if (normalized.isBlank()) {
                continue;
            }
            String lower = normalized.toLowerCase();
            if (lower.contains("use when") || lower.contains("trigger") || lower.contains("适用") || lower.contains("使用时机")) {
                return normalized;
            }
        }
        return fallback != null ? fallback : "";
    }

    private List<SkillSection> parseSections(String body) {
        String safeBody = body != null ? body : "";
        Matcher matcher = SECTION_HEADING_PATTERN.matcher(safeBody);
        List<HeadingSlice> headings = new ArrayList<>();
        while (matcher.find()) {
            headings.add(new HeadingSlice(matcher.start(), matcher.end(), matcher.group(2).trim()));
        }

        List<SkillSection> sections = new ArrayList<>();
        if (headings.isEmpty()) {
            SkillSection section = new SkillSection();
            section.setHeading("Overview");
            section.setContent(safeBody.trim());
            sections.add(section);
            return sections;
        }

        for (int i = 0; i < headings.size(); i++) {
            HeadingSlice current = headings.get(i);
            int contentStart = current.end();
            int contentEnd = i + 1 < headings.size() ? headings.get(i + 1).start() : safeBody.length();
            SkillSection section = new SkillSection();
            section.setHeading(current.heading());
            section.setContent(safeBody.substring(contentStart, contentEnd).trim());
            sections.add(section);
        }
        return sections;
    }

    private List<SkillReferenceWithPath> parseReferences(String skillId, Path skillDir, String body) {
        Map<String, SkillReferenceWithPath> refs = new LinkedHashMap<>();

        for (String conventionDir : CONVENTION_DIRS) {
            Path dir = skillDir.resolve(conventionDir);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    String relative = normalizeSeparators(skillDir.relativize(path).toString());
                    refs.putIfAbsent(relative, buildReference(skillId, relative, conventionDir.toUpperCase(), path));
                });
            } catch (IOException e) {
                logger.warn("Failed to scan skill references directory {}: {}", dir, e.getMessage());
            }
        }

        if (body != null && !body.isBlank()) {
            Matcher matcher = LINK_PATTERN.matcher(body);
            while (matcher.find()) {
                String rawTarget = matcher.group(1).trim();
                String target = stripTitleAndBrackets(rawTarget);
                if (target.isBlank() || isExternalLink(target)) {
                    continue;
                }
                Path resolved = skillDir.resolve(target).normalize();
                if (!resolved.startsWith(skillDir) || !Files.isRegularFile(resolved)) {
                    continue;
                }
                String relative = normalizeSeparators(skillDir.relativize(resolved).toString());
                refs.putIfAbsent(relative, buildReference(skillId, relative, "LINKED", resolved));
            }
        }

        return new ArrayList<>(refs.values());
    }

    private String stripTitleAndBrackets(String rawTarget) {
        String value = rawTarget;
        if (value.startsWith("<") && value.endsWith(">")) {
            value = value.substring(1, value.length() - 1);
        }
        int firstSpace = value.indexOf(' ');
        if (firstSpace > 0) {
            return value.substring(0, firstSpace).trim();
        }
        return value.trim();
    }

    private boolean isExternalLink(String target) {
        String lower = target.toLowerCase();
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("mailto:")
                || lower.startsWith("#")
                || lower.startsWith("/");
    }

    private SkillReferenceWithPath buildReference(String skillId, String relativePath, String category, Path absolutePath) {
        SkillReference ref = new SkillReference();
        ref.setRefId(sha1(skillId + ":" + relativePath));
        ref.setRelativePath(relativePath);
        ref.setCategory(category);
        try {
            ref.setSize(Files.size(absolutePath));
        } catch (IOException e) {
            ref.setSize(0L);
        }
        return new SkillReferenceWithPath(ref, absolutePath.normalize().toAbsolutePath());
    }

    private String normalizeSeparators(String value) {
        return value.replace('\\', '/');
    }

    static String sha1(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 algorithm is not available", e);
        }
    }

    record ParsedSkill(
            String id,
            String name,
            String description,
            String triggerSummary,
            String relativeSkillFile,
            Path skillFile,
            Path skillDir,
            Instant lastModified,
            Map<String, Object> frontMatter,
            List<SkillSection> sections,
            List<SkillReferenceWithPath> references
    ) {
    }

    record SkillReferenceWithPath(SkillReference reference, Path absolutePath) {
    }

    private record FrontMatterResult(Map<String, Object> frontMatter, String body) {
    }

    private record HeadingSlice(int start, int end, String heading) {
    }
}

