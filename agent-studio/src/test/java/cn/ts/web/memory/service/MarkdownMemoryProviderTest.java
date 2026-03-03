package cn.ts.web.memory.service;

import cn.ts.web.memory.config.MemoryProperties;
import cn.ts.web.memory.spi.MemoryPayload;
import cn.ts.web.memory.spi.MemoryRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownMemoryProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsMarkdownMemoryFromFile() throws Exception {
        Path file = tempDir.resolve("memory.md");
        Files.writeString(file, "Always use rg first.");

        MemoryProperties properties = new MemoryProperties();
        properties.setFile(file.toString());
        properties.setMaxChars(1000);
        MarkdownMemoryProvider provider = new MarkdownMemoryProvider(properties);

        Optional<MemoryPayload> payload = provider.load(new MemoryRequest("s1", "e1"));
        assertTrue(payload.isPresent());
        assertEquals("Always use rg first.", payload.get().content());
        assertFalse(payload.get().truncated());
        assertEquals("memory.md", payload.get().source());
    }

    @Test
    void truncatesWhenContentExceedsLimit() throws Exception {
        Path file = tempDir.resolve("memory.md");
        Files.writeString(file, "0123456789");

        MemoryProperties properties = new MemoryProperties();
        properties.setFile(file.toString());
        properties.setMaxChars(5);
        MarkdownMemoryProvider provider = new MarkdownMemoryProvider(properties);

        Optional<MemoryPayload> payload = provider.load(new MemoryRequest("s1", "e1"));
        assertTrue(payload.isPresent());
        assertEquals("01234", payload.get().content());
        assertTrue(payload.get().truncated());
    }

    @Test
    void returnsUpdatedContentAfterFileChanges() throws Exception {
        Path file = tempDir.resolve("memory.md");
        Files.writeString(file, "v1");

        MemoryProperties properties = new MemoryProperties();
        properties.setFile(file.toString());
        properties.setMaxChars(1000);
        MarkdownMemoryProvider provider = new MarkdownMemoryProvider(properties);

        Optional<MemoryPayload> first = provider.load(new MemoryRequest("s1", "e1"));
        assertTrue(first.isPresent());
        assertEquals("v1", first.get().content());

        Files.writeString(file, "v2-updated");
        Optional<MemoryPayload> second = provider.load(new MemoryRequest("s1", "e2"));
        assertTrue(second.isPresent());
        assertEquals("v2-updated", second.get().content());
    }

    @Test
    void returnsEmptyWhenFileMissing() {
        MemoryProperties properties = new MemoryProperties();
        properties.setFile(tempDir.resolve("missing-memory.md").toString());
        properties.setMaxChars(1000);
        MarkdownMemoryProvider provider = new MarkdownMemoryProvider(properties);

        Optional<MemoryPayload> payload = provider.load(new MemoryRequest("s1", "e1"));
        assertTrue(payload.isEmpty());
    }
}
