package cn.ts.web.controller;

import cn.ts.web.skills.controller.SkillController;
import cn.ts.web.skills.dto.SkillImportRequest;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SkillController.class)
class SkillImportControllerTest {

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
    void importFromGithub_ShouldReturnResult() throws Exception {
        when(skillHubImportService.importFromGithub(any(SkillImportRequest.class))).thenReturn(Map.of(
                "imported", 1,
                "enabled", 1
        ));

        mockMvc.perform(post("/api/skills/import/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "url", "https://github.com/demo/repo/archive/refs/heads/main.zip",
                                "overwrite", true,
                                "enableAfterImport", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(1));
    }
}
