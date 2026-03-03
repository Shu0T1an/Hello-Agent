package cn.ts.web.skills.service;

import cn.ts.web.skills.config.SkillsProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillLifecycleServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void enableSkill_ShouldMoveSkillToActiveAndReindex() throws IOException {
        TestContext ctx = buildContext();
        Path customizedSkill = ctx.customizedRoot.resolve("demo-skill");
        Files.createDirectories(customizedSkill);
        Files.writeString(customizedSkill.resolve("SKILL.md"), skillContent("demo-skill"));
        ctx.registryService.reindex();

        String customizedSuffix = "customized/demo-skill/SKILL.md";
        String skillId = ctx.registryService.findSkillIdBySkillFileSuffix(customizedSuffix).orElseThrow();

        ctx.lifecycleService.enable(skillId);

        assertTrue(Files.exists(ctx.activeRoot.resolve("demo-skill/SKILL.md")));
        assertTrue(ctx.registryService.findSkillIdBySkillFileSuffix("active/demo-skill/SKILL.md").isPresent());
    }

    @Test
    void disableSkill_ShouldRemoveActiveSkillAndReindex() {
        TestContext ctx = buildContext();
        String createdId = ctx.lifecycleService.create("demo-skill", skillContent("demo-skill"), true);

        assertTrue(Files.exists(ctx.activeRoot.resolve("demo-skill/SKILL.md")));
        ctx.lifecycleService.disable(createdId);

        assertFalse(Files.exists(ctx.activeRoot.resolve("demo-skill")));
        assertFalse(ctx.registryService.findSkillIdBySkillFileSuffix("active/demo-skill/SKILL.md").isPresent());
    }

    private TestContext buildContext() {
        Path baseRoot = tempDir.resolve("skills-home");
        SkillsProperties properties = new SkillsProperties();
        properties.setRoots(List.of(baseRoot.toString()));
        properties.setCustomizedRoot("customized");
        properties.setActiveRoot("active");

        SkillRegistryService registryService = new SkillRegistryService(properties, new SkillParser());
        registryService.reindex();
        SkillLifecycleService lifecycleService = new SkillLifecycleService(properties, registryService);

        return new TestContext(
                lifecycleService,
                registryService,
                baseRoot.resolve("customized"),
                baseRoot.resolve("active")
        );
    }

    private String skillContent(String name) {
        return """
                ---
                name: %s
                description: demo
                ---
                # %s
                Use when testing.
                """.formatted(name, name);
    }

    private record TestContext(
            SkillLifecycleService lifecycleService,
            SkillRegistryService registryService,
            Path customizedRoot,
            Path activeRoot
    ) {
    }
}
