package cn.ts.web.checkpoint;

import cn.ts.graph.checkpoint.CheckpointMetadata;
import cn.ts.graph.checkpoint.CheckpointStorage;
import cn.ts.graph.checkpoint.StateSnapshot;
import cn.ts.graph.serialization.TypedStateDeserializer;
import cn.ts.graph.serialization.TypedStateSerializer;
import cn.ts.web.mapper.CheckpointMapper;
import cn.ts.web.entity.CheckpointEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 数据库 Checkpoint 存储实现（重构版）
 * <p>
 * 使用 PostgreSQL 数据库持久化 Checkpoint，支持会话状态恢复。
 * 使用类型化序列化器保持 State 中的泛型类型信息（如 List&lt;Message&gt;）。
 * API 参数从 threadId 改为 sessionId，与 Session 表关联。
 * </p>
 *
 * @author tianshuo
 */
@Component
@ConditionalOnProperty(
        name = "checkpoint.storage.type",
        havingValue = "database",
        matchIfMissing = false
)
public class DatabaseCheckpointStorage implements CheckpointStorage {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCheckpointStorage.class);

    private final CheckpointMapper checkpointMapper;
    private final ObjectMapper objectMapper;
    private final TypedStateSerializer serializer;
    private final TypedStateDeserializer deserializer;

    public DatabaseCheckpointStorage(CheckpointMapper checkpointMapper, ObjectMapper objectMapper) {
        this.checkpointMapper = checkpointMapper;
        this.objectMapper = objectMapper;
        this.serializer = new TypedStateSerializer(objectMapper);
        this.deserializer = new TypedStateDeserializer(objectMapper);
    }

    @Override
    @Transactional
    public String saveCheckpoint(String threadId, StateSnapshot snapshot) {
        // threadId 在新设计中即为 sessionId
        String sessionId = threadId;

        CheckpointEntity entity = new CheckpointEntity();
        entity.setSessionId(sessionId);
        entity.setCheckpointId(snapshot.getCheckpointId());
        entity.setNodeId(snapshot.getNodeId());
        entity.setLastNodeId(snapshot.getLastNodeId());

        // 处理元数据
        CheckpointMetadata metadata = snapshot.getMetadata();
        if (metadata != null) {
            entity.setParentId(metadata.getParentId());
            entity.setSource(metadata.getSource());
            entity.setMetadataFromMap(metadata.getStepInfo());
        } else {
            entity.setSource("manual");
            entity.setMetadataFromMap(new HashMap<>());
        }

        // 使用类型化序列化将 State 转换为 JSON
        try {
            String stateJson = serializer.serializeWithTypeMetadata(snapshot.getState());
            entity.setStateJson(stateJson);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize state for checkpoint {}", snapshot.getCheckpointId(), e);
            throw new RuntimeException("Failed to serialize state", e);
        }

        entity.setIteration(snapshot.getIteration());
        entity.setCreatedAt(snapshot.getTimestamp());

        try {
            int result = checkpointMapper.insert(entity);
            if (result > 0) {
                log.debug("Saved checkpoint {} for session {}", snapshot.getCheckpointId(), sessionId);
                return snapshot.getCheckpointId();
            }
        } catch (Exception e) {
            log.error("Failed to save checkpoint for session {}", sessionId, e);
            throw new RuntimeException("Failed to save checkpoint", e);
        }

        throw new RuntimeException("Failed to save checkpoint: no rows inserted");
    }

    @Override
    public Optional<StateSnapshot> getCheckpoint(String threadId, String checkpointId) {
        // threadId 在新设计中即为 sessionId
        String sessionId = threadId;
        return checkpointMapper.selectBySessionIdAndCheckpointId(sessionId, checkpointId)
                .map(this::toSnapshot);
    }

    @Override
    public Optional<StateSnapshot> getLatestCheckpoint(String threadId) {
        // threadId 在新设计中即为 sessionId
        String sessionId = threadId;
        return checkpointMapper.selectLatestBySessionId(sessionId)
                .map(this::toSnapshot);
    }

    @Override
    public List<StateSnapshot> getCheckpointHistory(String threadId) {
        // threadId 在新设计中即为 sessionId
        String sessionId = threadId;
        List<CheckpointEntity> entities = checkpointMapper.selectHistoryBySessionId(sessionId);
        List<StateSnapshot> snapshots = new ArrayList<>();
        for (CheckpointEntity entity : entities) {
            snapshots.add(toSnapshot(entity));
        }
        return snapshots;
    }

    @Override
    @Transactional
    public void deleteCheckpoint(String threadId, String checkpointId) {
        int result = checkpointMapper.deleteByCheckpointId(checkpointId);
        log.debug("Deleted checkpoint {} for thread {}, result: {}", checkpointId, threadId, result);
    }

    @Override
    @Transactional
    public void deleteThread(String threadId) {
        // threadId 在新设计中即为 sessionId
        String sessionId = threadId;
        int result = checkpointMapper.deleteBySessionId(sessionId);
        log.debug("Deleted all checkpoints for session {}, count: {}", sessionId, result);
    }

    /**
     * 将实体转换为 StateSnapshot
     * <p>
     * 使用类型化反序列化器从 JSON 还原 State，确保泛型类型正确。
     * </p>
     */
    private StateSnapshot toSnapshot(CheckpointEntity entity) {
        CheckpointMetadata metadata = CheckpointMetadata.builder()
                .source(entity.getSource())
                .parentId(entity.getParentId())
                .stepInfo(entity.getMetadataMap())
                .build();

        // 使用类型化反序列化从 JSON 还原 State
        Map<String, Object> state;
        try {
            state = deserializer.deserializeWithTypeMetadata(entity.getStateJson());
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize state for checkpoint {}", entity.getCheckpointId(), e);
            state = new HashMap<>();
        }

        return StateSnapshot.builder()
                .checkpointId(entity.getCheckpointId())
                .threadId(entity.getSessionId())  // 使用 sessionId 作为 threadId
                .nodeId(entity.getNodeId())
                .lastNodeId(entity.getLastNodeId())
                .state(state)
                .metadata(metadata)
                .timestamp(entity.getCreatedAt())
                .iteration(entity.getIteration())
                .build();
    }
}
