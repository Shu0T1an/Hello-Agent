package cn.ts.graph.test;

import cn.ts.graph.GraphConfig;
import cn.ts.graph.StateGraph;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.node.Node;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 测试工具类
 * <p>
 * 提供通用的测试辅助方法，用于创建 Mock 对象和测试数据
 * </p>
 *
 * @author tianshuo
 */
public class TestUtils {

    /**
     * 创建一个空的 Mock 状态
     *
     * @return 空的 MapState
     */
    public static State createMockState() {
        return new MapState();
    }

    /**
     * 创建带有初始数据的 Mock 状态
     *
     * @param data 初始数据
     * @return 带有数据的 MapState
     */
    public static State createMockState(Map<String, Object> data) {
        return new MapState(data);
    }

    /**
     * 创建带有消息列表的 Mock 状态
     *
     * @param messages 消息列表
     * @return 带有消息列表的 MapState
     */
    public static State createMockStateWithMessages(List<Message> messages) {
        Map<String, Object> data = new HashMap<>();
        data.put("messages", messages);
        data.put("iteration", 0);
        data.put("max_iterations", 10);
        return new MapState(data);
    }

    /**
     * 创建一个简单的测试图
     * <p>
     * 图结构: START -> node1 -> END
     * </p>
     *
     * @return 编译后的图
     */
    public static GraphConfig createSimpleGraphConfig() {
        return new GraphConfig(
                Map.of("node1", Node.of("node1", state -> Map.of("result", "node1 executed"))),
                List.of(cn.ts.graph.edge.Edge.of(GraphConstants.START, "node1")),
                "node1",
                MapState::new,
                null
        );
    }

    /**
     * 创建一个简单的测试图（可编译）
     * <p>
     * 图结构: START -> node1 -> END
     * </p>
     *
     * @return 编译后的图
     */
    public static StateGraph createSimpleGraph() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", state -> Map.of("result", "node1 executed"));
        graph.addEdge(GraphConstants.START, "node1");
        return graph;
    }

    /**
     * 创建一个带条件边的测试图
     * <p>
     * 图结构: START -> node1 -> (condition) -> node2/node3
     * </p>
     *
     * @return 编译后的图
     */
    public static StateGraph createConditionalGraph() {
        StateGraph graph = new StateGraph();
        graph.addNode("node1", state -> Map.of("result", "node1 executed"));
        graph.addNode("node2", state -> Map.of("result", "node2 executed"));
        graph.addNode("node3", state -> Map.of("result", "node3 executed"));

        graph.addEdge(GraphConstants.START, "node1");
        graph.addConditionalEdge("node1",
                state -> state.value("go_to_node2").orElse(false).equals(true) ? "node2" : "node3",
                Map.of("node2", "node2", "node3", "node3"));

        return graph;
    }

    /**
     * 创建一个用于测试消息的状态
     *
     * @return 初始化好的 MapState
     */
    public static MapState createMessageState() {
        MapState state = new MapState();
        state.registerKeyStrategy("messages", cn.ts.graph.state.strategy.AppendStrategy.getInstance());
        state.registerKeyStrategy("iteration", cn.ts.graph.state.strategy.ReplaceStrategy.getInstance());
        state.update("messages", new ArrayList<Message>());
        state.update("iteration", 0);
        state.update("max_iterations", 10);
        return state;
    }

    /**
     * 创建状态初始化器
     *
     * @return 状态初始化器
     */
    public static Supplier<State> createStateInitializer() {
        return TestUtils::createMessageState;
    }

    /**
     * 等待指定的毫秒数
     *
     * @param millis 等待的毫秒数
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 创建简单的用户消息列表
     *
     * @param text 消息文本
     * @return 消息列表
     */
    public static List<Message> createUserMessage(String text) {
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(text));
        return messages;
    }

    /**
     * 创建简单的助手消息列表
     *
     * @param text 消息文本
     * @return 消息列表
     */
    public static List<Message> createAssistantMessage(String text) {
        List<Message> messages = new ArrayList<>();
        messages.add(new AssistantMessage(text));
        return messages;
    }
}
