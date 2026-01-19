package cn.ts.agent.core;

import cn.ts.agent.api.*;
import cn.ts.agent.node.*;
import cn.ts.graph.*;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.node.NodeAction;
import cn.ts.graph.edge.EdgeAction;
import cn.ts.graph.state.State;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;

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

    private final String name;
    private final String description;
    private final CompiledGraph graph;
    private final ChatModel chatModel;
    private final Object[] tools;

    /**
     * 创建 ReAct Agent
     *
     * @param name Agent 名称
     * @param description Agent 描述
     * @param chatModel ChatModel 实例
     * @param tools Spring AI 工具对象（使用 @Tool 注解的方法所在类）
     */
    public ReactAgent(String name, String description, ChatModel chatModel, Object... tools) {
        this.name = name;
        this.description = description;
        this.chatModel = chatModel;
        this.tools = tools;
        this.graph = buildReActGraph(chatModel, tools);
    }

    /**
     * 创建 ReAct Agent（使用默认描述）
     *
     * @param name Agent 名称
     * @param chatModel ChatModel 实例
     * @param tools Spring AI 工具对象
     */
    public ReactAgent(String name, ChatModel chatModel, Object... tools) {
        this(name, "ReAct Agent with tool calling capabilities", chatModel, tools);
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
                    "chat_history", new java.util.ArrayList<Map<String, String>>()
            );

            // 执行图
            GraphResult graphResult = graph.invoke(initialState);

            // 转换结果
            if (graphResult.isFailure()) {
                return AgentResult.failure(graphResult.error());
            }

            // 从 chat_history 中获取最后的 assistant 消息作为输出
            java.util.List<Map<String, String>> chatHistory = graphResult.finalState()
                    .<java.util.List<Map<String, String>>>value("chat_history")
                    .orElse(new java.util.ArrayList<>());

            // 获取最后一个 assistant 消息
            String output = "";
            for (int i = chatHistory.size() - 1; i >= 0; i--) {
                Map<String, String> msg = chatHistory.get(i);
                if ("assistant".equals(msg.get("role"))) {
                    output = msg.get("content");
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
     *
     * @param chatModel ChatModel 实例
     * @param tools 工具对象数组
     * @return 编译后的图
     */
    private CompiledGraph buildReActGraph(ChatModel chatModel, Object[] tools) {
        StateGraph graph = new StateGraph();

        // 创建节点
        LLMNode llmNode = new LLMNode(chatModel, tools);
        ToolNode toolNode = new ToolNode();

        // 添加节点到图
        graph.addNode("llm", llmNode);
        graph.addNode("tools", toolNode);

        // 条件边：判断是否有 toolCalls
        graph.addConditionalEdge("llm",
                new EdgeAction() {
                    @Override
                    public String route(State state) {
                        ChatResponse response = state.<ChatResponse>value("chat_response").orElse(null);
                        if (response != null && response.hasToolCalls()) {
                            return "tools";
                        }
                        return "end";
                    }
                },
                Map.of("tools", "tools", "end", "end")
        );

        // 条件边：判断是否继续迭代
        graph.addConditionalEdge("tools",
                new EdgeAction() {
                    @Override
                    public String route(State state) {
                        int iteration = state.<Integer>value("iteration").orElse(0);
                        int maxIterations = state.<Integer>value("max_iterations").orElse(10);
                        // 注意：iteration 的递增应该在节点中完成，这里只是读取
                        return (iteration + 1 < maxIterations) ? "llm" : "end";
                    }
                },
                Map.of("llm", "llm", "end", "end")
        );

        // 连接
        graph.addEdge(GraphConstants.START, "llm");
        graph.addEdge("end", GraphConstants.END);

        return graph.compile();
    }
}
