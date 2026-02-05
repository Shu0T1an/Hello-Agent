package cn.ts.graph.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Map 构建工具类
 * <p>
 * 提供流式 API 来构建 Map，减少重复代码
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * Map<String, Object> map = MapBuilder.<String, Object>of()
 *     .put("key1", "value1")
 *     .put("key2", 42)
 *     .putIfNotNull("key3", nullValue)
 *     .build();
 * }</pre>
 * </p>
 *
 * @author tianshuo
 */
public final class MapBuilder<K, V> {

    private final Map<K, V> map;

    private MapBuilder() {
        this.map = new HashMap<>();
    }

    private MapBuilder(Map<K, V> initialMap) {
        this.map = new HashMap<>(initialMap);
    }

    private MapBuilder(int initialCapacity) {
        this.map = new HashMap<>(initialCapacity);
    }

    /**
     * 创建空的 MapBuilder
     *
     * @param <K> 键类型
     * @param <V> 值类型
     * @return MapBuilder 实例
     */
    public static <K, V> MapBuilder<K, V> of() {
        return new MapBuilder<>();
    }

    /**
     * 创建带有初始容量的 MapBuilder
     *
     * @param <K>           键类型
     * @param <V>           值类型
     * @param initialCapacity 初始容量
     * @return MapBuilder 实例
     */
    public static <K, V> MapBuilder<K, V> of(int initialCapacity) {
        return new MapBuilder<>(initialCapacity);
    }

    /**
     * 创建带有初始 Map 的 MapBuilder
     *
     * @param <K>   键类型
     * @param <V>   值类型
     * @param initial 初始 Map
     * @return MapBuilder 实例
     */
    public static <K, V> MapBuilder<K, V> of(Map<K, V> initial) {
        return new MapBuilder<>(initial);
    }

    /**
     * 添加键值对
     *
     * @param key   键
     * @param value 值
     * @return this
     */
    public MapBuilder<K, V> put(K key, V value) {
        this.map.put(key, value);
        return this;
    }

    /**
     * 当值不为 null 时添加键值对
     *
     * @param key   键
     * @param value 值（如果为 null 则不添加）
     * @return this
     */
    public MapBuilder<K, V> putIfNotNull(K key, V value) {
        if (value != null) {
            this.map.put(key, value);
        }
        return this;
    }

    /**
     * 当条件为 true 时添加键值对
     *
     * @param key   键
     * @param value 值
     * @param condition 条件
     * @return this
     */
    public MapBuilder<K, V> putIf(K key, V value, boolean condition) {
        if (condition) {
            this.map.put(key, value);
        }
        return this;
    }

    /**
     * 批量添加键值对
     *
     * @param entries 键值对
     * @return this
     */
    @SafeVarargs
    public final MapBuilder<K, V> putAll(Map.Entry<K, V>... entries) {
        for (Map.Entry<K, V> entry : entries) {
            this.map.put(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * 批量添加键值对（使用 BiConsumer）
     *
     * @param consumer 消费者
     * @return this
     */
    public MapBuilder<K, V> apply(BiConsumer<K, V> consumer) {
        map.forEach(consumer);
        return this;
    }

    /**
     * 构建 Map
     *
     * @return 不可变的 Map
     */
    public Map<K, V> build() {
        return Map.copyOf(map);
    }

    /**
     * 构建可变的 Map
     *
     * @return 可变的 HashMap
     */
    public Map<K, V> buildMutable() {
        return new HashMap<>(map);
    }

    /**
     * 创建键值对
     *
     * @param key   键
     * @param value 值
     * @param <K>   键类型
     * @param <V>   值类型
     * @return Map.Entry
     */
    public static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return Map.entry(key, value);
    }
}
