package cn.ts.graph.state;

import cn.ts.graph.state.strategy.KeyStrategy;

import java.util.Map;
import java.util.Optional;

/**
 * 状态接口
 * <p>
 * 定义在图的节点之间传递的共享状态
 * 参考 Spring AI Alibaba 的 OverAllState 设计
 * </p>
 *
 * @author tianshuo
 */
public interface State {

    /**
     * 获取指定键的值
     *
     * @param key 键
     * @param <T> 值的类型
     * @return 值的 Optional 包装，如果不存在则返回空 Optional
     */
    <T> Optional<T> value(String key);

    /**
     * 获取指定键的值，如果不存在则返回默认值
     *
     * @param key          键
     * @param defaultValue 默认值
     * @param <T>          值的类型
     * @return 值，如果不存在则返回默认值
     */
    default <T> T value(String key, T defaultValue) {
        return this.<T>value(key).orElse(defaultValue);
    }

    /**
     * 更新指定键的值
     *
     * @param key   键
     * @param value 新值
     * @return 更新后的状态对象
     */
    State update(String key, Object value);

    /**
     * 合并多个键值对到当前状态
     *
     * @param updates 要合并的键值对
     * @return 合并后的状态对象
     */
    State merge(Map<String, Object> updates);

    /**
     * 获取所有数据的不可变副本
     *
     * @return 包含所有数据的 Map
     */
    Map<String, Object> data();

    /**
     * 检查是否包含指定键
     *
     * @param key 键
     * @return 如果包含返回 true，否则返回 false
     */
    boolean containsKey(String key);

    /**
     * 获取所有键的集合
     *
     * @return 所有键的集合
     */
    Iterable<String> keys();

    /**
     * 使用指定策略更新键值
     *
     * @param key 键
     * @param value 新值
     * @param strategy 合并策略
     * @return 更新后的状态
     */
    State update(String key, Object value, KeyStrategy<?> strategy);

    /**
     * 注册键的默认合并策略
     *
     * @param key 键
     * @param strategy 策略
     * @return 当前状态
     */
    State registerKeyStrategy(String key, KeyStrategy<?> strategy);

    /**
     * 创建状态的深拷贝
     *
     * @return 状态的副本
     */
    State copy();

    /**
     * 将更新应用到状态（不修改当前状态，返回新状态）
     *
     * @param updates 要应用的更新
     * @return 更新后的新状态
     */
    default State apply(Map<String, Object> updates) {
        return this.copy().merge(updates);
    }
}
