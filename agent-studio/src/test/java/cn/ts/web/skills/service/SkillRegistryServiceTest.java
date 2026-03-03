package cn.ts.web.skills.service;

import cn.ts.web.skills.config.SkillsProperties;
import cn.ts.web.skills.model.SkillDetail;
import cn.ts.web.skills.model.SkillReferenceContent;
import cn.ts.web.skills.model.SkillSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRegistryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void reindexAndReadSkillDetails() throws IOException {
        Path root = tempDir.resolve("skills");
        Path skillDir = root.resolve("brainstorming");
        Path refsDir = skillDir.resolve("references");
        Files.createDirectories(refsDir);

        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: brainstorming
                description: Use this skill for ideation.
                ---
                # Brainstorming
                Use when creating new features.

                ## Steps
                1. Explore
                2. Clarify

                [Reference](references/guide.md)
                """);
        Files.writeString(refsDir.resolve("guide.md"), "reference-content-12345");

        SkillsProperties properties = new SkillsProperties();
        properties.setRoots(List.of(root.toString()));
        properties.setMaxReferenceBytes(8);
        SkillRegistryService service = new SkillRegistryService(properties, new SkillParser());

        SkillRegistryService.ReindexResult result = service.reindex();
        assertEquals(1, result.count());

        List<SkillSummary> list = service.listSkills("brain", 10);
        assertEquals(1, list.size());
        assertEquals("brainstorming", list.get(0).getName());

        SkillDetail detail = service.getSkillDetail(list.get(0).getId());
        assertNotNull(detail);
        assertFalse(detail.getSections().isEmpty());
        assertFalse(detail.getReferences().isEmpty());

        String refId = detail.getReferences().get(0).getRefId();
        SkillReferenceContent content = service.getReferenceContent(detail.getId(), refId);
        assertTrue(content.isTruncated());
        assertEquals(23, content.getSize());
        assertEquals("referenc", content.getContent());
    }
}

