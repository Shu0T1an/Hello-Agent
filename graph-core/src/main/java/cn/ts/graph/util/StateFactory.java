package cn.ts.graph.util;

import cn.ts.graph.state.State;

import java.util.Map;

/**
 * 状态工厂接口
 * <p>
 * 定义状态创建和初始化的统一接口，用于消除代码中重复的状态初始化逻辑
 * </p>
 *
 * @author tianshuo
 */
public interface StateFactory {

    /**
     * 创建一个空的状态
     *
     * @return 新创建的空状态
     */
    State createState();

    /**
     * 基于初始数据创建状态
     *
     * @param initialData 初始数据
     * @return 包含初始数据的新状态
     */
    State createState(Map<String, Object> initialData);

    /**
     * 创建带有默认键策略的状态
     * <p>
     * 常用策略如 messages 使用 AppendStrategy，iteration 使用 ReplaceStrategy
     * </p>
     *
     * @return 配置了默认策略的状态
     */
    State createStateWithDefaultStrategies();

    /**
     * 创建带有默认键策略和初始数据的状态
     *
     * @param initialData 初始数据
     * @return 配置了默认策略和初始数据的状态
     */
    State createStateWithDefaultStrategies(Map<String, Object> initialData);
}
