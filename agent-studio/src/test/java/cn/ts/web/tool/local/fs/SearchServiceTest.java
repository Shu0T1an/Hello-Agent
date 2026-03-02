package cn.ts.web.tool.local.fs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchServiceTest {

    @TempDir
    Path tempDir;

    private FileToolProperties properties;
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        properties = new FileToolProperties();
        properties.setReadAllowedRoots(List.of(tempDir));
        properties.setWriteAllowedRoots(List.of(tempDir));
        properties.setMaxSearchResults(100);

        PathPolicyService pathPolicyService = new PathPolicyService(properties);
        FileOpsService fileOpsService = new FileOpsService(pathPolicyService, properties);
        searchService = new SearchService(pathPolicyService, fileOpsService, properties);
    }

    @Test
    void globMatchesForwardSlashPatternRecursively() throws IOException {
        writeFile("src/main/App.java", "class App {}");
        writeFile("src/main/readme.txt", "notes");

        Map<String, Object> data = searchService.glob("**/*.java", tempDir.toString());

        assertEquals(".", data.get("basePath"));
        assertEquals("**/*.java", data.get("pattern"));
        assertEquals(1, data.get("total"));
        assertEquals(List.of("src/main/App.java"), data.get("paths"));
    }

    @EnabledOnOs(OS.WINDOWS)
    @Test
    void globSupportsSingleBackslashSeparatorsOnWindows() throws IOException {
        writeFile("src/main/App.java", "class App {}");

        Map<String, Object> data = searchService.glob("src\\main\\*.java", tempDir.toString());

        assertEquals(1, data.get("total"));
        assertEquals(List.of("src/main/App.java"), data.get("paths"));
    }

    @Test
    void globRejectsInvalidPattern() {
        FileToolException ex = assertThrows(FileToolException.class, () -> searchService.glob("[", tempDir.toString()));
        assertEquals(ToolErrorCodes.INVALID_GLOB_PATTERN, ex.getErrorCode());
    }

    @Test
    void grepAppliesGlobFilter() throws IOException {
        writeFile("src/main/App.java", "TODO: java");
        writeFile("src/main/notes.txt", "TODO: text");

        Map<String, Object> data = searchService.grep(
                "TODO",
                tempDir.toString(),
                "**/*.java",
                "files_with_matches",
                null,
                false,
                null,
                null,
                null,
                false
        );

        assertEquals("files_with_matches", data.get("outputMode"));
        assertEquals(1, data.get("totalFiles"));
        assertEquals(1, data.get("totalMatches"));
        Object files = data.get("files");
        List<?> list = assertInstanceOf(List.class, files);
        assertEquals(1, list.size());
        assertEquals("src/main/App.java", list.get(0));
    }

    @Test
    void globRespectsMaxSearchResults() throws IOException {
        properties.setMaxSearchResults(1);
        PathPolicyService pathPolicyService = new PathPolicyService(properties);
        FileOpsService fileOpsService = new FileOpsService(pathPolicyService, properties);
        SearchService limitedSearchService = new SearchService(pathPolicyService, fileOpsService, properties);

        writeFile("a/A.java", "class A {}");
        writeFile("b/B.java", "class B {}");

        Map<String, Object> data = limitedSearchService.glob("**/*.java", tempDir.toString());

        assertEquals(1, data.get("total"));
        Object paths = data.get("paths");
        List<?> list = assertInstanceOf(List.class, paths);
        assertEquals(1, list.size());
        assertTrue(list.get(0).toString().endsWith(".java"));
    }

    private void writeFile(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
