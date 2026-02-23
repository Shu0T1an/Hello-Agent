package cn.ts.web.infra.audit.service;

import cn.ts.web.infra.audit.entity.LlmPromptAuditEntity;
import cn.ts.web.infra.audit.mapper.LlmPromptAuditMapper;
import cn.ts.web.session.dto.SessionAuditDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmPromptAuditServiceTest {

    @Mock
    private LlmPromptAuditMapper mapper;

    @InjectMocks
    private LlmPromptAuditService service;

    private LlmPromptAuditEntity sampleEntity;

    @BeforeEach
    void setUp() {
        sampleEntity = new LlmPromptAuditEntity()
                .setId(1L)
                .setTraceId("trace-1")
                .setSessionId("session-1")
                .setExecutionId("exec-1")
                .setAgentName("general-purpose")
                .setPhase("REQUEST")
                .setRequestJson("{\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}")
                .setCreatedAt(Instant.parse("2026-02-23T10:00:00Z"));
    }

    @Test
    void listBySessionIdUsesDefaultLimitWhenMissing() {
        when(mapper.countBySessionId("session-1")).thenReturn(1L);
        when(mapper.selectBySessionId("session-1", 100)).thenReturn(List.of(sampleEntity));

        SessionAuditDTO result = service.listBySessionId("session-1", null);

        assertEquals("session-1", result.getSessionId());
        assertEquals(1L, result.getTotal());
        assertEquals(100, result.getLimit());
        assertEquals(1, result.getRecords().size());
        verify(mapper).countBySessionId("session-1");
        verify(mapper).selectBySessionId("session-1", 100);
    }

    @Test
    void listBySessionIdCapsLimitAtMaxValue() {
        when(mapper.countBySessionId("session-1")).thenReturn(0L);
        when(mapper.selectBySessionId("session-1", 500)).thenReturn(List.of());

        SessionAuditDTO result = service.listBySessionId("session-1", 999);

        assertEquals(500, result.getLimit());
        assertTrue(result.getRecords().isEmpty());
        verify(mapper).selectBySessionId("session-1", 500);
    }

    @Test
    void listBySessionIdThrowsWhenSessionIdBlank() {
        assertThrows(IllegalArgumentException.class, () -> service.listBySessionId("  ", 50));
    }

    @Test
    void listBySessionIdThrowsWhenLimitInvalid() {
        assertThrows(IllegalArgumentException.class, () -> service.listBySessionId("session-1", 0));
    }

    @Test
    void listBySessionIdMapsEntityFields() {
        when(mapper.countBySessionId("session-1")).thenReturn(1L);
        when(mapper.selectBySessionId("session-1", 10)).thenReturn(List.of(sampleEntity));

        SessionAuditDTO result = service.listBySessionId("session-1", 10);
        SessionAuditDTO.AuditRecord record = result.getRecords().get(0);

        assertEquals("trace-1", record.getTraceId());
        assertEquals("exec-1", record.getExecutionId());
        assertEquals("general-purpose", record.getAgentName());
        assertEquals("REQUEST", record.getPhase());
        assertEquals("{\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}", record.getRequestJson());
        assertEquals(Instant.parse("2026-02-23T10:00:00Z"), record.getCreatedAt());
    }
}
