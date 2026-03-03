package cn.ts.web.skills.service;

import cn.ts.web.skills.config.SkillsProperties;
import cn.ts.web.skills.model.SkillDetail;
import cn.ts.web.skills.model.SkillReference;
import cn.ts.web.skills.model.SkillReferenceContent;
import cn.ts.web.skills.model.SkillSection;
import cn.ts.web.skills.model.SkillSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class SkillRegistryService {

    private static final Logger logger = LoggerFactory.getLogger(SkillRegistryService.class);
    private static final String SKILL_FILE_NAME = "SKILL.md";

    private final SkillsProperties properties;
    private final SkillParser skillParser;
    private volatile SkillIndex index = SkillIndex.empty();

    public SkillRegistryService(SkillsProperties properties, SkillParser skillParser) {
        this.properties = properties;
        this.skillParser = skillParser;
    }

    public synchronized ReindexResult reindex() {
        if (!properties.isEnabled()) {
            this.index = SkillIndex.empty();
            return new ReindexResult(0, 0);
        }

        List<Path> roots = resolveRoots(properties.getRoots());
        Map<String, SkillDocument> documents = new LinkedHashMap<>();

        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> SKILL_FILE_NAME.equals(path.getFileName().toString()))
                        .forEach(path -> addSkill(root, path, documents));
            } catch (IOException e) {
                logger.warn("Failed to scan skills root {}: {}", root, e.getMessage());
            }
        }

        this.index = new SkillIndex(List.copyOf(roots), Map.copyOf(documents));
        return new ReindexResult(documents.size(), roots.size());
    }

    public List<SkillSummary> listSkills(String query, Integer limit) {
        int cappedLimit = normalizeLimit(limit);
        String keyword = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<SkillSummary> list = index.documents().values().stream()
                .map(SkillDocument::summary)
                .filter(summary -> matches(summary, keyword))
                .sorted(Comparator.comparing(SkillSummary::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(cappedLimit)
                .toList();
        return new ArrayList<>(list);
    }

    public SkillDetail getSkillDetail(String skillId) {
        SkillDocument doc = requireSkill(skillId);
        SkillDetail detail = new SkillDetail();
        detail.setId(doc.summary().getId());
        detail.setName(doc.summary().getName());
        detail.setSkillFile(doc.summary().getSkillFile());
        detail.setFrontMatter(doc.frontMatter());
        detail.setSections(truncateSections(doc.sections(), properties.getMaxDetailChars()));
        detail.setReferences(doc.references().stream().map(SkillParser.SkillReferenceWithPath::reference).toList());
        return detail;
    }

    public SkillReferenceContent getReferenceContent(String skillId, String refId) {
        SkillDocument doc = requireSkill(skillId);
        SkillParser.SkillReferenceWithPath ref = doc.referenceById().get(refId);
        if (ref == null) {
            throw new IllegalArgumentException("Reference not found: " + refId);
        }
        Path file = ref.absolutePath();
        assertPathAllowed(file);

        int maxBytes = Math.max(properties.getMaxReferenceBytes(), 1);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read skill reference: " + file, e);
        }

        boolean truncated = bytes.length > maxBytes;
        int len = Math.min(bytes.length, maxBytes);
        String content = new String(bytes, 0, len, StandardCharsets.UTF_8);

        SkillReferenceContent payload = new SkillReferenceContent();
        payload.setContent(content);
        payload.setTruncated(truncated);
        payload.setSize(bytes.length);
        payload.setContentType(detectContentType(file));
        return payload;
    }

    public int count() {
        return index.documents().size();
    }

    public Optional<Path> resolveSkillFileById(String skillId) {
        SkillDocument doc = index.documents().get(skillId);
        if (doc == null) {
            return Optional.empty();
        }
        Path skillFile = doc.root().resolve(doc.summary().getSkillFile()).normalize().toAbsolutePath();
        return Optional.of(skillFile);
    }

    public Optional<String> findSkillIdBySkillFileSuffix(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return Optional.empty();
        }
        String normalized = suffix.replace('\\', '/');
        return index.documents().values().stream()
                .filter(doc -> doc.summary().getSkillFile() != null)
                .filter(doc -> doc.summary().getSkillFile().endsWith(normalized))
                .map(doc -> doc.summary().getId())
                .findFirst();
    }

    private void addSkill(Path root, Path skillFile, Map<String, SkillDocument> docs) {
        try {
            SkillParser.ParsedSkill parsed = skillParser.parse(root, skillFile);
            SkillSummary summary = new SkillSummary();
            summary.setId(parsed.id());
            summary.setName(parsed.name());
            summary.setDescription(parsed.description());
            summary.setTriggerSummary(parsed.triggerSummary());
            summary.setSkillFile(parsed.relativeSkillFile());
            summary.setLastModified(parsed.lastModified());

            Map<String, SkillParser.SkillReferenceWithPath> byRefId = new LinkedHashMap<>();
            for (SkillParser.SkillReferenceWithPath ref : parsed.references()) {
                byRefId.put(ref.reference().getRefId(), ref);
            }

            SkillDocument doc = new SkillDocument(
                    root,
                    summary,
                    parsed.frontMatter(),
                    parsed.sections(),
                    parsed.references(),
                    byRefId
            );
            docs.put(summary.getId(), doc);
        } catch (Exception e) {
            logger.warn("Failed to parse skill file {}: {}", skillFile, e.getMessage());
        }
    }

    private List<SkillSection> truncateSections(List<SkillSection> sections, int maxChars) {
        int limit = Math.max(maxChars, 1024);
        List<SkillSection> result = new ArrayList<>(sections.size());
        for (SkillSection section : sections) {
            SkillSection item = new SkillSection();
            item.setHeading(section.getHeading());
            String content = section.getContent() != null ? section.getContent() : "";
            item.setContent(content.length() > limit ? content.substring(0, limit) : content);
            result.add(item);
        }
        return result;
    }

    private boolean matches(SkillSummary summary, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String haystack = (safe(summary.getName()) + "\n"
                + safe(summary.getDescription()) + "\n"
                + safe(summary.getTriggerSummary())).toLowerCase(Locale.ROOT);
        return haystack.contains(keyword);
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private int normalizeLimit(Integer requested) {
        int max = Math.max(properties.getMaxListItems(), 1);
        if (requested == null || requested <= 0) {
            return Math.min(100, max);
        }
        return Math.min(requested, max);
    }

    private SkillDocument requireSkill(String skillId) {
        SkillDocument doc = index.documents().get(skillId);
        if (doc == null) {
            throw new IllegalArgumentException("Skill not found: " + skillId);
        }
        return doc;
    }

    private void assertPathAllowed(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        boolean allowed = index.roots().stream().anyMatch(normalized::startsWith);
        if (!allowed) {
            throw new IllegalArgumentException("Skill reference path is outside allowed roots: " + normalized);
        }
    }

    private List<Path> resolveRoots(List<String> rawRoots) {
        List<String> effective = (rawRoots == null || rawRoots.isEmpty()) ? List.of("skills") : rawRoots;
        List<Path> roots = new ArrayList<>();
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

        for (String rawRoot : effective) {
            if (rawRoot == null || rawRoot.isBlank()) {
                continue;
            }
            Path rootPath = Path.of(rawRoot);
            if (!rootPath.isAbsolute()) {
                rootPath = cwd.resolve(rootPath);
            }
            roots.add(rootPath.toAbsolutePath().normalize());
        }
        return roots;
    }

    private String detectContentType(Path path) {
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".java") || lower.endsWith(".ts")
                || lower.endsWith(".js") || lower.endsWith(".json") || lower.endsWith(".yaml")
                || lower.endsWith(".yml") || lower.endsWith(".xml")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    public record ReindexResult(int count, int roots) {
    }

    private record SkillIndex(List<Path> roots, Map<String, SkillDocument> documents) {
        private static SkillIndex empty() {
            return new SkillIndex(List.of(), Map.of());
        }
    }

    private record SkillDocument(
            Path root,
            SkillSummary summary,
            Map<String, Object> frontMatter,
            List<SkillSection> sections,
            List<SkillParser.SkillReferenceWithPath> references,
            Map<String, SkillParser.SkillReferenceWithPath> referenceById
    ) {
    }
}
