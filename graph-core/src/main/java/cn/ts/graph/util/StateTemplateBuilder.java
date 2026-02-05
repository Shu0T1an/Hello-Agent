package cn.ts.graph.util;

import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import cn.ts.graph.state.strategy.AppendStrategy;
import cn.ts.graph.state.strategy.KeyStrategy;
import cn.ts.graph.state.strategy.ReplaceStrategy;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 状态模板构建器
 * <p>
 * 提供流式 API 构建复杂的状态模板，支持预定义数据和策略配置
 * </p>
 *
 * @author tianshuo
 */
public class StateTemplateBuilder {

    private final Map<String, Object> data;
    private final Map<String, KeyStrategy<?>> strategies;

    private StateTemplateBuilder() {
        this.data = new HashMap<>();
        this.strategies = new HashMap<>();
    }

    /**
     * 创建新的构建器实例
     *
     * @return 新的构建器
     */
    public static StateTemplateBuilder builder() {
        return new StateTemplateBuilder();
    }

    /**
     * 添加键值对
     *
     * @param key   键
     * @param value 值
     * @return this
     */
    public StateTemplateBuilder put(String key, Object value) {
        this.data.put(key, value);
        return this;
    }

    /**
     * 批量添加键值对
     *
     * @param map 键值对映射
     * @return this
     */
    public StateTemplateBuilder putAll(Map<String, Object> map) {
        if (map != null) {
            this.data.putAll(map);
        }
        return this;
    }

    /**
     * 配置键使用追加策略
     *
     * @param key 键
     * @return this
     */
    public StateTemplateBuilder withAppendStrategy(String key) {
        this.strategies.put(key, AppendStrategy.getInstance());
        return this;
    }

    /**
     * 配置键使用替换策略
     *
     * @param key 键
     * @return this
     */
    public StateTemplateBuilder withReplaceStrategy(String key) {
        this.strategies.put(key, ReplaceStrategy.getInstance());
        return this;
    }

    /**
     * 配置键使用自定义策略
     *
     * @param key      键
     * @param strategy 策略
     * @return this
     */
    public StateTemplateBuilder withStrategy(String key, KeyStrategy<?> strategy) {
        this.strategies.put(key, strategy);
        return this;
    }

    /**
     * 使用配置函数自定义构建
     *
     * @param configurer 配置函数
     * @return this
     */
    public StateTemplateBuilder configure(Consumer<StateTemplateBuilder> configurer) {
        if (configurer != null) {
            configurer.accept(this);
        }
        return this;
    }

    /**
     * 构建状态
     *
     * @return 构建好的状态
     */
    public State build() {
        MapState state = new MapState(data);
        strategies.forEach(state::registerKeyStrategy);
        return state;
    }

    /**
     * 构建状态的不可变数据视图
     *
     * @return 状态数据的不可变映射
     */
    public Map<String, Object> buildData() {
        return Map.copyOf(data);
    }
}
