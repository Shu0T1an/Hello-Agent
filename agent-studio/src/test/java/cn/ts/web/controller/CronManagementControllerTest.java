package cn.ts.web.controller;

import cn.ts.web.cron.controller.CronManagementController;
import cn.ts.web.cron.dto.CronJobDTO;
import cn.ts.web.cron.service.CronJobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CronManagementController.class)
class CronManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CronJobService cronJobService;

    @Test
    void createJob_ShouldReturnCreatedJob() throws Exception {
        CronJobDTO dto = new CronJobDTO();
        dto.setId(1L);
        dto.setJobName("daily-summary");
        dto.setCronExpression("0 0 9 * * ?");
        dto.setAgentName("general-purpose");
        dto.setEnabled(true);

        when(cronJobService.create(any(CronJobDTO.class))).thenReturn(dto);

        mockMvc.perform(post("/api/cron/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobName").value("daily-summary"));
    }

    @Test
    void listJobs_ShouldReturnJobList() throws Exception {
        CronJobDTO dto = new CronJobDTO();
        dto.setId(1L);
        dto.setJobName("daily-summary");
        dto.setCronExpression("0 0 9 * * ?");

        when(cronJobService.list()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/cron/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].jobName").value("daily-summary"));
    }
}
