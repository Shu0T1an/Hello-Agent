package cn.ts.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 会话详情（包含消息历史）
 *
 * @author tianshuo
 */
public class SessionDetailDTO extends SessionDTO {

    private List<SessionMessage> messages;

    public SessionDetailDTO() {
    }

    public List<SessionMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<SessionMessage> messages) {
        this.messages = messages;
    }

    /**
     * 会话消息
     */
    public static class SessionMessage {
        private String id;
        private String role;      // "user" | "assistant"
        private String content;
        private Instant timestamp;
        private Map<String,Object> metadata;

        public SessionMessage() {
        }

        public SessionMessage(String id, String role, String content, Instant timestamp, Map<String,Object> metadata) {
            this.id = id;
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
            this.metadata = metadata;

        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
        }

        public Map<String,Object> getMetadata() {
            return metadata;
        }
        public void setMetadata(Map<String,Object> metadata) {
            this.metadata = metadata;
        }
    }
}
