package cn.ts.graph.test;

import cn.ts.graph.GraphConfig;
import cn.ts.graph.StateGraph;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.edge.Edge;
import cn.ts.graph.node.Node;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 测试固定装置类
 * <p>
 * 提供预定义的测试数据和 Mock 对象，减少测试代码重复
 * </p>
 *
 * @author tianshuo
 */
public class TestFixture {

    // ==================== 节点动作固定装置 ====================

    /**
     * 简单的节点动作，只返回一个固定结果
     */
    public static final cn.ts.graph.node.NodeAction SIMPLE_NODE_ACTION = state -> Map.of("result", "executed");

    /**
     * 返回状态数据的节点动作
     */
    public static final cn.ts.graph.node.NodeAction STATE_NODE_ACTION = state -> {
        Map<String, Object> result = new HashMap<>();
        result.put("count", 1);
        result.put("message", "Node executed");
        return result;
    };

    /**
     * 抛出异常的节点动作（用于测试异常处理）
     */
    public static final cn.ts.graph.node.NodeAction ERROR_NODE_ACTION = state -> {
        throw new RuntimeException("Test exception from node");
    };

    /**
     * 返回空 Map 的节点动作
     */
    public static final cn.ts.graph.node.NodeAction EMPTY_NODE_ACTION = state -> Map.of();

    // ==================== 路由动作固定装置 ====================

    /**
     * 始终返回 "continue" 的路由动作
     */
    public static final cn.ts.graph.edge.EdgeAction CONTINUE_ROUTE_ACTION = state -> "continue";

    /**
     * 始终返回 "end" 的路由动作
     */
    public static final cn.ts.graph.edge.EdgeAction END_ROUTE_ACTION = state -> "end";

    /**
     * 基于状态值的路由动作
     */
    public static final cn.ts.graph.edge.EdgeAction CONDITIONAL_ROUTE_ACTION = state ->
            state.value("condition", "default");

    // ==================== 测试数据固定装置 ====================

    /**
     * 空的初始状态
     */
    public static final Map<String, Object> EMPTY_INITIAL_STATE = Map.of();

    /**
     * 带有基本数据的初始状态
     */
    public static final Map<String, Object> BASIC_INITIAL_STATE = Map.of(
            "input", "test input",
            "iteration", 0,
            "max_iterations", 10
    );

    /**
     * 带有消息列表的初始状态
     */
    public static final Map<String, Object> MESSAGE_INITIAL_STATE = Map.of(
            "input", "test input",
            "messages", new ArrayList<Message>(),
            "iteration", 0,
            "max_iterations", 10
    );

    /**
     * 带有条件值的初始状态（用于条件边测试）
     */
    public static final Map<String, Object> CONDITIONAL_INITIAL_STATE = Map.of(
            "condition", "branch_a"
    );

    // ==================== 路由映射固定装置 ====================

    /**
     * 简单的两路路由映射
     */
    public static final Map<String, String> SIMPLE_ROUTE_MAPPING = Map.of(
            "continue", "node2",
            "end", GraphConstants.END
    );

    /**
     * 三路路由映射
     */
    public static final Map<String, String> THREE_WAY_ROUTE_MAPPING = Map.of(
            "branch_a", "node_a",
            "branch_b", "node_b",
            "default", GraphConstants.END
    );

    // ==================== 状态初始化器固定装置 ====================

    /**
     * 创建基本的 MapState 初始化器
     */
    public static final Supplier<State> BASIC_STATE_INITIALIZER = MapState::new;

    /**
     * 创建带有策略注册的 MapState 初始化器
     */
    public static final Supplier<State> STRATEGY_STATE_INITIALIZER = () -> {
        MapState state = new MapState();
        state.registerKeyStrategy("messages", cn.ts.graph.state.strategy.AppendStrategy.getInstance());
        state.registerKeyStrategy("iteration", cn.ts.graph.state.strategy.ReplaceStrategy.getInstance());
        state.registerKeyStrategy("max_iterations", cn.ts.graph.state.strategy.ReplaceStrategy.getInstance());
        return state;
    };

    // ==================== 图配置固定装置 ====================

