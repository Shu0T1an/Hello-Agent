package cn.ts.graph.state.strategy;

/**
 * 替换策略：直接用新值替换旧值
 * <p>
 * 适用于：标量值（数字、字符串、简单对象）
 * 这是默认的状态合并行为
 * </p>
 *
 * @param <T> 值类型
 * @author tianshuo
 */
public final class ReplaceStrategy<T> implements KeyStrategy<T> {

    private static final ReplaceStrategy<?> INSTANCE = new ReplaceStrategy<>();

    /**
     * 获取策略单例
     *
     * @param <T> 值类型
     * @return 替换策略实例
     */
    @SuppressWarnings("unchecked")
    public static <T> ReplaceStrategy<T> getInstance() {
        return (ReplaceStrategy<T>) INSTANCE;
    }

    private ReplaceStrategy() {
        // 私有构造函数，强制使用单例
    }

    @Override
    public T merge(T existingValue, T newValue) {
        return newValue; // 直接返回新值
    }


    @Override
    public String toString() {
        return "ReplaceStrategy";
    }
}
