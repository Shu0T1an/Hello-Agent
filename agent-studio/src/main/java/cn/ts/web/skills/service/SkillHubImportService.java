package cn.ts.web.skills.service;

import cn.ts.web.skills.config.SkillsProperties;
import cn.ts.web.skills.dto.SkillImportRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class SkillHubImportService {

    private final SkillsProperties properties;
    private final SkillRegistryService registryService;
    private final SkillLifecycleService lifecycleService;

    public SkillHubImportService(SkillsProperties properties,
                                 SkillRegistryService registryService,
                                 SkillLifecycleService lifecycleService) {
        this.properties = properties;
        this.registryService = registryService;
        this.lifecycleService = lifecycleService;
    }

    public Map<String, Integer> importFromGithub(SkillImportRequest request) {
        if (request == null || request.getUrl() == null || request.getUrl().isBlank()) {
            throw new IllegalArgumentException("Import url is required");
        }

        URI uri = URI.create(request.getUrl());
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!isAllowedGithubHost(host)) {
            throw new IllegalArgumentException("Only github.com url is supported");
        }

        boolean overwrite = Boolean.TRUE.equals(request.getOverwrite());
        boolean enableAfterImport = Boolean.TRUE.equals(request.getEnableAfterImport());

        Path tempZip = null;
        Path tempDir = null;
        try {
            tempZip = Files.createTempFile("skill-import-", ".zip");
            try (InputStream inputStream = new URL(request.getUrl()).openStream()) {
                Files.copy(inputStream, tempZip, StandardCopyOption.REPLACE_EXISTING);
            }

            tempDir = Files.createTempDirectory("skill-import-unzip-");
            unzipSecurely(tempZip, tempDir);

            List<Path> skillFiles = new ArrayList<>();
            Files.walk(tempDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> "SKILL.md".equals(path.getFileName().toString()))
                    .forEach(skillFiles::add);

            int imported = 0;
            int skipped = 0;
            List<String> importedSkillNames = new ArrayList<>();
            Path customizedRoot = resolveCustomizedRoot();

            for (Path skillFile : skillFiles) {
                Path sourceDir = skillFile.getParent();
                String skillName = sourceDir.getFileName().toString();
                Path targetDir = customizedRoot.resolve(skillName).normalize();
                if (Files.exists(targetDir)) {
                    if (!overwrite) {
                        skipped++;
                        continue;
                    }
                    deleteRecursively(targetDir);
                }

                copyDirectory(sourceDir, targetDir);
                imported++;
                importedSkillNames.add(skillName);
            }

            registryService.reindex();

            int enabled = 0;
            if (enableAfterImport) {
                for (String skillName : importedSkillNames) {
                    Optional<String> skillId = registryService.findSkillIdBySkillFileSuffix(skillName + "/SKILL.md");
                    if (skillId.isPresent()) {
                        lifecycleService.enable(skillId.get());
                        enabled++;
                    }
                }
            }

            Map<String, Integer> result = new HashMap<>();
            result.put("imported", imported);
            result.put("skipped", skipped);
            result.put("enabled", enabled);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to import skill from github", e);
        } finally {
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException ignored) {
                }
            }
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    void unzipSecurely(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outputPath = targetDir.resolve(entry.getName()).normalize();
                if (!outputPath.startsWith(targetDir)) {
                    throw new IllegalArgumentException("Zip slip attempt: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(zis, outputPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private boolean isAllowedGithubHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        return "github.com".equals(host) || "www.github.com".equals(host);
    }

    private void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        Files.walk(sourceDir).forEach(path -> {
            try {
                Path relative = sourceDir.relativize(path);
                Path targetPath = targetDir.resolve(relative).normalize();
                if (Files.isDirectory(path)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to copy directory", e);
            }
        });
    }

    private Path resolveCustomizedRoot() {
        String firstRoot = properties.getRoots().isEmpty() ? "skills" : properties.getRoots().get(0);
        Path base = Path.of(firstRoot);
        if (!base.isAbsolute()) {
            base = Path.of(System.getProperty("user.dir")).resolve(base);
        }
        String customizedRoot = properties.getCustomizedRoot();
        Path path = (customizedRoot == null || customizedRoot.isBlank())
                ? base.resolve("customized")
                : (Path.of(customizedRoot).isAbsolute() ? Path.of(customizedRoot) : base.resolve(customizedRoot));
        Path normalized = path.toAbsolutePath().normalize();
        try {
            Files.createDirectories(normalized);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create customized root", e);
        }
        return normalized;
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to delete path: " + p, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to cleanup temp files", e);
        }
    }
}
