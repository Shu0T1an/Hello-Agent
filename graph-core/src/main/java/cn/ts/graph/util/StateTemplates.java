package cn.ts.graph.util;

import cn.ts.graph.state.State;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 状态模板类
 * <p>
 * 提供预定义的状态创建模板，简化常用状态的创建过程
 * </p>
 *
 * @author tianshuo
 */
public final class StateTemplates {

    // 状态键常量（独立定义，避免跨模块依赖）
    private static final class Keys {
        static final String INPUT = "input";
        static final String MESSAGES = "messages";
        static final String ITERATION = "iteration";
        static final String MAX_ITERATIONS = "max_iterations";
        static final String EXECUTE_RECORD = "execute_record";
        static final String CURRENT_AGENT = "current_agent";
        static final String AGENT_HISTORY = "agent_history";
    }

    private StateTemplates() {
        // 防止实例化
    }

    /**
     * 创建 Agent 初始状态模板
     * <p>
     * 包含输入、迭代次数、消息列表和执行记录的默认值
     * </p>
     *
     * @param input         用户输入
     * @param maxIterations 最大迭代次数
     * @return 初始状态
     */
    public static State createAgentInitialState(String input, int maxIterations) {
        return StateTemplateBuilder.builder()
                .put(Keys.INPUT, input)
                .put(Keys.MAX_ITERATIONS, maxIterations)
                .put(Keys.ITERATION, 0)
                .put(Keys.MESSAGES, new ArrayList<Message>())
                .put(Keys.EXECUTE_RECORD, new ArrayList<Map<String, Object>>())
                .withAppendStrategy(Keys.MESSAGES)
                .withReplaceStrategy(Keys.ITERATION)
                .withReplaceStrategy(Keys.MAX_ITERATIONS)
                .withAppendStrategy(Keys.EXECUTE_RECORD)
                .build();
    }

    /**
     * 创建空的 Agent 状态模板
     * <p>
     * 包含所有必要键的空值，用于会话初始化
     * </p>
     *
     * @return 空的 Agent 状态
     */
    public static State createEmptyAgentState() {
        return StateTemplateBuilder.builder()
                .put(Keys.MESSAGES, new ArrayList<Message>())
                .put(Keys.EXECUTE_RECORD, new ArrayList<Map<String, Object>>())
                .put(Keys.ITERATION, 0)
                .withAppendStrategy(Keys.MESSAGES)
                .withReplaceStrategy(Keys.ITERATION)
                .withAppendStrategy(Keys.EXECUTE_RECORD)
                .build();
    }

    /**
     * 创建会话初始状态模板
     * <p>
     * 用于创建新会话时的初始状态
     * </p>
     *
     * @param agentName Agent 名称
     * @return 会话初始状态
     */
    public static State createSessionInitialState(String agentName) {
        return StateTemplateBuilder.builder()
                .put(Keys.MESSAGES, new ArrayList<Message>())
                .put(Keys.CURRENT_AGENT, agentName)
                .put(Keys.AGENT_HISTORY, new ArrayList<String>())
                .put(Keys.ITERATION, 0)
                .withAppendStrategy(Keys.MESSAGES)
                .withReplaceStrategy(Keys.CURRENT_AGENT)
                .withAppendStrategy(Keys.AGENT_HISTORY)
                .withReplaceStrategy(Keys.ITERATION)
                .build();
    }

    /**
     * 创建带有消息的状态
     *
     * @param messages  消息列表
     * @param iteration 迭代次数
     * @return 包含消息的状态
     */
    public static State createStateWithMessages(List<Message> messages, int iteration) {
        return StateTemplateBuilder.builder()
                .put(Keys.MESSAGES, messages)
                .put(Keys.ITERATION, iteration)
                .withAppendStrategy(Keys.MESSAGES)
                .withReplaceStrategy(Keys.ITERATION)
                .build();
    }

    /**
     * 创建带有自定义数据的状态
     *
     * @param customData 自定义数据
     * @return 包含自定义数据的状态
     */
    public static State createWithCustomData(Map<String, Object> customData) {
        return StateTemplateBuilder.builder()
                .putAll(customData)
                .build();
    }

    /**
     * 创建默认配置的状态工厂
     * <p>
     * 返回配置好的 StateFactory 实例，可以直接使用
     * </p>
     *
     * @return 默认状态工厂
     */
    public static StateFactory defaultFactory() {
        return new DefaultStateFactory();
    }
}
