package cn.ts.graph.state.strategy;

/**
 * 状态键合并策略接口
 * <p>
 * 定义如何合并新旧状态值
 * 参考 Spring AI Alibaba 的 KeyStrategy 设计
 * </p>
 *
 * @param <T> 值类型
 * @author tianshuo
 */
@FunctionalInterface
public interface KeyStrategy<T> {

    /**
     * 合并新旧值
     *
     * @param existingValue 现有值（可能为 null）
     * @param newValue 新值
     * @return 合并后的值
     */
    T merge(T existingValue, T newValue);

    // 改为 default 方法
    default String name() {
        return "DefaultStrategy";
    }
}
