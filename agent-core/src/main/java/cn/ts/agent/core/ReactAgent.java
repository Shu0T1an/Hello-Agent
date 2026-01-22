package cn.ts.agent.core;

import cn.ts.agent.api.*;
import cn.ts.agent.node.*;
import cn.ts.graph.*;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.node.NodeAction;
import cn.ts.graph.edge.EdgeAction;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import cn.ts.graph.state.strategy.AppendStrategy;
import cn.ts.graph.state.strategy.ReplaceStrategy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct Agent：组合 LLMNode 和 ToolNode
 * <p>
 * 流程：
 * START → llmNode → (有toolCalls?) → toolNode → llmNode → ...
 * ↓
 * (无) → END
 * </p>
 *
 * @author tianshuo
 */
public class ReactAgent implements Agent {

    /**
     * 节点名称常量
     * <p>
     * 使用内部常量类避免硬编码字符串散布在代码中
     * </p>
     */
    private static final class NodeNames {
        static final String MODEL = GraphConstants.AGENT_MODEL;
        static final String TOOL = GraphConstants.AGENT_TOOL;
        static final String END = GraphConstants.AGENT_END;
    }

    private final String name;
    private final String description;
    private final CompiledGraph graph;
    private final ChatModel chatModel;
    private final Object[] tools;
    private final boolean streaming;

    /**
     * 创建 ReAct Agent
     *
     * @param name        Agent 名称
     * @param description Agent 描述
     * @param chatModel   ChatModel 实例
     * @param tools       Spring AI 工具对象（使用 @Tool 注解的方法所在类）
     */
    public ReactAgent(String name, String description, ChatModel chatModel, Object... tools) {
        this(name, description, chatModel, false, tools);
    }

    /**
     * 创建 ReAct Agent（使用默认描述）
     *
     * @param name       Agent 名称
     * @param chatModel  ChatModel 实例
     * @param tools      Spring AI 工具对象
     */
    public ReactAgent(String name, ChatModel chatModel, Object... tools) {
        this(name, "ReAct Agent with tool calling capabilities", chatModel, false, tools);
    }

    /**
     * 创建 ReAct Agent（支持流式配置）
     *
     * @param name        Agent 名称
     * @param description Agent 描述
     * @param chatModel   ChatModel 实例
     * @param streaming   是否启用流式输出
     * @param tools       Spring AI 工具对象
     */
    public ReactAgent(String name, String description, ChatModel chatModel, boolean streaming, Object... tools) {
        this.name = name;
        this.description = description;
        this.chatModel = chatModel;
        this.tools = tools;
        this.streaming = streaming;
        this.graph = buildReActGraph(chatModel, streaming, tools);
    }

    @Override
    public AgentResult invoke(String input) {
        return invoke(input, AgentConfig.defaultConfig());
    }

