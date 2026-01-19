package cn.ts.graph.checkpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存存储实现
 * <p>
 * 使用 ConcurrentHashMap 在内存中存储检查点
 * 适合开发和测试环境，生产环境建议使用数据库持久化
 * 参考 Spring AI Alibaba 的 MemoryCheckpointStorage 设计
 * </p>
 *
 * @author tianshuo
 */
public class MemoryCheckpointStorage implements CheckpointStorage {

    private final ConcurrentHashMap<String, List<StateSnapshot>> storage;
    private final ConcurrentHashMap<String, String> latestCheckpoint;

    /**
     * 创建默认的内存存储
     */
    public MemoryCheckpointStorage() {
        this.storage = new ConcurrentHashMap<>();
        this.latestCheckpoint = new ConcurrentHashMap<>();
    }

    @Override
    public String saveCheckpoint(String threadId, StateSnapshot snapshot) {
        storage.computeIfAbsent(threadId, k -> new ArrayList<>()).add(snapshot);
        latestCheckpoint.put(threadId, snapshot.getCheckpointId());
        return snapshot.getCheckpointId();
    }

    @Override
    public Optional<StateSnapshot> getCheckpoint(String threadId, String checkpointId) {
        List<StateSnapshot> snapshots = storage.get(threadId);
        if (snapshots == null) {
            return Optional.empty();
        }
        return snapshots.stream()
                .filter(s -> s.getCheckpointId().equals(checkpointId))
                .findFirst();
    }

    @Override
    public Optional<StateSnapshot> getLatestCheckpoint(String threadId) {
        String checkpointId = latestCheckpoint.get(threadId);
        if (checkpointId == null) {
            return Optional.empty();
        }
        return getCheckpoint(threadId, checkpointId);
    }

    @Override
    public List<StateSnapshot> getCheckpointHistory(String threadId) {
        List<StateSnapshot> snapshots = storage.get(threadId);
        if (snapshots == null) {
            return List.of();
        }
        return new ArrayList<>(snapshots);
    }

    @Override
    public void deleteCheckpoint(String threadId, String checkpointId) {
        List<StateSnapshot> snapshots = storage.get(threadId);
        if (snapshots != null) {
            snapshots.removeIf(s -> s.getCheckpointId().equals(checkpointId));
            // 如果删除的是最新的检查点，更新最新检查点ID
            String latestId = latestCheckpoint.get(threadId);
            if (checkpointId.equals(latestId)) {
                if (snapshots.isEmpty()) {
                    latestCheckpoint.remove(threadId);
                } else {
                    latestCheckpoint.put(threadId, snapshots.get(snapshots.size() - 1).getCheckpointId());
                }
            }
        }
    }

    @Override
    public void deleteThread(String threadId) {
        storage.remove(threadId);
        latestCheckpoint.remove(threadId);
    }

    /**
     * 获取所有会话ID
     *
     * @return 会话ID列表
     */
    public List<String> getAllThreadIds() {
        return new ArrayList<>(storage.keySet());
    }

    /**
     * 获取存储中的检查点总数
     *
     * @return 检查点总数
     */
    public int getTotalCheckpointCount() {
        return storage.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * 清空所有存储
     */
    public void clear() {
        storage.clear();
        latestCheckpoint.clear();
    }
}
