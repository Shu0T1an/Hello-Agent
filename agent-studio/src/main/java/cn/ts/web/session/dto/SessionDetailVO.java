package cn.ts.web.session.dto;

import cn.ts.graph.checkpoint.StateSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 会话详情视图对象
 * <p>
 * 用于将 StateSnapshot 转换为会话详情格式返回给前端
 * </p>
 *
 * @author tianshuo
 */
public class SessionDetailVO {

    private String id;
    private String agentName;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;
    private List<MessageVO> messages;
    private int messageCount;
    private String currentCheckpointId;

    /**
     * 从 StateSnapshot 转换为 SessionDetailVO
     *
     * @param snapshot 状态快照
     * @return SessionDetailVO
     */
    public static SessionDetailVO from(StateSnapshot snapshot) {
        SessionDetailVO vo = new SessionDetailVO();
        Map<String, Object> state = snapshot.getState();
        Map<String, Object> stepInfo = snapshot.getMetadata() != null
                ? snapshot.getMetadata().getStepInfo()
                : Map.of();

        vo.id = snapshot.getThreadId();
        vo.agentName = (String) state.get("current_agent");
        vo.title = stepInfo.containsKey("title")
                ? (String) stepInfo.get("title")
                : "新对话";
        vo.createdAt = snapshot.getTimestamp();
        vo.updatedAt = snapshot.getTimestamp();
        vo.currentCheckpointId = snapshot.getCheckpointId();

        // 提取消息
        Object messagesObj = state.get("messages");
        if (messagesObj instanceof List<?> messagesList) {
            vo.messages = MessageVO.fromList(messagesList);
            vo.messageCount = vo.messages.size();
        } else {
            vo.messages = List.of();
            vo.messageCount = 0;
        }

        return vo;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public List<MessageVO> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageVO> messages) {
        this.messages = messages;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public String getCurrentCheckpointId() {
        return currentCheckpointId;
    }

    public void setCurrentCheckpointId(String currentCheckpointId) {
        this.currentCheckpointId = currentCheckpointId;
    }
}
