package cn.ts.web.controller;

import cn.ts.web.skills.controller.SkillController;
import cn.ts.web.skills.service.SkillHubImportService;
import cn.ts.web.skills.service.SkillLifecycleService;
import cn.ts.web.skills.service.SkillRegistryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SkillController.class)
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillRegistryService skillRegistryService;

    @MockBean
    private SkillLifecycleService skillLifecycleService;

    @MockBean
    private SkillHubImportService skillHubImportService;

    @Test
    void enableSkill_ShouldMoveSkillToActiveAndReindex() throws Exception {
        mockMvc.perform(post("/api/skills/skill-1/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(skillLifecycleService).enable("skill-1");
    }
}
