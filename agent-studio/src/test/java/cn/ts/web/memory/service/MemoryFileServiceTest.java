package cn.ts.web.memory.service;

import cn.ts.web.memory.config.MemoryProperties;
import cn.ts.web.memory.dto.MemoryDocumentDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryFileServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void readReturnsEmptyDocumentWhenFileDoesNotExist() {
        MemoryProperties properties = new MemoryProperties();
        properties.setFile(tempDir.resolve("memory.md").toString());
        properties.setMaxChars(2048);

        MemoryFileService service = new MemoryFileService(properties);
        MemoryDocumentDTO doc = service.readDocument();

        assertFalse(doc.exists());
        assertEquals("", doc.content());
        assertEquals(0, doc.length());
        assertEquals(2048, doc.maxChars());
    }

    @Test
    void writeCreatesFileAndReadReturnsContent() throws Exception {
        MemoryProperties properties = new MemoryProperties();
        Path file = tempDir.resolve("nested").resolve("memory.md");
        properties.setFile(file.toString());
        properties.setMaxChars(9999);

        MemoryFileService service = new MemoryFileService(properties);
        MemoryDocumentDTO written = service.writeDocument("Keep responses concise.");

        assertTrue(written.exists());
        assertEquals("Keep responses concise.", written.content());
        assertTrue(Files.exists(file));

        MemoryDocumentDTO read = service.readDocument();
        assertTrue(read.exists());
        assertEquals("Keep responses concise.", read.content());
        assertEquals("Keep responses concise.".length(), read.length());
    }

    @Test
    void readFallsBackWhenConfiguredPathIsInvalid() {
        MemoryProperties properties = new MemoryProperties();
        properties.setFile("${MEMORY_FILE:${user.dir}/memory.md}");
        properties.setMaxChars(12000);

        MemoryFileService service = new MemoryFileService(properties);
        MemoryDocumentDTO doc = service.readDocument();

        assertTrue(doc.filePath().endsWith("memory.md"));
        assertEquals(12000, doc.maxChars());
    }
}
