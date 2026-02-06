package cn.ts.agent.core;

import cn.ts.agent.api.*;
import cn.ts.agent.constant.StateKeys;
import cn.ts.agent.node.*;
import cn.ts.agent.util.MessageUtils;
import cn.ts.graph.*;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.hook.*;
import cn.ts.graph.node.AsyncNodeActionWithConfig;
import cn.ts.graph.node.InterruptableAction;
import cn.ts.graph.node.Node;
import cn.ts.graph.node.NodeAction;
import cn.ts.graph.observation.GraphLifecycleListener;
import cn.ts.graph.edge.EdgeAction;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import cn.ts.graph.state.strategy.AppendStrategy;
import cn.ts.graph.state.strategy.ReplaceStrategy;
import cn.ts.graph.util.StateFactory;
import cn.ts.graph.util.StateTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    private static final Logger logger = LoggerFactory.getLogger(ReactAgent.class);

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

    /**
     * 创建 Builder 实例
     *
     * @return Builder
     */
    public static Builder builder() {
        return new Builder();
    }



    private final String name;
    private final String description;
    private final CompiledGraph graph;
    private final ChatModel chatModel;
    private final List<Advisor> advisors;
    private final Object[] tools;
    private final boolean streaming;
    private final List<Hook> hooks;


    public ReactAgent(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.graph = builder.graph;
        this.chatModel = builder.chatModel;
        this.advisors = builder.advisors;
        this.tools = builder.tools != null ? builder.tools : new Object[0];
        this.streaming = builder.streaming;
        this.hooks = builder.hooks != null ? new ArrayList<>(builder.hooks) : new ArrayList<>();
    }



    public static class Builder {

        private String name;
        private String description;
        private CompiledGraph graph;
        private ChatModel chatModel;
        private List<Advisor> advisors;
        private Object[] tools;
        private boolean streaming;
        private List<Hook> hooks;
        private CheckpointManager checkpointManager;
        private List<GraphLifecycleListener> lifecycleListeners;


        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder graph(CompiledGraph graph) {
            this.graph = graph;
            return this;
        }
        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder advisors(List<Advisor> advisors) {
            this.advisors = advisors;
            return this;
        }
        public Builder streaming(boolean streaming) {
            this.streaming = streaming;
            return this;
        }

        public Builder tools(Object... tools) {
            this.tools = tools;
            return this;
        }

        /**
         * 设置 Hook 列表
         *
         * @param hooks Hook 列表
         * @return this
         */
        public Builder hooks(List<Hook> hooks) {
            this.hooks = hooks;
            return this;
        }

        /**
         * 设置检查点管理器
         * <p>
         * 用于支持状态持久化和中断恢复功能
         * </p>
         *
         * @param checkpointManager 检查点管理器
         * @return this
         */
        public Builder checkpointManager(CheckpointManager checkpointManager) {
            this.checkpointManager = checkpointManager;
            return this;
        }

        /**
         * 添加生命周期监听器
         * <p>
         * 用于监听图执行的各个阶段，实现可观测性、日志记录、性能监控等功能
         * </p>
         *
         * @param listener 生命周期监听器
         * @return this
         */
        public Builder addLifecycleListener(GraphLifecycleListener listener) {
            if (this.lifecycleListeners == null) {
                this.lifecycleListeners = new ArrayList<>();
            }
            this.lifecycleListeners.add(listener);
            return this;
        }

        /**
         * 设置生命周期监听器列表
         * <p>
         * 用于监听图执行的各个阶段，实现可观测性、日志记录、性能监控等功能
         * </p>
         *
         * @param listeners 生命周期监听器列表
         * @return this
         */
        public Builder lifecycleListeners(List<GraphLifecycleListener> listeners) {
            this.lifecycleListeners = listeners != null ? new ArrayList<>(listeners) : null;
            return this;
        }

        public ReactAgent build() {
            // 构建 ReAct 图，传递 checkpointManager 和 lifecycleListeners
            CompiledGraph compiledGraph = buildReActGraph(chatModel, advisors, streaming, tools, hooks, checkpointManager, lifecycleListeners);
            this.graph = compiledGraph;
            return new ReactAgent(this);
        }

    }

    @Override
    public AgentResult invoke(String input) {
        return invoke(input, AgentConfig.defaultConfig());
    }

    @Override
    public AgentResult invoke(String input, AgentConfig config) {
        try {
            // 使用 StateTemplates 创建初始状态
            State initialState = StateTemplates.createAgentInitialState(input, config.getMaxIterations());

            // 执行图
            GraphResult graphResult = graph.invoke(initialState.data());

            // 转换结果
            if (graphResult.isFailure()) {
                return AgentResult.failure(graphResult.error());
            }

            // 从 messages 中获取最后的 assistant 消息作为输出
            List<Message> messages = graphResult.finalState()
                    .<List<Message>>value(StateKeys.MESSAGES)
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
                        .<ChatResponse>value(StateKeys.CHAT_RESPONSE)
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
     * 支持 Hook 集成和检查点管理
     * </p>
     *
     * @param chatModel         ChatModel 实例
     * @param advisors          Advisor 列表
     * @param streaming         是否启用流式输出
     * @param tools             工具对象数组
     * @param hooks             Hook 列表
     * @param checkpointManager 检查点管理器（可选）
     * @param lifecycleListeners 生命周期监听器（可选）
     * @return 编译后的图
     */
    private static CompiledGraph buildReActGraph(ChatModel chatModel, List<Advisor> advisors, boolean streaming,
                                                  Object[] tools, List<Hook> hooks, CheckpointManager checkpointManager,
                                                  List<GraphLifecycleListener> lifecycleListeners) {
        StateGraph graph = new StateGraph();

        // 配置状态初始化器
        configureStateInitializer(graph);

        // 配置检查点管理器（如果提供）
        if (checkpointManager != null) {
            graph.setCheckpointManager(checkpointManager);
        }

        // 配置生命周期监听器（如果提供）
        if (lifecycleListeners != null && !lifecycleListeners.isEmpty()) {
            for (GraphLifecycleListener listener : lifecycleListeners) {
                graph.withLifecycleListener(listener);
            }
        }

        // 处理 null tools，使用空数组
        Object[] safeTools = tools != null ? tools : new Object[0];

        // 处理 null hooks，使用空列表
        List<Hook> safeHooks = hooks != null ? new ArrayList<>(hooks) : new ArrayList<>();

        // 创建核心节点
        LLMNode llmNode = LLMNode.builder(chatModel)
                .systemPrompt(cn.ts.agent.constant.AgentConstants.DEFAULT_SYSTEM_PROMPT)
                .streaming(streaming)
                .tools(safeTools)
                .advisors(advisors != null ? advisors : new ArrayList<>())
                .build();
        ToolNode toolNode = new ToolNode(safeTools);

        // 添加核心节点到图
        graph.addNode(NodeNames.MODEL, llmNode);
        graph.addNode(NodeNames.TOOL, toolNode);
        graph.addNode(NodeNames.END, NodeAction.of(state -> Map.of()));

        // 集成 Hook（包括条件边设置）
        String entryPoint = integrateHooks(graph, safeHooks, NodeNames.MODEL, NodeNames.TOOL, NodeNames.END);

        // 条件边：从 TOOL 节点判断是否继续迭代
        graph.addConditionalEdge(NodeNames.TOOL, ReactAgent::routeFromTool, toolRouteMapping());

        // 连接
        graph.addEdge(GraphConstants.START, entryPoint);
        graph.addEdge(NodeNames.END, GraphConstants.END);

        return graph.compile();
    }

    /**
     * 集成 Hook 到图中
     * <p>
     * 为每个 Hook 创建对应的图节点，并设置边连接
     * 正确处理条件边和 Hook 之间的关系
     * </p>
     *
     * @param graph       图
     * @param hooks       Hook 列表
     * @param modelNode   MODEL 节点名称
     * @param toolNode    TOOL 节点名称
     * @param endNode     END 节点名称
     * @return 入口点节点名称
     */
    private static String integrateHooks(StateGraph graph, List<Hook> hooks, String modelNode, String toolNode, String endNode) {
        if (hooks.isEmpty()) {
            // 无 Hook 时，直接从 MODEL 出发条件边
            graph.addConditionalEdge(modelNode, ReactAgent::routeFromModel, modelRouteMapping());
            return modelNode;
        }

        // 分类 Hook
        List<Hook> beforeModelHooks = new ArrayList<>();
        List<Hook> afterModelHooks = new ArrayList<>();

        for (Hook hook : hooks) {
            hook.setAgentName("ReactAgent"); // 设置 Agent 名称
            if (hook.supportsPosition(HookPosition.BEFORE_MODEL)) {
                beforeModelHooks.add(hook);
            }
            if (hook.supportsPosition(HookPosition.AFTER_MODEL)) {
                afterModelHooks.add(hook);
            }
        }

        // 构建执行链
        String entryPoint;
        String chainEnd; // 这是条件边的起点

        // 1. 创建所有 BEFORE_MODEL Hook 节点（顺序）
        for (Hook hook : beforeModelHooks) {
            String hookNodeName = Hook.getBeforeHookName(hook);
            createHookNode(graph, hook, hookNodeName, HookPosition.BEFORE_MODEL);
        }

        // 2. 创建所有 AFTER_MODEL Hook 节点（顺序）
        for (Hook hook : afterModelHooks) {
            String hookNodeName = Hook.getAfterHookName(hook);
            createHookNode(graph, hook, hookNodeName, HookPosition.AFTER_MODEL);
        }

        // 3. 构建 BEFORE_MODEL Hook 链的边连接
        String currentNode = modelNode;
        if (!beforeModelHooks.isEmpty()) {
            entryPoint = Hook.getBeforeHookName(beforeModelHooks.get(0));
            graph.addEdge(GraphConstants.START, entryPoint);

            // 连接 BEFORE_MODEL Hook 链
            for (int i = 0; i < beforeModelHooks.size(); i++) {
                String hookNodeName = Hook.getBeforeHookName(beforeModelHooks.get(i));
                if (i > 0) {
                    // 前一个 Hook 到当前 Hook
                    graph.addEdge(Hook.getBeforeHookName(beforeModelHooks.get(i - 1)), hookNodeName);
                }
            }

            // 最后一个 BEFORE_MODEL Hook 到 MODEL
            String lastBeforeHook = Hook.getBeforeHookName(beforeModelHooks.get(beforeModelHooks.size() - 1));
            graph.addEdge(lastBeforeHook, modelNode);
            currentNode = modelNode;
        } else {
            entryPoint = modelNode;
        }

        // 4. 构建 AFTER_MODEL Hook 链的边连接（逆序连接，栈行为：先进后出）
        if (!afterModelHooks.isEmpty()) {
            // 连接 MODEL 到最后一个 AFTER_MODEL Hook（逆序第一个）
            String firstAfterHookInReverse = Hook.getAfterHookName(afterModelHooks.get(afterModelHooks.size() - 1));
            graph.addEdge(currentNode, firstAfterHookInReverse);

            // 逆序连接 AFTER_MODEL Hook 链：hook[n-1] → hook[n-2] → ... → hook[0]
            for (int i = afterModelHooks.size() - 1; i > 0; i--) {
                String currentHookName = Hook.getAfterHookName(afterModelHooks.get(i));
                String nextHookName = Hook.getAfterHookName(afterModelHooks.get(i - 1));
                graph.addEdge(currentHookName, nextHookName);
            }

            // 从第一个 AFTER_MODEL Hook（逆序最后一个）发出条件边
            String lastAfterHookInReverse = Hook.getAfterHookName(afterModelHooks.get(0));
            graph.addConditionalEdge(lastAfterHookInReverse, ReactAgent::routeFromModel, modelRouteMapping());
        } else {
            // 没有 AFTER_MODEL Hook，直接从 MODEL 出发条件边
            chainEnd = modelNode;
            graph.addConditionalEdge(modelNode, ReactAgent::routeFromModel, modelRouteMapping());
        }

        return entryPoint;
    }

    /**
     * 创建 Hook 节点
     * <p>
     * 支持 InterruptableAction，如果是可中断的 Hook 则使用 Node.ofInterruptable()
     * 支持 AsyncNodeActionWithConfig，优先使用带 config 的新接口
     * </p>
     *
     * @param graph    图
     * @param hook     Hook 实例
     * @param nodeName 节点名称
     * @param position Hook 位置
     */
    private static void createHookNode(StateGraph graph, Hook hook, String nodeName, HookPosition position) {
        if (hook instanceof InterruptableAction interruptable) {
            // 可中断 Hook，使用 Node.ofInterruptable() 创建完整节点
            // 使用 addNode(Node) 方法保留 InterruptableAction 信息
            graph.addNode(Node.ofInterruptable(nodeName, interruptable));
        } else if (hook instanceof ModelHook modelHook) {
            // 普通 ModelHook
            graph.addNode(nodeName, createModelHookAction(modelHook, position));
        } else {
            // 默认空实现
            graph.addNode(nodeName, NodeAction.of(state -> Map.of()));
        }
    }

    /**
     * 创建 ModelHook 动作
     *
     * @param hook    ModelHook 实例
     * @param position Hook 位置
     * @return NodeAction
     */
    private static NodeAction createModelHookAction(ModelHook hook, HookPosition position) {
        return state -> {
            try {
                RunnableConfig config = RunnableConfig.defaultConfig();
                CompletableFuture<Map<String, Object>> future;

                if (position == HookPosition.BEFORE_MODEL) {
                    future = hook.beforeModel(state, config);
                } else {
                    future = hook.afterModel(state, config);
                }

                // 处理 JumpTo
                Map<String, Object> result = future.get();
                if (result.containsKey("jump_to")) {
                    Object jumpToValue = result.get("jump_to");
                    if (jumpToValue instanceof JumpTo jumpTo) {
                        // JumpTo 会在 RunnableConfig 中处理
                        return result;
                    }
                }

                return result;
            } catch (Exception e) {
                throw new RuntimeException("Hook execution failed: " + hook.getName(), e);
            }
        };
    }

    /**
     * 配置状态初始化器
     * <p>
     * 使用 StateFactory 统一状态创建逻辑
     * </p>
     */
    private static void configureStateInitializer(StateGraph graph) {
        StateFactory factory = StateTemplates.defaultFactory();
        graph.setStateInitializer(factory::createStateWithDefaultStrategies);
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
    private static String routeFromModel(State state) {
        List<Message> messages = state.value(StateKeys.MESSAGES, new ArrayList<Message>());
        return MessageUtils.MessageRouter.routeFromModel(messages, NodeNames.TOOL, NodeNames.MODEL, NodeNames.END);
    }

    /**
     * 从工具节点路由
     * <p>
     * 根据迭代次数决定是否继续循环
     * </p>
     */
    private static String routeFromTool(State state) {
        int iteration = state.<Integer>value(StateKeys.ITERATION).orElse(0);
        int maxIterations = state.<Integer>value(StateKeys.MAX_ITERATIONS).orElse(100);
        // iteration 已在 ToolNode 中递增

        if(iteration >= maxIterations){
            logger.info("Max iterations reached, ending the loop. Current iteration: {}", iteration);
        }
        return (iteration < maxIterations) ? NodeNames.MODEL : NodeNames.END;
    }

    /**
     * LLM 节点的路由映射
     */
    private static Map<String, String> modelRouteMapping() {
        return Map.of(
                NodeNames.TOOL, NodeNames.TOOL,
                NodeNames.MODEL, NodeNames.MODEL,
                NodeNames.END, NodeNames.END
        );
    }

    /**
     * 工具节点的路由映射
     */
    private static Map<String, String> toolRouteMapping() {
        return Map.of(
                NodeNames.MODEL, NodeNames.MODEL,
                NodeNames.END, NodeNames.END
        );
    }
}
