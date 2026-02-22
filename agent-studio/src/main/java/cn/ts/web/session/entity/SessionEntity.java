package cn.ts.web.session.entity;

import java.io.Serializable;
import java.time.Instant;

/**
 * Session 会话实体类
 * <p>
 * 存储会话级别的元数据，与 Checkpoint 分离
 * </p>
 *
 * @author tianshuo
 */
public class SessionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 会话唯一标识
     */
    private String sessionId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 当前使用的 Agent 名称
     */
    private String currentAgent;

    /**
     * 会话状态：active/deleted
     */
    private String status;

    /**
     * Agent 切换历史（JSON 数组）
     */
    private String agentSwitchHistory;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;

    // ==================== Getters and Setters ====================

    public Long getId() {
        return id;
    }

    public SessionEntity setId(Long id) {
        this.id = id;
        return this;
    }

    public String getSessionId() {
        return sessionId;
    }

    public SessionEntity setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public SessionEntity setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getCurrentAgent() {
        return currentAgent;
    }

    public SessionEntity setCurrentAgent(String currentAgent) {
        this.currentAgent = currentAgent;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public SessionEntity setStatus(String status) {
        this.status = status;
        return this;
    }

    public String getAgentSwitchHistory() {
        return agentSwitchHistory;
    }

    public SessionEntity setAgentSwitchHistory(String agentSwitchHistory) {
        this.agentSwitchHistory = agentSwitchHistory;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public SessionEntity setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public SessionEntity setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}
