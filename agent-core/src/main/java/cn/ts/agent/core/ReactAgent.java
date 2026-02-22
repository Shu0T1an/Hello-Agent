package cn.ts.agent.core;

import cn.ts.agent.api.Agent;
import cn.ts.agent.api.AgentConfig;
import cn.ts.agent.api.AgentResult;
import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.graph.CompiledGraph;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.hook.Hook;
import cn.ts.graph.observation.GraphLifecycleListener;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct Agent：组合 LLMNode 与 ToolNode。
 */
public class ReactAgent implements Agent {

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
    private final List<ModelInterceptor> modelInterceptors;
    private final AgentResultMapper resultMapper;

    public ReactAgent(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.graph = builder.graph;
        this.chatModel = builder.chatModel;
        this.advisors = builder.advisors;
        this.tools = builder.tools != null ? builder.tools : new Object[0];
        this.streaming = builder.streaming;
        this.hooks = builder.hooks != null ? new ArrayList<>(builder.hooks) : new ArrayList<>();
        this.modelInterceptors = builder.modelInterceptors != null ? new ArrayList<>(builder.modelInterceptors) : new ArrayList<>();
        this.resultMapper = new AgentResultMapper();
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
        private List<ModelInterceptor> modelInterceptors;
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

        public Builder hooks(List<Hook> hooks) {
            this.hooks = hooks;
            return this;
        }

        public Builder modelInterceptors(List<ModelInterceptor> modelInterceptors) {
            this.modelInterceptors = modelInterceptors;
            return this;
        }

        public Builder checkpointManager(CheckpointManager checkpointManager) {
            this.checkpointManager = checkpointManager;
            return this;
        }

        public Builder addLifecycleListener(GraphLifecycleListener listener) {
            if (this.lifecycleListeners == null) {
                this.lifecycleListeners = new ArrayList<>();
            }
            this.lifecycleListeners.add(listener);
            return this;
        }

        public Builder lifecycleListeners(List<GraphLifecycleListener> listeners) {
            this.lifecycleListeners = listeners != null ? new ArrayList<>(listeners) : null;
            return this;
        }

        public ReactAgent build() {
            ReActGraphFactory graphFactory = new ReActGraphFactory();
            this.graph = graphFactory.build(
                    chatModel,
                    advisors,
                    streaming,
                    tools,
                    hooks,
                    modelInterceptors,
                    checkpointManager,
                    lifecycleListeners
            );
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
            var initialState = cn.ts.graph.util.StateTemplates.createAgentInitialState(input, config.getMaxIterations());
            var graphResult = graph.invoke(initialState.data());
            return resultMapper.map(graphResult);
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

    public ChatModel getChatModel() {
        return chatModel;
    }

    public Object[] getTools() {
        return tools;
    }

    public CompiledGraph getGraph() {
        return graph;
    }
}
