package cn.ts.graph.record;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 执行记录基础实现
 * <p>
 * 提供通用的字段和默认实现，子类只需添加特定字段
 * </p>
 *
 * @author tianshuo
 */
public abstract class AbstractExecutionRecord implements ExecutionRecord {

    private final NodeType nodeType;
    private final String nodeId;
    private final String startTime;
    private final String endTime;
    private final long duration;
    private final boolean success;
    private final String errorMessage;

    /**
     * 构造函数
     *
     * @param nodeType     节点类型
     * @param nodeId       节点ID
     * @param startTime    开始时间
     * @param endTime      结束时间
     * @param success      是否成功
     * @param errorMessage 错误消息（失败时）
     */
    protected AbstractExecutionRecord(
            NodeType nodeType,
            String nodeId,
            Instant startTime,
            Instant endTime,
            boolean success,
            String errorMessage) {
        this.nodeType = nodeType;
        this.nodeId = nodeId;
        this.startTime = startTime.toString();
        this.endTime = endTime.toString();
        this.duration = Duration.between(startTime, endTime).toMillis();
        this.success = success;
        this.errorMessage = errorMessage;
    }

    @Override
    public NodeType getNodeType() {
        return nodeType;
    }

    @Override
    public String getNodeId() {
        return nodeId;
    }

    @Override
    public String getStartTime() {
        return startTime;
    }

    @Override
    public String getEndTime() {
        return endTime;
    }

    @Override
    public long getDuration() {
        return duration;
    }

    @Override
    public boolean isSuccess() {
        return success;
    }

    @Override
    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("nodeType", nodeType.getValue());
        map.put("nodeId", nodeId);
        map.put("startTime", startTime);
        map.put("endTime", endTime);
        map.put("duration", duration);
        map.put("success", success);
        if (errorMessage != null) {
            map.put("errorMessage", errorMessage);
        }
        return map;
    }
}