    /**
     * 创建简单的图配置
     * <p>
     * 结构: START -> node1 -> END
     * </p>
     */
    public static GraphConfig createSimpleGraphConfig() {
        return new GraphConfig(
                Map.of("node1", Node.of("node1", SIMPLE_NODE_ACTION)),
                List.of(Edge.of(GraphConstants.START, "node1")),
                "node1",
                BASIC_STATE_INITIALIZER,
                null
        );
    }

    /**
     * 创建多节点图配置
     * <p>
     * 结构: START -> node1 -> node2 -> END
     * </p>
     */
    public static GraphConfig createMultiNodeGraphConfig() {
        return new GraphConfig(
                Map.of(
                        "node1", Node.of("node1", SIMPLE_NODE_ACTION),
                        "node2", Node.of("node2", STATE_NODE_ACTION)
                ),
                List.of(
                        Edge.of(GraphConstants.START, "node1"),
                        Edge.of("node1", "node2")
                ),
                "node1",
                BASIC_STATE_INITIALIZER,
                null
        );
    }

    /**
     * 创建带条件边的图配置
     * <p>
     * 结构: START -> node1 -> (route) -> node_a / node_b / END
     * </p>
     */
    public static GraphConfig createConditionalGraphConfig() {
        return new GraphConfig(
                Map.of(
                        "node1", Node.of("node1", STATE_NODE_ACTION),
                        "node_a", Node.of("node_a", SIMPLE_NODE_ACTION),
                        "node_b", Node.of("node_b", SIMPLE_NODE_ACTION)
                ),
                List.of(
                        Edge.of(GraphConstants.START, "node1"),
                        Edge.conditional("node1", CONDITIONAL_ROUTE_ACTION, THREE_WAY_ROUTE_MAPPING)
                ),
                "node1",
                BASIC_STATE_INITIALIZER,
                null
        );
    }

    // ==================== 消息固定装置 ====================

    /**
     * 创建用户消息
     */
    public static UserMessage createUserMessage(String text) {
        return new UserMessage(text);
    }

    /**
     * 创建助手消息
     */
    public static AssistantMessage createAssistantMessage(String text) {
        return new AssistantMessage(text);
    }

    /**
     * 创建带有工具调用的助手消息
     * 注意：由于 Spring AI 版本差异，此方法仅返回简单消息
     * 如需测试工具调用，请在具体测试类中使用 Mock 对象
     */
    public static AssistantMessage createAssistantMessageWithToolCalls() {
        // 返回简单的 AssistantMessage，避免版本兼容性问题
        return new AssistantMessage("I'll call a tool");
    }

    /**
     * 创建测试消息列表
     */
    public static List<Message> createTestMessages() {
        List<Message> messages = new ArrayList<>();
        messages.add(createUserMessage("Hello"));
        messages.add(createAssistantMessage("Hi there!"));
        return messages;
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建带有指定键值对的状态
     *
     * @param key   键
     * @param value 值
     * @return 状态 Map
     */
    public static Map<String, Object> stateWith(String key, Object value) {
        return Map.of(key, value);
    }

    /**
     * 创建带有多个键值对的状态
     *
     * @param entries 键值对数组（交替的键和值）
     * @return 状态 Map
     */
    @SafeVarargs
    public static <T> Map<String, Object> stateWithEntries(Map.Entry<String, T>... entries) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, T> entry : entries) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * 创建简单的 StateGraph
     * <p>
     * 结构: START -> node1 -> END
     * </p>
     */
    public static StateGraph createSimpleStateGraph() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", SIMPLE_NODE_ACTION);
        graph.addEdge(GraphConstants.START, "node1");
        return graph;
    }

    /**
     * 创建多节点 StateGraph
     * <p>
     * 结构: START -> node1 -> node2 -> node3 -> END
     * </p>
     */
    public static StateGraph createMultiNodeStateGraph() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", SIMPLE_NODE_ACTION);
        graph.addNode("node2", STATE_NODE_ACTION);
        graph.addNode("node3", EMPTY_NODE_ACTION);
        graph.addEdge(GraphConstants.START, "node1");
        graph.addEdge("node1", "node2");
        graph.addEdge("node2", "node3");
        return graph;
    }
}
