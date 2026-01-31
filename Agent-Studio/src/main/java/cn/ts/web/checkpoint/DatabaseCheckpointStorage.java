package cn.ts.web.checkpoint;

import cn.ts.graph.checkpoint.CheckpointMetadata;
import cn.ts.graph.checkpoint.CheckpointStorage;
import cn.ts.graph.checkpoint.StateSnapshot;
import cn.ts.web.entity.CheckpointEntity;
import cn.ts.web.mapper.CheckpointMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 数据库 Checkpoint 存储实现
 * <p>
 * 使用 PostgreSQL 数据库持久化 Checkpoint，支持会话状态恢复
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

    public DatabaseCheckpointStorage(CheckpointMapper checkpointMapper, ObjectMapper objectMapper) {
        this.checkpointMapper = checkpointMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public String saveCheckpoint(String threadId, StateSnapshot snapshot) {
        CheckpointEntity entity = new CheckpointEntity();
        entity.setThreadId(threadId);
        entity.setCheckpointId(snapshot.getCheckpointId());
        entity.setNodeId(snapshot.getNodeId());

        // 处理元数据
        CheckpointMetadata metadata = snapshot.getMetadata();
        if (metadata != null) {
            entity.setParentId(metadata.getParentId());
            entity.setSource(metadata.getSource());
            entity.setMetadataFromMap(metadata.getStepInfo());
        } else {
            entity.setSource("manual");
            entity.setMetadataFromMap(new java.util.HashMap<>());
        }

        // 将状态 Map 转换为 JSON 字符串
        entity.setStateFromMap(snapshot.getState());
        entity.setIteration(snapshot.getIteration());
        entity.setIsLatest(true);
        entity.setCreatedAt(snapshot.getTimestamp());

        try {
            int result = checkpointMapper.insert(entity);
            if (result > 0) {
                log.debug("Saved checkpoint {} for thread {}", snapshot.getCheckpointId(), threadId);
                return snapshot.getCheckpointId();
            }
        } catch (Exception e) {
            log.error("Failed to save checkpoint for thread {}", threadId, e);
            throw new RuntimeException("Failed to save checkpoint", e);
        }

        throw new RuntimeException("Failed to save checkpoint: no rows inserted");
    }

    @Override
    public Optional<StateSnapshot> getCheckpoint(String threadId, String checkpointId) {
        return checkpointMapper.selectByThreadIdAndCheckpointId(threadId, checkpointId)
                .map(this::toSnapshot);
    }

    @Override
    public Optional<StateSnapshot> getLatestCheckpoint(String threadId) {
        return checkpointMapper.selectLatestByThreadId(threadId)
                .map(this::toSnapshot);
    }

    @Override
    public List<StateSnapshot> getCheckpointHistory(String threadId) {
        List<CheckpointEntity> entities = checkpointMapper.selectByThreadIdOrderByCreatedAt(threadId);
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
        int result = checkpointMapper.deleteByThreadId(threadId);
        log.debug("Deleted all checkpoints for thread {}, count: {}", threadId, result);
    }

    /**
     * 将实体转换为 StateSnapshot
     */
    private StateSnapshot toSnapshot(CheckpointEntity entity) {
        CheckpointMetadata metadata = CheckpointMetadata.builder()
                .source(entity.getSource())
                .parentId(entity.getParentId())
                .stepInfo(entity.getMetadataMap())
                .build();

        return StateSnapshot.builder()
                .checkpointId(entity.getCheckpointId())
                .threadId(entity.getThreadId())
                .nodeId(entity.getNodeId())
                .state(entity.getStateMap())
                .metadata(metadata)
                .timestamp(entity.getCreatedAt())
                .iteration(entity.getIteration())
                .build();
    }
}
