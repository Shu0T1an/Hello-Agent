package cn.ts.agent.interceptor;

import cn.ts.agent.model.ChatModelRequest;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.state.State;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable context shared across model interceptors.
 */
public final class ModelInvocationContext {

    private final State state;
    private final RunnableConfig config;
    private final ChatModelRequest request;
    private final boolean streaming;
    private final Map<String, Object> attributes;

    private ModelInvocationContext(
            State state,
            RunnableConfig config,
            ChatModelRequest request,
            boolean streaming,
            Map<String, Object> attributes) {
        this.state = Objects.requireNonNull(state, "state cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.request = Objects.requireNonNull(request, "request cannot be null");
        this.streaming = streaming;
        this.attributes = Collections.unmodifiableMap(new HashMap<>(attributes));
    }

    public static ModelInvocationContext of(
            State state,
            RunnableConfig config,
            ChatModelRequest request,
            boolean streaming) {
        return new ModelInvocationContext(state, config, request, streaming, Map.of());
    }

    public State state() {
        return state;
    }

    public RunnableConfig config() {
        return config;
    }

    public ChatModelRequest request() {
        return request;
    }

    public boolean streaming() {
        return streaming;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public ModelInvocationContext withRequest(ChatModelRequest newRequest) {
        return new ModelInvocationContext(state, config, newRequest, streaming, attributes);
    }

    public ModelInvocationContext withAttribute(String key, Object value) {
        Map<String, Object> newAttributes = new HashMap<>(attributes);
        newAttributes.put(key, value);
        return new ModelInvocationContext(state, config, request, streaming, newAttributes);
    }
}
