package cn.ts.web.workspace.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class WorkspaceArchiveService {

    private static final Set<String> ALLOWED_PREFIXES = Set.of("skills/", "uploads/", "memory.md");

    public byte[] exportWorkspace() {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            addPathIfExists(root.resolve("skills"), root, zos);
            addPathIfExists(root.resolve("uploads"), root, zos);
            addFileIfExists(root.resolve("memory.md"), root, zos);
            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to export workspace", e);
        }
    }

    public int importWorkspace(MultipartFile file, String strategy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Workspace file is required");
        }

        String normalizedStrategy = (strategy == null || strategy.isBlank()) ? "merge" : strategy.trim().toLowerCase();
        if (!normalizedStrategy.equals("merge") && !normalizedStrategy.equals("overwrite")) {
            throw new IllegalArgumentException("Unsupported strategy: " + strategy);
        }

        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        int imported = 0;

        try (InputStream in = file.getInputStream(); ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');
                if (!isAllowedEntry(entryName)) {
                    zis.closeEntry();
                    continue;
                }

                Path target = root.resolve(entryName).normalize();
                if (!target.startsWith(root)) {
                    throw new IllegalArgumentException("Zip slip attempt: " + entryName);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    if (Files.exists(target) && "merge".equals(normalizedStrategy)) {
                        zis.closeEntry();
                        continue;
                    }
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                    imported++;
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to import workspace", e);
        }

        return imported;
    }

    private boolean isAllowedEntry(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return false;
        }
        for (String prefix : ALLOWED_PREFIXES) {
            if (entryName.equals(prefix) || entryName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void addPathIfExists(Path path, Path root, ZipOutputStream zos) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walk(path).forEach(p -> {
            try {
                if (Files.isDirectory(p)) {
                    return;
                }
                String entryName = root.relativize(p).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(p, zos);
                zos.closeEntry();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write zip entry", e);
            }
        });
    }

    private void addFileIfExists(Path file, Path root, ZipOutputStream zos) throws IOException {
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return;
        }
        String entryName = root.relativize(file).toString().replace('\\', '/');
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zos);
        zos.closeEntry();
    }
}
