package cn.ts.web.infra.audit.service;

import cn.ts.web.infra.audit.entity.LlmPromptAuditEntity;
import cn.ts.web.infra.audit.mapper.LlmPromptAuditMapper;
import cn.ts.web.session.dto.SessionAuditDTO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Persists model prompt audit records.
 */
@Service
public class LlmPromptAuditService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final LlmPromptAuditMapper mapper;

    public LlmPromptAuditService(LlmPromptAuditMapper mapper) {
        this.mapper = mapper;
    }

    public void save(LlmPromptAuditEntity entity) {
        LlmPromptAuditEntity safe = Objects.requireNonNull(entity, "entity cannot be null");
        if (safe.getCreatedAt() == null) {
            safe.setCreatedAt(Instant.now());
        }
        mapper.insert(safe);
    }

    public SessionAuditDTO listBySessionId(String sessionId, Integer limit) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        int effectiveLimit = normalizeLimit(limit);

        long total = mapper.countBySessionId(normalizedSessionId);
        List<SessionAuditDTO.AuditRecord> records = mapper.selectBySessionId(normalizedSessionId, effectiveLimit)
                .stream()
                .map(this::toRecord)
                .toList();

        SessionAuditDTO result = new SessionAuditDTO();
        result.setSessionId(normalizedSessionId);
        result.setTotal(total);
        result.setLimit(effectiveLimit);
        result.setRecords(records);
        return result;
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be blank");
        }
        return sessionId;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private SessionAuditDTO.AuditRecord toRecord(LlmPromptAuditEntity entity) {
        SessionAuditDTO.AuditRecord record = new SessionAuditDTO.AuditRecord();
        record.setId(entity.getId());
        record.setTraceId(entity.getTraceId());
        record.setSessionId(entity.getSessionId());
        record.setExecutionId(entity.getExecutionId());
        record.setAgentName(entity.getAgentName());
        record.setPhase(entity.getPhase());
        record.setRequestJson(entity.getRequestJson());
        record.setResponseJson(entity.getResponseJson());
        record.setErrorMessage(entity.getErrorMessage());
        record.setCreatedAt(entity.getCreatedAt());
        return record;
    }
}