    @Override
    public AgentResult invoke(String input, AgentConfig config) {
        try {
            // 准备初始状态
            Map<String, Object> initialState = Map.of(
                    "input", input,
                    "max_iterations", config.getMaxIterations(),
                    "iteration", 0,
                    "messages", new ArrayList<Message>(),
                    "execute_record", new ArrayList<Map<String, Object>>()
            );

            // 执行图
            GraphResult graphResult = graph.invoke(initialState);

            // 转换结果
            if (graphResult.isFailure()) {
                return AgentResult.failure(graphResult.error());
            }

            // 从 messages 中获取最后的 assistant 消息作为输出
            List<Message> messages = graphResult.finalState()
                    .<List<Message>>value("messages")
                    .orElse(new ArrayList<>());

            // 获取最后一个 assistant 消息
            String output = "";
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message msg = messages.get(i);
                if (msg instanceof AssistantMessage) {
                    AssistantMessage am = (AssistantMessage) msg;
                    output = am.getText();
                    break;
                }
            }

            // 如果没有找到 assistant 消息，尝试从 chat_response 获取
            if (output.isEmpty()) {
                ChatResponse response = graphResult.finalState()
                        .<ChatResponse>value("chat_response")
                        .orElse(null);
                if (response != null && !response.getResults().isEmpty()) {
                    output = response.getResults().get(0).getOutput().getText();
                }
            }

            return AgentResult.success(output, graphResult);
        } catch (Exception e) {
            return AgentResult.failure(e);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * 获取 ChatModel
     *
     * @return ChatModel 实例
     */
    public ChatModel getChatModel() {
        return chatModel;
    }

    /**
     * 获取工具列表
     *
     * @return 工具对象数组
     */
    public Object[] getTools() {
        return tools;
    }

    /**
     * 构建可执行的状态图
     *
     * @return 编译后的图
     */
    public CompiledGraph getGraph() {
        return graph;
    }

    /**
     * 构建 ReAct 循环图
     * <p>
     * 使用内部常量和提取的私有方法，提高代码可读性和可维护性
     * </p>
     *
     * @param chatModel ChatModel 实例
     * @param streaming 是否启用流式输出
     * @param tools     工具对象数组
     * @return 编译后的图
     */
    private CompiledGraph buildReActGraph(ChatModel chatModel, boolean streaming, Object[] tools) {
        StateGraph graph = new StateGraph();

        // 配置状态初始化器
        configureStateInitializer(graph);

        // 创建节点
        LLMNode llmNode = new LLMNode(chatModel, "You are a helpful assistant.", streaming, tools);
        ToolNode toolNode = new ToolNode(tools);

        // 添加节点到图
        graph.addNode(NodeNames.MODEL, llmNode);
        graph.addNode(NodeNames.TOOL, toolNode);
        // 添加 AGENT_END 空节点，作为流程的终点中转站
        graph.addNode(NodeNames.END, NodeAction.of(state -> Map.of()));

        // 条件边1：判断最后一条消息是否有 toolCalls
        graph.addConditionalEdge(NodeNames.MODEL, this::routeFromModel, modelRouteMapping());

        // 条件边2：判断是否继续迭代
        graph.addConditionalEdge(NodeNames.TOOL, this::routeFromTool, toolRouteMapping());

        // 连接
        graph.addEdge(GraphConstants.START, NodeNames.MODEL);
        graph.addEdge(NodeNames.END, GraphConstants.END);

        return graph.compile();
    }

    /**
     * 配置状态初始化器
     * <p>
     * 提取为私有方法，提高代码可读性
     * </p>
     */
    private void configureStateInitializer(StateGraph graph) {
        graph.setStateInitializer(() -> {
            MapState state = new MapState();
            // messages 使用追加策略，这样多个节点的输出可以追加到同一个列表
            state.registerKeyStrategy("messages", AppendStrategy.getInstance());
            System.out.println("State initialized with keys: " + state.keys());
            // iteration 使用替换策略（这是默认策略，但显式声明更清晰）
            state.registerKeyStrategy("iteration", ReplaceStrategy.getInstance());
            // max_iterations 使用替换策略
            state.registerKeyStrategy("max_iterations", ReplaceStrategy.getInstance());
            state.registerKeyStrategy("execute_record", AppendStrategy.getInstance());
            return state;
        });
    }

    /**
     * 从 LLM 节点路由
     * <p>
     * 根据最后一条消息决定下一个节点：
     * - 有 toolCalls → 工具节点
     * - 是 ToolResponseMessage → LLM 节点（继续循环）
     * - 其他 → 结束节点
     * </p>
     */
    private String routeFromModel(State state) {
        List<Message> messages = state.value("messages", new ArrayList<Message>());
        if (messages.isEmpty()) {
            return NodeNames.END;
        }

        Message last = messages.get(messages.size() - 1);
        if (last instanceof AssistantMessage am && am.hasToolCalls()) {
            return NodeNames.TOOL;
        } else if (last instanceof ToolResponseMessage) {
            return NodeNames.MODEL;
        }
        return NodeNames.END;
    }

    /**
     * 从工具节点路由
     * <p>
     * 根据迭代次数决定是否继续循环
     * </p>
     */
    private String routeFromTool(State state) {
        int iteration = state.<Integer>value("iteration").orElse(0);
        int maxIterations = state.<Integer>value("max_iterations").orElse(10);
        // iteration 已在 ToolNode 中递增
        return (iteration < maxIterations) ? NodeNames.MODEL : NodeNames.END;
    }

    /**
     * LLM 节点的路由映射
     */
    private Map<String, String> modelRouteMapping() {
        return Map.of(
                NodeNames.TOOL, NodeNames.TOOL,
                NodeNames.MODEL, NodeNames.MODEL,
                NodeNames.END, NodeNames.END
        );
    }

    /**
     * 工具节点的路由映射
     */
    private Map<String, String> toolRouteMapping() {
        return Map.of(
                NodeNames.MODEL, NodeNames.MODEL,
                NodeNames.END, NodeNames.END
        );
    }
}
