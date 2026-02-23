package cn.ts.web.infra.audit.entity;

import java.io.Serializable;
import java.time.Instant;

/**
 * Audit record for a single model invocation phase.
 */
public class LlmPromptAuditEntity implements Serializable {

    private static final long serialVersionUID = 1L;

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

    public LlmPromptAuditEntity setId(Long id) {
        this.id = id;
        return this;
    }

    public String getTraceId() {
        return traceId;
    }

    public LlmPromptAuditEntity setTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    public String getSessionId() {
        return sessionId;
    }

    public LlmPromptAuditEntity setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public String getExecutionId() {
        return executionId;
    }

    public LlmPromptAuditEntity setExecutionId(String executionId) {
        this.executionId = executionId;
        return this;
    }

    public String getAgentName() {
        return agentName;
    }

    public LlmPromptAuditEntity setAgentName(String agentName) {
        this.agentName = agentName;
        return this;
    }

    public String getPhase() {
        return phase;
    }

    public LlmPromptAuditEntity setPhase(String phase) {
        this.phase = phase;
        return this;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public LlmPromptAuditEntity setRequestJson(String requestJson) {
        this.requestJson = requestJson;
        return this;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public LlmPromptAuditEntity setResponseJson(String responseJson) {
        this.responseJson = responseJson;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LlmPromptAuditEntity setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public LlmPromptAuditEntity setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}

