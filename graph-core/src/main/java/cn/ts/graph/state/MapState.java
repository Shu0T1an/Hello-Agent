package cn.ts.graph.state;

import cn.ts.graph.state.strategy.AppendStrategy;
import cn.ts.graph.state.strategy.KeyStrategy;
import cn.ts.graph.state.strategy.ReplaceStrategy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Map 的状态实现
 * <p>
 * 使用 ConcurrentHashMap 实现线程安全的状态存储。
 * 本类是线程安全的，可以在多线程环境下安全使用。
 * </p>
 *
 * @author tianshuo
 */
public class MapState implements State {

    private final ConcurrentHashMap<String, Object> data;
    /**
     * 策略注册表，使用 ConcurrentHashMap 保证线程安全
     */
    private final ConcurrentHashMap<String, KeyStrategy<?>> strategies;

    /**
     * 创建一个空的状态
     */
    public MapState() {
        this.data = new ConcurrentHashMap<>();
        this.strategies = new ConcurrentHashMap<>();
    }

    /**
     * 基于初始数据创建状态
     *
     * @param initialData 初始数据
     */
    public MapState(Map<String, Object> initialData) {
        this();
        if (initialData != null) {
            this.data.putAll(initialData);
        }
    }

    @Override
    public <T> Optional<T> value(String key) {
        if (key == null) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        T value = (T) data.get(key);
        return Optional.ofNullable(value);
    }

    @Override
    public State update(String key, Object value) {
        // 如果值为 null，直接删除键
        if (value == null) {
            if (key != null) {
                data.remove(key);
            }
            return this;
        }

        // 检查是否有注册的策略，如果有就使用注册的策略，否则使用默认的 ReplaceStrategy
        KeyStrategy<?> strategy = strategies.get(key);
        if (strategy != null) {
            return update(key, value, strategy);
        }
        return update(key, value, ReplaceStrategy.getInstance());
    }

    @Override
    public State update(String key, Object value, KeyStrategy<?> strategy) {
        if (key != null) {
            Object existingValue = data.get(key);
            // 使用策略合并值
            @SuppressWarnings("unchecked")
            KeyStrategy<Object> objectStrategy = (KeyStrategy<Object>) strategy;
            Object mergedValue = objectStrategy.merge(existingValue, value);
            if (mergedValue == null) {
                data.remove(key);
            } else {
                data.put(key, mergedValue);
            }
        }
        return this;
    }

    @Override
    public State registerKeyStrategy(String key, KeyStrategy<?> strategy) {
        if (key != null && strategy != null) {
            strategies.put(key, strategy);
        }
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public State merge(Map<String, Object> updates) {
        if (updates != null) {
            updates.forEach((key, value) -> {
                KeyStrategy<Object> strategy = (KeyStrategy<Object>) strategies.get(key);
                if (strategy != null) {
                    // 使用注册的策略
                    update(key, value, strategy);
                } else {
                    // 默认使用替换策略
                    if (value == null) {
                        data.remove(key);
                    } else {
                        data.put(key, value);
                    }
                }
            });
        }
        return this;
    }

    @Override
    public Map<String, Object> data() {
        return Collections.unmodifiableMap(new HashMap<>(data));
    }

    @Override
    public boolean containsKey(String key) {
        return key != null && data.containsKey(key);
    }

    @Override
    public Iterable<String> keys() {
        return Collections.unmodifiableSet(data.keySet());
    }

    /**
     * 创建当前状态的副本
     *
     * @return 新的状态对象，包含相同的数据
     */
    @Override
    public MapState copy() {
        MapState copy = new MapState();
        copy.data.putAll(this.data);
        copy.strategies.putAll(this.strategies);
        return copy;
    }

    /**
     * 清空状态中的所有数据
     */
    public void clear() {
        data.clear();
    }

    /**
     * 获取状态中数据的数量
     *
     * @return 数据条目数
     */
    public int size() {
        return data.size();
    }

    @Override
    public String toString() {
        return "MapState{" + data + '}';
    }
}
