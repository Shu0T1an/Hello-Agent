package cn.ts.agent.node;

import cn.ts.agent.constant.AgentConstants;
import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.agent.interceptor.ModelInterceptorChain;
import cn.ts.agent.interceptor.ModelInvocationContext;
import cn.ts.agent.interceptor.ModelInvocationResult;
import cn.ts.agent.interceptor.ModelInvoker;
import cn.ts.agent.model.ChatModelRequest;
import cn.ts.agent.retry.RetryConfig;
import cn.ts.agent.retry.RetryUtils;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.flux.GraphFlux;
import cn.ts.graph.node.NodeAction;
import cn.ts.graph.node.NodeActionWithConfig;
import cn.ts.graph.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static cn.ts.agent.Tool.ToolUtils.getAllToolCallbacksFromTools;

/**
 * LLM node backed by Spring AI ChatClient.
 */
public class LLMNode implements NodeAction, NodeActionWithConfig {

    private static final Logger logger = LoggerFactory.getLogger(LLMNode.class);

    private final ChatClient chatClient;
    private final String systemPrompt;
    private final ChatOptions chatOptions;
    private final List<ToolCallback> toolCallbacks;
    private final boolean streaming;
    private final RetryConfig retryConfig;
    private final boolean enableRetry;
    private final List<ModelInterceptor> interceptors;

    private LLMNode(Builder builder) {
        this.chatClient = builder.chatClient;
        this.systemPrompt = builder.systemPrompt;
        this.chatOptions = builder.chatOptions;
        this.toolCallbacks = builder.toolCallbacks != null ? builder.toolCallbacks : new ArrayList<>();
        this.streaming = builder.streaming;
        this.retryConfig = builder.retryConfig;
        this.enableRetry = builder.enableRetry;
        this.interceptors = builder.modelInterceptors != null ? List.copyOf(builder.modelInterceptors) : List.of();
    }

    @Override
    public Map<String, Object> apply(State state) throws Exception {
        return apply(state, RunnableConfig.defaultConfig());
    }

    @Override
    public Map<String, Object> apply(State state, RunnableConfig config) throws Exception {
        logger.debug("LLMNode processing state");

        ChatModelRequest request = ChatModelRequest.builder(state)
                .systemPrompt(systemPrompt)
                .baseOptions(chatOptions)
                .toolCallbacks(toolCallbacks)
                .build();

        ModelInvocationContext context = ModelInvocationContext.of(state, config, request, streaming);
        return invokeWithInterceptors(context).get().updates();
    }

    private CompletableFuture<ModelInvocationResult> invokeWithInterceptors(ModelInvocationContext context) {
        ModelInvoker terminalInvoker = invocationContext -> {
            ChatClient.ChatClientRequestSpec requestSpec = invocationContext.request().buildRequest(chatClient);
            Map<String, Object> updates = invocationContext.streaming()
                    ? applyStreaming(requestSpec)
                    : applyNonStreaming(requestSpec);
            return CompletableFuture.completedFuture(ModelInvocationResult.of(updates));
        };
        ModelInterceptorChain chain = ModelInterceptorChain.create(interceptors, terminalInvoker);
        return chain.proceed(context);
    }

    private Map<String, Object> applyNonStreaming(ChatClient.ChatClientRequestSpec requestSpec) {
        Mono<ChatResponse> responseMono = Mono.fromCallable(() -> requestSpec.call().chatResponse());

        if (enableRetry && retryConfig != null) {
            responseMono = Mono.defer(() -> Mono.fromCallable(() -> requestSpec.call().chatResponse()));
            responseMono = responseMono.retryWhen(RetryUtils.retryFor429(retryConfig, "LLM"));
        }

        Duration timeout = getTimeoutFromOptions();
        responseMono = responseMono.timeout(timeout);

        ChatResponse response = responseMono.block();
        if (response == null || response.getResult() == null) {
            throw new IllegalStateException("LLM response is null");
        }

        AssistantMessage assistantMessage = response.getResult().getOutput();
        return Map.of(
                "messages", List.of(assistantMessage),
                "chat_response", response
        );
    }

