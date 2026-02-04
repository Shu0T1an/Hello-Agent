package cn.ts.web.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Checkpoint 快照实体类
 * 用于统一会话和状态管理
 */
public class CheckpointEntity implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 会话ID（外键关联 sessions 表）
     */
    private String sessionId;

    /**
     * Checkpoint 唯一标识
     */
    private String checkpointId;

    /**
     * 当前执行的节点ID
     */
    private String nodeId;

    /**
     * 父 Checkpoint ID，支持检查点链
     */
    private String parentId;

    /**
     * 完整状态（JSON 字符串）
     */
    private String stateJson;

    /**
     * 元数据（JSON 字符串）
     */
    private String metadataJson;

    /**
     * Checkpoint 来源：auto/manual/error/restore
     */
    private String source;

    /**
     * 当前迭代次数
     */
    private Integer iteration;

    /**
     * 创建时间
     */
    private Instant createdAt;

    // ==================== Getters and Setters ====================

    public Long getId() {
        return id;
    }

    public CheckpointEntity setId(Long id) {
        this.id = id;
        return this;
    }

    public String getSessionId() {
        return sessionId;
    }

    public CheckpointEntity setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public CheckpointEntity setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
        return this;
    }

    public String getNodeId() {
        return nodeId;
    }

    public CheckpointEntity setNodeId(String nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    public String getParentId() {
        return parentId;
    }

    public CheckpointEntity setParentId(String parentId) {
        this.parentId = parentId;
        return this;
    }

    public String getStateJson() {
        return stateJson;
    }

    public CheckpointEntity setStateJson(String stateJson) {
        this.stateJson = stateJson;
        return this;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public CheckpointEntity setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
        return this;
    }

    public String getSource() {
        return source;
    }

    public CheckpointEntity setSource(String source) {
        this.source = source;
        return this;
    }

    public Integer getIteration() {
        return iteration;
    }

    public CheckpointEntity setIteration(Integer iteration) {
        this.iteration = iteration;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public CheckpointEntity setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    // ==================== JSON Helper Methods ====================

    /**
     * 获取 stateJson 的 Map 形式
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStateMap() {
        if (stateJson == null || stateJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(stateJson, Map.class);
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    /**
     * 从 Map 设置 stateJson
     */
    public void setStateFromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            this.stateJson = "{}";
        } else {
            try {
                this.stateJson = objectMapper.writeValueAsString(map);
            } catch (JsonProcessingException e) {
                this.stateJson = "{}";
            }
        }
    }

    /**
     * 获取 metadataJson 的 Map 形式
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMetadataMap() {
        if (metadataJson == null || metadataJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(metadataJson, Map.class);
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    /**
     * 从 Map 设置 metadataJson
     */
    public void setMetadataFromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            this.metadataJson = "{}";
        } else {
            try {
                this.metadataJson = objectMapper.writeValueAsString(map);
            } catch (JsonProcessingException e) {
                this.metadataJson = "{}";
            }
        }
    }
}
