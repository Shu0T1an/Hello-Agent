package cn.ts.agent.core;

import cn.ts.agent.constant.StateKeys;
import cn.ts.agent.constant.AgentConstants;
import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.agent.node.LLMNode;
import cn.ts.agent.node.ToolNode;
import cn.ts.agent.util.MessageUtils;
import cn.ts.graph.CompiledGraph;
import cn.ts.graph.StateGraph;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.hook.Hook;
import cn.ts.graph.hook.HookPosition;
import cn.ts.graph.hook.ModelHook;
import cn.ts.graph.node.InterruptableAction;
import cn.ts.graph.node.Node;
import cn.ts.graph.node.NodeAction;
import cn.ts.graph.observation.GraphLifecycleListener;
import cn.ts.graph.state.State;
import cn.ts.graph.util.StateFactory;
import cn.ts.graph.util.StateTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ReActGraphFactory {

    private static final Logger logger = LoggerFactory.getLogger(ReActGraphFactory.class);

    private static final class NodeNames {
        static final String MODEL = GraphConstants.AGENT_MODEL;
        static final String TOOL = GraphConstants.AGENT_TOOL;
        static final String END = GraphConstants.AGENT_END;
    }

    private final ModelInvocationPipeline modelInvocationPipeline = new ModelInvocationPipeline();

    CompiledGraph build(
            ChatModel chatModel,
            List<Advisor> advisors,
            String systemPrompt,
            boolean streaming,
            Object[] tools,
            List<Hook> hooks,
            List<ModelInterceptor> modelInterceptors,
            CheckpointManager checkpointManager,
            List<GraphLifecycleListener> lifecycleListeners) {

        StateGraph graph = new StateGraph();
        configureStateInitializer(graph);

        if (checkpointManager != null) {
            graph.setCheckpointManager(checkpointManager);
        }
        if (lifecycleListeners != null && !lifecycleListeners.isEmpty()) {
            for (GraphLifecycleListener listener : lifecycleListeners) {
                graph.withLifecycleListener(listener);
            }
        }

        Object[] safeTools = tools != null ? tools : new Object[0];
        List<Hook> safeHooks = hooks != null ? new ArrayList<>(hooks) : new ArrayList<>();
        List<ModelInterceptor> safeInterceptors = modelInterceptors != null
                ? new ArrayList<>(modelInterceptors)
                : new ArrayList<>();
        String effectiveSystemPrompt = resolveSystemPrompt(systemPrompt);

        LLMNode llmNode = LLMNode.builder(chatModel)
                .systemPrompt(effectiveSystemPrompt)
                .streaming(streaming)
                .tools(safeTools)
                .advisors(advisors != null ? advisors : new ArrayList<>())
                .interceptors(safeInterceptors)
                .build();
        ToolNode toolNode = new ToolNode(safeTools);

        graph.addNode(NodeNames.MODEL, llmNode);
        graph.addNode(NodeNames.TOOL, toolNode);
        graph.addNode(NodeNames.END, NodeAction.of(state -> Map.of()));

        String entryPoint = integrateHooks(graph, safeHooks, NodeNames.MODEL);

        graph.addConditionalEdge(NodeNames.TOOL, ReActGraphFactory::routeFromTool, toolRouteMapping());
        graph.addEdge(GraphConstants.START, entryPoint);
        graph.addEdge(NodeNames.END, GraphConstants.END);

        return graph.compile();
    }

    private String integrateHooks(StateGraph graph, List<Hook> hooks, String modelNode) {
        if (hooks.isEmpty()) {
            graph.addConditionalEdge(modelNode, ReActGraphFactory::routeFromModel, modelRouteMapping());
            return modelNode;
        }

        List<Hook> beforeModelHooks = new ArrayList<>();
        List<Hook> afterModelHooks = new ArrayList<>();

        for (Hook hook : hooks) {
            hook.setAgentName("ReactAgent");
            if (hook.supportsPosition(HookPosition.BEFORE_MODEL)) {
                beforeModelHooks.add(hook);
            }
            if (hook.supportsPosition(HookPosition.AFTER_MODEL)) {
                afterModelHooks.add(hook);
            }
        }

        for (Hook hook : beforeModelHooks) {
            addHookNode(graph, hook, HookPosition.BEFORE_MODEL);
        }
        for (Hook hook : afterModelHooks) {
            addHookNode(graph, hook, HookPosition.AFTER_MODEL);
        }

        String entryPoint = modelNode;
        String currentNode = modelNode;

        if (!beforeModelHooks.isEmpty()) {
            entryPoint = Hook.getBeforeHookName(beforeModelHooks.get(0));
            for (int i = 1; i < beforeModelHooks.size(); i++) {
                graph.addEdge(Hook.getBeforeHookName(beforeModelHooks.get(i - 1)), Hook.getBeforeHookName(beforeModelHooks.get(i)));
            }
            graph.addEdge(Hook.getBeforeHookName(beforeModelHooks.get(beforeModelHooks.size() - 1)), modelNode);
        }

        if (!afterModelHooks.isEmpty()) {
            String firstAfterInReverse = Hook.getAfterHookName(afterModelHooks.get(afterModelHooks.size() - 1));
            graph.addEdge(currentNode, firstAfterInReverse);
            for (int i = afterModelHooks.size() - 1; i > 0; i--) {
                graph.addEdge(Hook.getAfterHookName(afterModelHooks.get(i)), Hook.getAfterHookName(afterModelHooks.get(i - 1)));
            }
            String lastAfterInReverse = Hook.getAfterHookName(afterModelHooks.get(0));
            graph.addConditionalEdge(lastAfterInReverse, ReActGraphFactory::routeFromModel, modelRouteMapping());
        } else {
            graph.addConditionalEdge(modelNode, ReActGraphFactory::routeFromModel, modelRouteMapping());
        }

        return entryPoint;
    }

    private void addHookNode(StateGraph graph, Hook hook, HookPosition position) {
        String nodeName = position == HookPosition.BEFORE_MODEL
                ? Hook.getBeforeHookName(hook)
                : Hook.getAfterHookName(hook);
        if (hook instanceof InterruptableAction interruptable) {
            graph.addNode(Node.ofInterruptable(nodeName, interruptable));
            return;
        }
        if (hook instanceof ModelHook modelHook) {
            graph.addNode(nodeName, modelInvocationPipeline.toNodeAction(modelHook, position));
            return;
        }
        graph.addNode(nodeName, NodeAction.of(state -> Map.of()));
    }

    private void configureStateInitializer(StateGraph graph) {
        StateFactory factory = StateTemplates.defaultFactory();
        graph.setStateInitializer(factory::createStateWithDefaultStrategies);
    }

    private static String routeFromModel(State state) {
        List<Message> messages = state.value(StateKeys.MESSAGES, new ArrayList<Message>());
        return MessageUtils.MessageRouter.routeFromModel(messages, NodeNames.TOOL, NodeNames.MODEL, NodeNames.END);
    }

    private static String routeFromTool(State state) {
        int iteration = state.<Integer>value(StateKeys.ITERATION).orElse(0);
        int maxIterations = state.<Integer>value(StateKeys.MAX_ITERATIONS).orElse(100);
        if (iteration >= maxIterations) {
            logger.info("Max iterations reached, ending the loop. Current iteration: {}", iteration);
        }
        return (iteration < maxIterations) ? NodeNames.MODEL : NodeNames.END;
    }

    private static Map<String, String> modelRouteMapping() {
        return Map.of(
                NodeNames.TOOL, NodeNames.TOOL,
                NodeNames.MODEL, NodeNames.MODEL,
                NodeNames.END, NodeNames.END
        );
    }

    private static Map<String, String> toolRouteMapping() {
        return Map.of(
                NodeNames.MODEL, NodeNames.MODEL,
                NodeNames.END, NodeNames.END
        );
    }

    private String resolveSystemPrompt(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return AgentConstants.DEFAULT_SYSTEM_PROMPT;
        }
        return systemPrompt;
    }
}