    private Duration getTimeoutFromOptions() {
        try {
            if (chatOptions != null) {
                logger.debug("Using default timeout for LLM call");
            }
        } catch (Exception e) {
            logger.warn("Failed to get timeout from options, using default: {}", e.getMessage());
        }
        return Duration.ofMinutes(2);
    }

    private Map<String, Object> applyStreaming(ChatClient.ChatClientRequestSpec requestSpec) {
        Flux<ChatResponse> stream = Flux.defer(() -> {
            logger.debug("Creating new streaming request");
            return requestSpec.stream().chatResponse();
        });

        if (enableRetry && retryConfig != null) {
            logger.debug("Enable retry for streaming request: maxRetries={}, initialBackoff={}, backoffMultiplier={}",
                    retryConfig.getMaxRetries(), retryConfig.getInitialBackoff(), retryConfig.getBackoffMultiplier());
            stream = stream.retryWhen(RetryUtils.retryFor429(retryConfig, "LLM"));
        }

        GraphFlux<ChatResponse> graphFlux = GraphFlux.of("llm", stream);
        return Map.of("llm_stream", graphFlux);
    }

    public static Builder builder(ChatModel chatModel) {
        return new Builder(chatModel);
    }

    public static Builder builder(ChatClient chatClient) {
        return new Builder(chatClient);
    }

    public static class Builder {
        private ChatClient chatClient;
        private String systemPrompt = AgentConstants.DEFAULT_SYSTEM_PROMPT;
        private ChatOptions chatOptions;
        private List<ToolCallback> toolCallbacks;
        private boolean streaming = AgentConstants.Defaults.DEFAULT_STREAMING;
        private List<Advisor> advisors;
        private RetryConfig retryConfig = RetryConfig.getDefault();
        private boolean enableRetry = AgentConstants.Defaults.DEFAULT_ENABLE_RETRY;
        private List<ModelInterceptor> modelInterceptors;

        private Builder(ChatModel chatModel) {
            buildClientFromModel(chatModel);
        }

        private Builder(ChatClient chatClient) {
            this.chatClient = chatClient;
        }

        private void buildClientFromModel(ChatModel chatModel) {
            ChatClient.Builder builder = ChatClient.builder(chatModel);
            ChatClient.Builder clientBuilder = builder.defaultOptions(OpenAiChatOptions.builder()
                    .streamUsage(true)
                    .internalToolExecutionEnabled(false)
                    .build());

            if (advisors != null && !advisors.isEmpty()) {
                clientBuilder.defaultAdvisors(advisors);
            }
            this.chatClient = clientBuilder.build();
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder streaming(boolean streaming) {
            this.streaming = streaming;
            return this;
        }

        public Builder chatOptions(ChatOptions chatOptions) {
            this.chatOptions = chatOptions;
            return this;
        }

        public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
            this.toolCallbacks = toolCallbacks;
            return this;
        }

        public Builder addToolCallback(ToolCallback toolCallback) {
            if (this.toolCallbacks == null) {
                this.toolCallbacks = new ArrayList<>();
            }
            this.toolCallbacks.add(toolCallback);
            return this;
        }

        public Builder interceptors(List<ModelInterceptor> interceptors) {
            this.modelInterceptors = interceptors != null ? new ArrayList<>(interceptors) : new ArrayList<>();
            return this;
        }

        public Builder addInterceptor(ModelInterceptor interceptor) {
            if (this.modelInterceptors == null) {
                this.modelInterceptors = new ArrayList<>();
            }
            this.modelInterceptors.add(interceptor);
            return this;
        }

        public Builder tools(Object... tools) {
            this.toolCallbacks = getAllToolCallbacksFromTools(tools);
            return this;
        }

        public final Builder advisors(Advisor... advisors) {
            this.advisors = List.of(advisors);
            return this;
        }

        public final Builder advisors(List<Advisor> advisors) {
            this.advisors = advisors;
            return this;
        }

        public Builder addAdvisor(Advisor advisor) {
            if (this.advisors == null) {
                this.advisors = new ArrayList<>();
            }
            this.advisors.add(advisor);
            return this;
        }

        public Builder retryConfig(RetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }

        public Builder enableRetry(boolean enableRetry) {
            this.enableRetry = enableRetry;
            return this;
        }

        public LLMNode build() {
            return new LLMNode(this);
        }
    }
}
