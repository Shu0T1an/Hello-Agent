package cn.ts.graph.util;

import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import cn.ts.graph.state.strategy.AppendStrategy;
import cn.ts.graph.state.strategy.ReplaceStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 默认状态工厂实现
 * <p>
 * 提供 StateFactory 接口的默认实现，使用 MapState 作为状态实现
 * </p>
 *
 * @author tianshuo
 */
public class DefaultStateFactory implements StateFactory {

    private static final Logger logger = LoggerFactory.getLogger(DefaultStateFactory.class);

    /**
     * 默认消息键名
     */
    private static final String DEFAULT_MESSAGES_KEY = "messages";

    /**
     * 默认迭代键名
     */
    private static final String DEFAULT_ITERATION_KEY = "iteration";

    /**
     * 默认最大迭代键名
     */
    private static final String DEFAULT_MAX_ITERATIONS_KEY = "max_iterations";

    /**
     * 默认执行记录键名
     */
    private static final String DEFAULT_EXECUTE_RECORD_KEY = "execute_record";

    @Override
    public State createState() {
        return new MapState();
    }

    @Override
    public State createState(Map<String, Object> initialData) {
        return new MapState(initialData);
    }

    @Override
    public State createStateWithDefaultStrategies() {
        MapState state = new MapState();
        registerDefaultStrategies(state);
        logger.debug("State initialized with default strategies, keys: {}", state.keys());
        return state;
    }

    @Override
    public State createStateWithDefaultStrategies(Map<String, Object> initialData) {
        MapState state = new MapState(initialData);
        registerDefaultStrategies(state);
        logger.debug("State initialized with default strategies and data, keys: {}", state.keys());
        return state;
    }

    /**
     * 注册默认的键策略
     *
     * @param state 要配置策略的状态
     */
    private void registerDefaultStrategies(MapState state) {
        state.registerKeyStrategy(DEFAULT_MESSAGES_KEY, AppendStrategy.getInstance());
        state.registerKeyStrategy(DEFAULT_ITERATION_KEY, ReplaceStrategy.getInstance());
        state.registerKeyStrategy(DEFAULT_MAX_ITERATIONS_KEY, ReplaceStrategy.getInstance());
        state.registerKeyStrategy(DEFAULT_EXECUTE_RECORD_KEY, AppendStrategy.getInstance());
    }
}
