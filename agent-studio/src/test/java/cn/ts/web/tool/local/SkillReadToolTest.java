package cn.ts.web.tool.local;

import cn.ts.web.skills.config.SkillsProperties;
import cn.ts.web.skills.model.SkillSummary;
import cn.ts.web.skills.service.SkillRegistryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillReadToolTest {

    @Test
    void listSkillsReturnsDisabledErrorWhenFeatureOff() {
        SkillsProperties properties = new SkillsProperties();
        properties.setEnabled(false);
        SkillRegistryService registryService = mock(SkillRegistryService.class);
        SkillReadTool tool = new SkillReadTool(properties, registryService, new ObjectMapper());

        String result = tool.listSkills(new SkillReadTool.ListSkillsRequest("x", 5));
        assertTrue(result.contains("SKILLS_DISABLED"));
    }

    @Test
    void listSkillsReturnsDataWhenEnabled() {
        SkillsProperties properties = new SkillsProperties();
        properties.setEnabled(true);
        properties.setToolEnabled(true);
        SkillRegistryService registryService = mock(SkillRegistryService.class);

        SkillSummary summary = new SkillSummary();
        summary.setId("id-1");
        summary.setName("brainstorming");
        summary.setDescription("desc");
        when(registryService.listSkills(any(), anyInt())).thenReturn(List.of(summary));

        SkillReadTool tool = new SkillReadTool(properties, registryService, new ObjectMapper());
        String result = tool.listSkills(new SkillReadTool.ListSkillsRequest("brain", 10));

        assertTrue(result.contains("\"status\":\"ok\""));
        assertTrue(result.contains("brainstorming"));
    }
}

