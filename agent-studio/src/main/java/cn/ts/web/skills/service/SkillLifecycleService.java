package cn.ts.web.skills.service;

import cn.ts.web.skills.config.SkillsProperties;
import cn.ts.web.skills.model.SkillDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;

@Service
public class SkillLifecycleService {

    private static final String SKILL_FILE_NAME = "SKILL.md";

    private final SkillsProperties properties;
    private final SkillRegistryService registryService;

    public SkillLifecycleService(SkillsProperties properties, SkillRegistryService registryService) {
        this.properties = properties;
        this.registryService = registryService;
    }

    @Transactional
    public String create(String name, String content, Boolean enableAfterCreate) {
        validateName(name);
        validateContent(content);

        Path customizedRoot = resolveCustomizedRoot();
        Path skillDir = customizedRoot.resolve(name).normalize();
        ensureUnderRoot(skillDir, customizedRoot);

        if (Files.exists(skillDir)) {
            throw new IllegalArgumentException("Skill already exists in customized: " + name);
        }

        try {
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve(SKILL_FILE_NAME), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create skill: " + name, e);
        }

        if (Boolean.TRUE.equals(enableAfterCreate)) {
            copyToActive(skillDir);
        }

        registryService.reindex();
        return registryService.findSkillIdBySkillFileSuffix(name + "/" + SKILL_FILE_NAME)
                .orElseThrow(() -> new IllegalStateException("Created skill cannot be indexed: " + name));
    }

    @Transactional
    public void update(String skillId, String content) {
        validateContent(content);
        Path skillFile = registryService.resolveSkillFileById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));
        try {
            Files.writeString(skillFile, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to update skill: " + skillId, e);
        }
        registryService.reindex();
    }

    @Transactional
    public void delete(String skillId) {
        SkillDetail detail = registryService.getSkillDetail(skillId);
        Path skillFile = registryService.resolveSkillFileById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));

        Path skillDir = skillFile.getParent();
        Path customizedRoot = resolveCustomizedRoot();

        if (!skillDir.startsWith(customizedRoot)) {
            throw new IllegalArgumentException("Only customized skills can be deleted");
        }

        deleteRecursively(skillDir);
        deleteRecursively(resolveActiveRoot().resolve(detail.getName()));
        registryService.reindex();
    }

    @Transactional
    public void enable(String skillId) {
        Path skillFile = registryService.resolveSkillFileById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));
        copyToActive(skillFile.getParent());
        registryService.reindex();
    }

    @Transactional
    public void disable(String skillId) {
        SkillDetail detail = registryService.getSkillDetail(skillId);
        Path activeDir = resolveActiveRoot().resolve(detail.getName()).normalize();
        ensureUnderRoot(activeDir, resolveActiveRoot());
        deleteRecursively(activeDir);
        registryService.reindex();
    }

    private void copyToActive(Path sourceSkillDir) {
        Path activeRoot = resolveActiveRoot();
        Path targetSkillDir = activeRoot.resolve(sourceSkillDir.getFileName().toString()).normalize();
        ensureUnderRoot(targetSkillDir, activeRoot);

        try {
            Files.createDirectories(targetSkillDir);
            Files.walk(sourceSkillDir).forEach(path -> {
                try {
                    Path relative = sourceSkillDir.relativize(path);
                    Path target = targetSkillDir.resolve(relative).normalize();
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to copy skill to active", e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to copy skill to active", e);
        }
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to delete path: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete directory: " + root, e);
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Skill name is required");
        }
        if (!name.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Skill name contains invalid characters");
        }
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Skill content is required");
        }
        if (!content.contains("---") || !content.contains("name:")) {
            throw new IllegalArgumentException("Invalid skill frontmatter: missing 'name:'");
        }
    }

    private Path resolveCustomizedRoot() {
        return resolvePath(properties.getCustomizedRoot(), "customized");
    }

    private Path resolveActiveRoot() {
        return resolvePath(properties.getActiveRoot(), "active");
    }

    private Path resolvePath(String configuredPath, String fallbackChild) {
        Path base = resolveBaseRoot();
        Path resolved;
        if (configuredPath == null || configuredPath.isBlank()) {
            resolved = base.resolve(fallbackChild);
        } else {
            Path path = Path.of(configuredPath);
            resolved = path.isAbsolute() ? path : base.resolve(path);
        }
        Path normalized = resolved.toAbsolutePath().normalize();
        try {
            Files.createDirectories(normalized);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create skills directory: " + normalized, e);
        }
        return normalized;
    }

    private Path resolveBaseRoot() {
        String raw = properties.getRoots().isEmpty() ? "skills" : properties.getRoots().get(0);
        Path path = Path.of(raw);
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private void ensureUnderRoot(Path path, Path root) {
        if (!path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Skill path is outside allowed root: " + path);
        }
    }
}
