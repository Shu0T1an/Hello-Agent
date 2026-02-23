package cn.ts.web.session.dto;

import java.time.Instant;
import java.util.List;

/**
 * Session-level prompt audit payload.
 */
public class SessionAuditDTO {

    private String sessionId;
    private Long total;
    private Integer limit;
    private List<AuditRecord> records;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public List<AuditRecord> getRecords() {
        return records;
    }

    public void setRecords(List<AuditRecord> records) {
        this.records = records;
    }

    public static class AuditRecord {
        private Long id;
        private String traceId;
        private String sessionId;
        private String executionId;
        private String agentName;
        private String phase;
        private String requestJson;
        private String responseJson;
        private String errorMessage;
        private Instant createdAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getExecutionId() {
            return executionId;
        }

        public void setExecutionId(String executionId) {
            this.executionId = executionId;
        }

        public String getAgentName() {
            return agentName;
        }

        public void setAgentName(String agentName) {
            this.agentName = agentName;
        }

        public String getPhase() {
            return phase;
        }

        public void setPhase(String phase) {
            this.phase = phase;
        }

        public String getRequestJson() {
            return requestJson;
        }

        public void setRequestJson(String requestJson) {
            this.requestJson = requestJson;
        }

        public String getResponseJson() {
            return responseJson;
        }

        public void setResponseJson(String responseJson) {
            this.responseJson = responseJson;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }
    }
}
