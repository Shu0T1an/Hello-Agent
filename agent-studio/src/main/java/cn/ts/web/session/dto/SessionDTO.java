package cn.ts.web.session.dto;

import java.time.Instant;
import java.util.List;

/**
 * 会话数据传输对象
 *
 * @author tianshuo
 */
public class SessionDTO {

    private String id;
    private String title;
    private String agentName;
    private Instant createdAt;
    private Instant updatedAt;
    private int messageCount;

    public SessionDTO() {
    }

    public SessionDTO(String id, String title, String agentName, Instant createdAt, Instant updatedAt, int messageCount) {
        this.id = id;
        this.title = title;
        this.agentName = agentName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messageCount = messageCount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }
}
