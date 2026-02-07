package cn.ts.graph.record;

import cn.ts.graph.state.State;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 执行记录管理器接口
 * <p>
 * 负责 execution_record 的创建、存储和历史管理
 * </p>
 *
 * @author tianshuo
 */
public interface ExecutionRecordManager {

    /**
     * 创建并保存当前节点执行记录
     * <p>
     * 自动将上一条记录移入 history，并将新记录设为当前记录
     * </p>
     *
     * @param record 执行记录
     * @param state  状态对象
     */
    void saveRecord(ExecutionRecord record, State state);

    /**
     * 获取当前节点执行记录
     *
     * @param state 状态对象
     * @return 当前执行记录的 Optional
     */
    Optional<ExecutionRecord> getCurrentRecord(State state);

    /**
     * 获取执行历史
     *
     * @param state 状态对象
     * @return 执行历史列表（按时间顺序，从旧到新）
     */
    List<ExecutionRecord> getHistory(State state);

    /**
     * 清空执行历史
     *
     * @param state 状态对象
     */
    void clearHistory(State state);

    /**
     * 从 Map 反序列化为 ExecutionRecord
     *
     * @param map 数据 Map
     * @return 执行记录的 Optional
     */
    Optional<ExecutionRecord> fromMap(Map<String, Object> map);

    /**
     * 获取 State 中存储的当前记录（Map格式）
     *
     * @param state 状态对象
     * @return 记录 Map 的 Optional
     */
    default Optional<Map<String, Object>> getCurrentRecordMap(State state) {
        return state.value(RecordKeys.CURRENT_RECORD_KEY);
    }

    /**
     * 获取 State 中存储的历史记录（List格式）
     *
     * @param state 状态对象
     * @return 历史记录 List 的 Optional
     */
    default Optional<List<Map<String, Object>>> getHistoryMaps(State state) {
        return state.value(RecordKeys.HISTORY_KEY);
    }

    /**
     * 记录存储键名常量
     */
    interface RecordKeys {
        String CURRENT_RECORD_KEY = "execution_record";
        String HISTORY_KEY = "execution_history";
    }
}
