package cn.ts.web.tool.local.fs;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileToolPropertiesTest {

    @Test
    void hasExpectedDefaults() {
        FileToolProperties properties = new FileToolProperties();

        assertTrue(properties.isEnabled());
        assertEquals(2 * 1024 * 1024, properties.getMaxReadBytes());
        assertEquals(2000, properties.getMaxReadLines());
        assertEquals(5000, properties.getMaxSearchResults());
        assertEquals(List.of(Path.of("D:/JavaProject/Hello-Agent")), properties.getReadAllowedRoots());
        assertEquals(List.of(
                Path.of("D:/JavaProject/Hello-Agent/docs"),
                Path.of("D:/JavaProject/Hello-Agent/uploads")
        ), properties.getWriteAllowedRoots());
    }

    @Test
    void supportsSetterOverrides() {
        FileToolProperties properties = new FileToolProperties();
        properties.setEnabled(false);
        properties.setMaxReadBytes(1024);
        properties.setMaxReadLines(20);
        properties.setMaxSearchResults(30);
        properties.setReadAllowedRoots(List.of(Path.of("D:/tmp/read")));
        properties.setWriteAllowedRoots(List.of(Path.of("D:/tmp/write")));

        assertTrue(!properties.isEnabled());
        assertEquals(1024, properties.getMaxReadBytes());
        assertEquals(20, properties.getMaxReadLines());
        assertEquals(30, properties.getMaxSearchResults());
        assertEquals(List.of(Path.of("D:/tmp/read")), properties.getReadAllowedRoots());
        assertEquals(List.of(Path.of("D:/tmp/write")), properties.getWriteAllowedRoots());
    }
}
