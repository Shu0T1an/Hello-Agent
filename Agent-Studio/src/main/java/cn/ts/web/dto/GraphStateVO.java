package cn.ts.web.dto;

import cn.ts.graph.checkpoint.StateSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 图状态视图对象
 * <p>
 * 用于将 StateSnapshot 转换为前端可用的格式
 * </p>
 *
 * @author tianshuo
 */
public class GraphStateVO {

    private String checkpointId;
    private String threadId;
    private String nodeId;
    private int iteration;
    private Instant timestamp;
    private String source;          // auto/manual/error/restore
    private Map<String, Object> stateData;
    private List<MessageVO> messages;
    private Map<String, Object> metadata;

    /**
     * 从 StateSnapshot 转换为 GraphStateVO
     *
     * @param snapshot 状态快照
     * @return GraphStateVO
     */
    public static GraphStateVO from(StateSnapshot snapshot) {
        GraphStateVO vo = new GraphStateVO();
        vo.checkpointId = snapshot.getCheckpointId();
        vo.threadId = snapshot.getThreadId();
        vo.nodeId = snapshot.getNodeId();
        vo.iteration = snapshot.getIteration();
        vo.timestamp = snapshot.getTimestamp();
        vo.source = snapshot.getMetadata() != null ? snapshot.getMetadata().getSource() : "unknown";
        vo.stateData = snapshot.getState();
        vo.metadata = snapshot.getMetadata() != null ? snapshot.getMetadata().getStepInfo() : Map.of();

        // 从 stateData 中提取 messages
        Object messagesObj = snapshot.getState().get("messages");
        if (messagesObj instanceof List<?> messagesList) {
            vo.messages = MessageVO.fromList(messagesList);
        }

        return vo;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public int getIteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Map<String, Object> getStateData() {
        return stateData;
    }

    public void setStateData(Map<String, Object> stateData) {
        this.stateData = stateData;
    }

    public List<MessageVO> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageVO> messages) {
        this.messages = messages;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
