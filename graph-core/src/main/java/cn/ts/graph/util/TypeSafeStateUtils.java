package cn.ts.graph.util;

import cn.ts.graph.state.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 类型安全的状态工具类
 * <p>
 * 提供类型安全的方法来访问 State 中的值，避免使用 @SuppressWarnings("unchecked")
 * </p>
 *
 * @author tianshuo
 */
public final class TypeSafeStateUtils {

    private TypeSafeStateUtils() {
        // 防止实例化
    }

    /**
     * 从 State 中获取 List 类型的值
     *
     * @param state        状态对象
     * @param key          键
     * @param elementType  列表元素类型
     * @param <T>          列表元素类型
     * @return Optional 包装的列表
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<List<T>> getList(State state, String key, Class<T> elementType) {
        return state.value(key)
                .filter(obj -> obj instanceof List<?>)
                .map(obj -> (List<T>) obj);
    }

    /**
     * 从 State 中获取 List 类型的值，如果不存在则返回默认值
     *
     * @param state        状态对象
     * @param key          键
     * @param defaultValue 默认值
     * @param <T>          列表元素类型
     * @return 列表
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> getListOrDefault(State state, String key, List<T> defaultValue) {
        return state.value(key)
                .filter(obj -> obj instanceof List<?>)
                .map(obj -> (List<T>) obj)
                .orElse(defaultValue);
    }

    /**
     * 从 State 中获取 List 类型的值，如果不存在或类型不匹配则返回空列表
     *
     * @param state        状态对象
     * @param key          键
     * @param <T>          列表元素类型
     * @return 列表
     */
    public static <T> List<T> getListOrEmpty(State state, String key) {
        return getListOrDefault(state, key, new ArrayList<>());
    }

    /**
     * 从 Map 中获取 List 类型的值
     *
     * @param map          Map 对象
     * @param key          键
     * @param elementType  列表元素类型
     * @param <T>          列表元素类型
     * @return Optional 包装的列表
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<List<T>> getListFromMap(Map<String, Object> map, String key, Class<T> elementType) {
        Object value = map.get(key);
        if (value instanceof List<?>) {
            return Optional.of((List<T>) value);
        }
        return Optional.empty();
    }

    /**
     * 从 Map 中获取 List 类型的值，如果不存在则返回默认值
     *
     * @param map          Map 对象
     * @param key          键
     * @param defaultValue 默认值
     * @param <T>          列表元素类型
     * @return 列表
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> getListFromMapOrDefault(Map<String, Object> map, String key, List<T> defaultValue) {
        Object value = map.get(key);
        if (value instanceof List<?>) {
            return (List<T>) value;
        }
        return defaultValue;
    }

    /**
     * 从 Map 中获取 List 类型的值，如果不存在或类型不匹配则返回空列表
     *
     * @param map          Map 对象
     * @param key          键
     * @param <T>          列表元素类型
     * @return 列表
     */
    public static <T> List<T> getListFromMapOrEmpty(Map<String, Object> map, String key) {
        return getListFromMapOrDefault(map, key, new ArrayList<>());
    }

    /**
     * 从 State 中获取 Integer 类型的值
     *
     * @param state 状态对象
     * @param key   键
     * @return Optional 包装的整数
     */
    public static Optional<Integer> getInteger(State state, String key) {
        return state.value(key)
                .filter(obj -> obj instanceof Integer || obj instanceof Number)
                .map(obj -> obj instanceof Integer ? (Integer) obj : ((Number) obj).intValue());
    }

    /**
     * 从 State 中获取 Integer 类型的值，如果不存在则返回默认值
     *
     * @param state        状态对象
     * @param key          键
     * @param defaultValue 默认值
     * @return 整数
     */
    public static int getIntegerOrDefault(State state, String key, int defaultValue) {
        return getInteger(state, key).orElse(defaultValue);
    }

    /**
     * 从 State 中获取 String 类型的值
     *
     * @param state 状态对象
     * @param key   键
     * @return Optional 包装的字符串
     */
    public static Optional<String> getString(State state, String key) {
        return state.value(key)
                .filter(obj -> obj instanceof String)
                .map(obj -> (String) obj);
    }

    /**
     * 从 State 中获取 String 类型的值，如果不存在则返回默认值
     *
     * @param state        状态对象
     * @param key          键
     * @param defaultValue 默认值
     * @return 字符串
     */
    public static String getStringOrDefault(State state, String key, String defaultValue) {
        return getString(state, key).orElse(defaultValue);
    }

    /**
     * 从 State 中获取 Map 类型的值
     *
     * @param state       状态对象
     * @param key         键
     * @param keyType     Map 键类型
     * @param valueType   Map 值类型
     * @param <K>         Map 键类型
     * @param <V>         Map 值类型
     * @return Optional 包装的 Map
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Optional<Map<K, V>> getMap(State state, String key, Class<K> keyType, Class<V> valueType) {
        return state.value(key)
                .filter(obj -> obj instanceof Map<?, ?>)
                .map(obj -> (Map<K, V>) obj);
    }

    /**
     * 从 State 中获取 Map 类型的值，如果不存在则返回默认值
     *
     * @param state        状态对象
     * @param key          键
     * @param defaultValue 默认值
     * @param <K>          Map 键类型
     * @param <V>          Map 值类型
     * @return Map
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> getMapOrDefault(State state, String key, Map<K, V> defaultValue) {
        return state.value(key)
                .filter(obj -> obj instanceof Map<?, ?>)
                .map(obj -> (Map<K, V>) obj)
                .orElse(defaultValue);
    }
}
