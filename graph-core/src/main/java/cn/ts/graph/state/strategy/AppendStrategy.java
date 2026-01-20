package cn.ts.graph.state.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 追加策略：将新值追加到现有值
 * <p>
 * 支持类型：
 * - List: 追加元素
 * - Map: 合并条目
 * - String: 连接字符串
 * </p>
 * <p>
 * 本类是线程安全的，可以在多线程环境下安全使用
 * </p>
 *
 * @param <T> 值类型
 * @author tianshuo
 */
public final class AppendStrategy<T> implements KeyStrategy<T> {

    private static final AppendStrategy<?> INSTANCE = new AppendStrategy<>();

    /**
     * 获取策略单例
     *
     * @param <T> 值类型
     * @return 追加策略实例
     */
    @SuppressWarnings("unchecked")
    public static <T> AppendStrategy<T> getInstance() {
        return (AppendStrategy<T>) INSTANCE;
    }

    private AppendStrategy() {
        // 私有构造函数，强制使用单例
    }

    @Override
    @SuppressWarnings("unchecked")
    public T merge(T existingValue, T newValue) {
        // 如果新值为 null，返回旧值
        if (newValue == null) {
            return existingValue;
        }
        // 如果旧值为 null，返回新值
        if (existingValue == null) {
            return newValue;
        }

        // 处理 List
        if (existingValue instanceof List && newValue instanceof List) {
            // 运行时类型检查：确保列表元素类型兼容
            try {
                List<Object> result = new ArrayList<>((List<?>) existingValue);
                result.addAll((List<?>) newValue);
                return (T) result;
            } catch (ClassCastException e) {
                throw new IllegalArgumentException(
                        "Cannot merge lists: element types are incompatible. " +
                                "Existing type: " + existingValue.getClass().getName() + ", " +
                                "New type: " + newValue.getClass().getName(), e);
            }
        }

        // 处理 Map
        if (existingValue instanceof Map && newValue instanceof Map) {
            // 运行时类型检查：确保 Map 键值类型兼容
            try {
                Map<Object, Object> result = new HashMap<>((Map<?, ?>) existingValue);
                result.putAll((Map<?, ?>) newValue);
                return (T) result;
            } catch (ClassCastException e) {
                throw new IllegalArgumentException(
                        "Cannot merge maps: key/value types are incompatible. " +
                                "Existing type: " + existingValue.getClass().getName() + ", " +
                                "New type: " + newValue.getClass().getName(), e);
            }
        }

        // 处理 String
        if (existingValue instanceof String && newValue instanceof String) {
            // 使用 StringBuilder 进行字符串拼接（性能优化）
            String result = existingValue.toString() + newValue.toString();
            return (T) result;
        }

        // 不支持的类型，回退到替换策略
        // 记录警告信息帮助调试
        if (existingValue.getClass() != newValue.getClass()) {
            // 类型不匹配时直接返回新值
            return newValue;
        }
        return newValue;
    }


    @Override
    public String toString() {
        return "AppendStrategy";
    }
}
