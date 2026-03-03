package cn.ts.web.skills.service;

import cn.ts.web.skills.config.SkillsProperties;
import cn.ts.web.skills.dto.SkillImportRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillHubImportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void importFromGithub_ShouldRejectNonGithubHost() {
        SkillHubImportService service = buildService();
        SkillImportRequest request = new SkillImportRequest();
        request.setUrl("https://evilgithub.com/demo/repo/archive/refs/heads/main.zip");

        assertThrows(IllegalArgumentException.class, () -> service.importFromGithub(request));
    }

    @Test
    void unzipSecurely_ShouldRejectZipSlipEntry() throws IOException {
        SkillHubImportService service = buildService();
        Path zipFile = tempDir.resolve("attack.zip");
        Path unzipTarget = tempDir.resolve("unzip-target");
        Files.createDirectories(unzipTarget);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("../escape.txt"));
            zos.write("malicious".getBytes());
            zos.closeEntry();
        }

        assertThrows(IllegalArgumentException.class, () -> service.unzipSecurely(zipFile, unzipTarget));
    }

    private SkillHubImportService buildService() {
        SkillsProperties properties = new SkillsProperties();
        properties.setRoots(java.util.List.of(tempDir.toString()));
        return new SkillHubImportService(
                properties,
                Mockito.mock(SkillRegistryService.class),
                Mockito.mock(SkillLifecycleService.class)
        );
    }
}
