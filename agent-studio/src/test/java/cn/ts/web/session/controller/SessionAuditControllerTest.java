package cn.ts.web.session.controller;

import cn.ts.web.infra.audit.service.LlmPromptAuditService;
import cn.ts.web.session.dto.SessionAuditDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SessionAuditControllerTest {

    @Mock
    private LlmPromptAuditService auditService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SessionAuditController controller = new SessionAuditController(auditService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getSessionAuditsReturnsData() throws Exception {
        SessionAuditDTO.AuditRecord record = new SessionAuditDTO.AuditRecord();
        record.setId(1L);
        record.setTraceId("trace-1");
        record.setPhase("REQUEST");
        record.setCreatedAt(Instant.parse("2026-02-23T10:00:00Z"));

        SessionAuditDTO dto = new SessionAuditDTO();
        dto.setSessionId("session-1");
        dto.setTotal(1L);
        dto.setLimit(200);
        dto.setRecords(List.of(record));

        when(auditService.listBySessionId("session-1", 200)).thenReturn(dto);

        mockMvc.perform(get("/api/sessions/session-1/audits").param("limit", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].traceId").value("trace-1"))
                .andExpect(jsonPath("$.data.records[0].phase").value("REQUEST"));
    }

    @Test
    void getSessionAuditsReturnsBadRequestForInvalidLimit() throws Exception {
        when(auditService.listBySessionId("session-1", 0)).thenThrow(new IllegalArgumentException("limit must be positive"));

        mockMvc.perform(get("/api/sessions/session-1/audits").param("limit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
