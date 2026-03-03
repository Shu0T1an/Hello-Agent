package cn.ts.web.controller;

import cn.ts.web.skills.controller.SkillController;
import cn.ts.web.skills.service.SkillHubImportService;
import cn.ts.web.skills.service.SkillLifecycleService;
import cn.ts.web.skills.service.SkillRegistryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SkillController.class)
class SkillLifecycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SkillRegistryService skillRegistryService;

    @MockBean
    private SkillLifecycleService skillLifecycleService;

    @MockBean
    private SkillHubImportService skillHubImportService;

    @Test
    void enableSkill_ShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/api/skills/skill-1/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(skillLifecycleService).enable("skill-1");
    }

    @Test
    void createSkill_ShouldReturnCreatedSkillId() throws Exception {
        when(skillLifecycleService.create(any(), any(), any())).thenReturn("abc123");

        mockMvc.perform(post("/api/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "demo-skill",
                                "content", "---\nname: demo-skill\n---\n# Demo",
                                "enable", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillId").value("abc123"));
    }
}
