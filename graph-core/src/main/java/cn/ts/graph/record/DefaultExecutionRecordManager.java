package cn.ts.graph.record;

import cn.ts.graph.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 默认执行记录管理器实现
 * <p>
 * 功能：
 * 1. 自动将当前记录移入历史
 * 2. 限制历史大小防止内存溢出
 * 3. 提供记录查询和清理方法
 * </p>
 *
 * @author tianshuo
 */
public class DefaultExecutionRecordManager implements ExecutionRecordManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultExecutionRecordManager.class);

    private final int maxHistorySize;

    /**
     * 构造函数（使用默认历史大小 100）
     */
    public DefaultExecutionRecordManager() {
        this(1000);
    }

    /**
     * 构造函数
     *
     * @param maxHistorySize 最大历史记录数量
     */
    public DefaultExecutionRecordManager(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
    }

    @Override
    public void saveRecord(ExecutionRecord record, State state) {
        if (record == null || state == null) {
            logger.warn("Cannot save null record or state");
            return;
        }

        // 1. 将当前记录移入历史
        getCurrentRecordMap(state).ifPresent(currentRecord -> {
            addToHistory(currentRecord, state);
        });

        // 2. 保存新记录
        state.update(RecordKeys.CURRENT_RECORD_KEY, record.toMap());

        logger.debug("Saved execution record: nodeId={}, type={}, success={}",
                record.getNodeId(), record.getNodeType(), record.isSuccess());
    }

    /**
     * 将记录添加到历史
     */
    private void addToHistory(Map<String, Object> currentRecord, State state) {
        List<Map<String, Object>> history = getHistoryMaps(state)
                .orElse(new ArrayList<>());

        List<Map<String, Object>> newHistory = new ArrayList<>(history);
        newHistory.add(currentRecord);

        // 限制历史大小
        if (newHistory.size() > maxHistorySize) {
            newHistory = newHistory.subList(
                    newHistory.size() - maxHistorySize,
                    newHistory.size()
            );
        }

        state.update(RecordKeys.HISTORY_KEY, newHistory);
    }

    @Override
    public Optional<ExecutionRecord> getCurrentRecord(State state) {
        return getCurrentRecordMap(state)
                .flatMap(this::fromMap);
    }

    @Override
    public List<ExecutionRecord> getHistory(State state) {
        List<Map<String, Object>> maps = getHistoryMaps(state)
                .orElse(List.of());
        List<ExecutionRecord> result = new ArrayList<>();
        for (Map<String, Object> map : maps) {
            fromMap(map).ifPresent(result::add);
        }
        return result;
    }

    @Override
    public void clearHistory(State state) {
        state.update(RecordKeys.HISTORY_KEY, List.of());
        logger.debug("Cleared execution history");
    }

    @Override
    public Optional<ExecutionRecord> fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Optional.empty();
        }
        return ExecutionRecord.fromMap(map);
    }

    /**
     * 获取最大历史大小
     */
    public int getMaxHistorySize() {
        return maxHistorySize;
    }

    /**
     * 创建默认实例
     */
    public static DefaultExecutionRecordManager create() {
        return new DefaultExecutionRecordManager();
    }

    /**
     * 创建指定历史大小的实例
     */
    public static DefaultExecutionRecordManager create(int maxHistorySize) {
        return new DefaultExecutionRecordManager(maxHistorySize);
    }
}
