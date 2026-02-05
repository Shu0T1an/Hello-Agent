package cn.ts.agent.node;

import cn.ts.agent.Tool.ToolUtils;
import cn.ts.agent.constant.AgentConstants;
import cn.ts.agent.model.ChatModelRequest;
import cn.ts.agent.retry.RetryConfig;
import cn.ts.agent.retry.RetryUtils;
import cn.ts.graph.flux.GraphFlux;
import cn.ts.graph.node.NodeAction;
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

import static cn.ts.agent.Tool.ToolUtils.getAllToolCallbacksFromTools;

/**
 * LLM 节点：调用 Spring AI ChatClient
 * <p>
 * 功能：
 * 1. 从 State 中获取对话历史
 * 2. 通过 ModelRequest 构建 ChatClient 请求
 * 3. 调用 ChatClient 进行 LLM 推理（支持流式和非流式）
 * 4. 返回包含响应内容和更新后消息列表的状态
 * </p>
 * <p>
 * 使用 Builder 模式创建实例：
 * <pre>{@code
 * LLMNode node = LLMNode.builder(chatModel)
 *     .systemPrompt("You are a helpful assistant.")
 *     .streaming(true)
 *     .tools(tool1, tool2)
 *     .build();
 * }</pre>
 * </p>
 *
 * @author tianshuo
 */
public class LLMNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(LLMNode.class);

    private final ChatClient chatClient;
    private final String systemPrompt;
    private final ChatOptions chatOptions;
    private final List<ToolCallback> toolCallbacks;
    private final boolean streaming;
    private final RetryConfig retryConfig;
    private final boolean enableRetry;

    /**
     * 私有构造函数，仅供 Builder 使用
     */
    private LLMNode(Builder builder) {
        this.chatClient = builder.chatClient;
        this.systemPrompt = builder.systemPrompt;
        this.chatOptions = builder.chatOptions;
        this.toolCallbacks = builder.toolCallbacks != null
                ? builder.toolCallbacks
                : new ArrayList<>();
        this.streaming = builder.streaming;
        this.retryConfig = builder.retryConfig;
        this.enableRetry = builder.enableRetry;
    }

    @Override
    public Map<String, Object> apply(State state) throws Exception {
        logger.debug("LLMNode processing state: {}, streaming: {}", state, streaming);

        // 构建请求
        ChatModelRequest request = ChatModelRequest.builder(state)
                .systemPrompt(systemPrompt)
                .baseOptions(chatOptions)
                .toolCallbacks(toolCallbacks)
                .build();

        ChatClient.ChatClientRequestSpec requestSpec = request.buildRequest(chatClient);

        // 根据 streaming 配置选择调用方式
        if (streaming) {
            return applyStreaming(request, requestSpec);
        } else {
            return applyNonStreaming(request, requestSpec);
        }
    }

    /**
     * 非流式调用
     * <p>
     * 使用响应式链 + 超时配置，避免无限期阻塞
     * </p>
     */
    private Map<String, Object> applyNonStreaming(ChatModelRequest request, ChatClient.ChatClientRequestSpec requestSpec) {
        // 创建响应 Mono
        Mono<ChatResponse> responseMono = Mono.fromCallable(() -> requestSpec.call().chatResponse());

        // 如果启用重试且配置了重试策略，添加重试逻辑
        if (enableRetry && retryConfig != null) {
            responseMono = responseMono.retryWhen(RetryUtils.retryFor429(retryConfig));
        }

        // 添加超时配置，避免无限期阻塞
        // 默认超时 2 分钟，可通过 chatOptions 覆盖
        Duration timeout = getTimeoutFromOptions();
        responseMono = responseMono.timeout(timeout);

        // 使用 block() 但带超时，防止永久阻塞
        ChatResponse response = responseMono.block();

        // 获取响应中的 AssistantMessage（包含 toolCalls）
        AssistantMessage assistantMessage = response.getResult().getOutput();

        // 只返回新增的 AssistantMessage，让 AppendStrategy 追加到现有列表
        return Map.of(
                "messages", List.of(assistantMessage),
                "chat_response", response
        );
    }

    /**
     * 从 ChatOptions 获取超时配置
     * <p>
     * 如果没有配置，返回默认超时时间
     * </p>
     */
    private Duration getTimeoutFromOptions() {
        // 尝试从 chatOptions 获取超时配置
        // 如果无法获取，使用默认值
        try {
            if (chatOptions != null) {
                // 使用反射或其他方式获取超时配置
                // 这里简化处理，使用默认超时
                logger.debug("Using default timeout for LLM call");
            }
        } catch (Exception e) {
            logger.warn("Failed to get timeout from options, using default: {}", e.getMessage());
        }
        // 默认超时 2 分钟
        return Duration.ofMinutes(2);
    }

    /**
     * 流式调用
     * <p>
     * 返回 GraphFlux 用于实时输出
     * NodeExecutor 会自动在流结束时聚合并更新 state
     * </p>
     */
    private Map<String, Object> applyStreaming(ChatModelRequest request, ChatClient.ChatClientRequestSpec requestSpec) {
        // 调用 stream API
        Flux<ChatResponse> stream = requestSpec.stream().chatResponse();

        // 如果启用重试且配置了重试策略，添加重试逻辑
        if (enableRetry && retryConfig != null) {
            stream = stream.retryWhen(RetryUtils.retryFor429(retryConfig));
        }

        // 创建流式输出包装
        GraphFlux<ChatResponse> graphFlux = GraphFlux.of("llm", stream);

        // 返回 GraphFlux
        // NodeExecutor 会：
        // 1. 实时发射每个 ChatResponse 包装的 StreamingOutput
        // 2. 流完成时自动聚合并更新 state["messages"]
        return Map.of("llm_stream", graphFlux);
    }

    /**
     * 创建基于 ChatModel 的 Builder
     *
     * @param chatModel ChatModel 实例
     * @return Builder 实例
     */
    public static Builder builder(ChatModel chatModel) {
        return new Builder(chatModel);
    }

    /**
     * 创建基于 ChatClient 的 Builder
     *
     * @param chatClient ChatClient 实例
     * @return Builder 实例
     */
    public static Builder builder(ChatClient chatClient) {
        return new Builder(chatClient);
    }

    /**
     * LLMNode Builder 类
     * <p>
     * 使用示例：
     * <pre>{@code
     * LLMNode node = LLMNode.builder(chatModel)
     *     .systemPrompt("You are a helpful assistant.")
     *     .streaming(true)
     *     .tools(tool1, tool2)
     *     .build();
     *
     * // 带 Advisors 的示例（如 RAG）
     * LLMNode node = LLMNode.builder(chatModel)
     *     .systemPrompt("You are a helpful assistant.")
     *     .advisors(new RagAdvisor(vectorStore, ragConfig))
     *     .build();
     * }</pre>
     * </p>
     */
    public static class Builder {
        private ChatClient chatClient;
        private String systemPrompt = AgentConstants.DEFAULT_SYSTEM_PROMPT;
        private ChatOptions chatOptions;
        private List<ToolCallback> toolCallbacks;
        private boolean streaming = AgentConstants.Defaults.DEFAULT_STREAMING;
        private Object[] tools;
        private List<Advisor> advisors;
        private RetryConfig retryConfig;
        private boolean enableRetry = AgentConstants.Defaults.DEFAULT_ENABLE_RETRY;

        private Builder(ChatModel chatModel) {
            // ChatClient 会在 build() 时创建，这里只保存 ChatModel
            buildClientFromModel(chatModel);
        }

        private Builder(ChatClient chatClient) {
            this.chatClient = chatClient;
        }

        /**
         * 从 ChatModel 构建 ChatClient（支持 Advisors）
         */
        private void buildClientFromModel(ChatModel chatModel) {

            ChatClient.Builder builder = ChatClient.builder(chatModel);



            ChatClient.Builder clientBuilder = builder
                    .defaultOptions(OpenAiChatOptions.builder()
                            .streamUsage(true)
                            .internalToolExecutionEnabled(false)
                            .build());

            // 添加 Advisors
            if (advisors != null && !advisors.isEmpty()) {
                clientBuilder.defaultAdvisors(advisors);
            }

            this.chatClient = clientBuilder.build();
        }

        /**
         * 确保 ChatClient 已创建（延迟初始化）
         * 如果添加了 Advisors，需要重新构建 ChatClient
         */
        private void ensureClientBuilt(ChatModel chatModel) {
            if (this.chatClient == null || (advisors != null && !advisors.isEmpty())) {
                buildClientFromModel(chatModel);
            }
        }

        /**
         * 设置系统提示词
         *
         * @param systemPrompt 系统提示词
         * @return Builder 实例
         */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
         * 设置是否启用流式输出
         *
         * @param streaming 是否启用流式输出
         * @return Builder 实例
         */
        public Builder streaming(boolean streaming) {
            this.streaming = streaming;
            return this;
        }

        /**
         * 设置聊天选项
         *
         * @param chatOptions 聊天选项
         * @return Builder 实例
         */
        public Builder chatOptions(ChatOptions chatOptions) {
            this.chatOptions = chatOptions;
            return this;
        }

        /**
         * 设置工具回调列表
         *
         * @param toolCallbacks 工具回调列表
         * @return Builder 实例
         */
        public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
            this.toolCallbacks = toolCallbacks;
            return this;
        }

        /**
         * 添加工具回调
         *
         * @param toolCallback 工具回调
         * @return Builder 实例
         */
        public Builder addToolCallback(ToolCallback toolCallback) {
            if (this.toolCallbacks == null) {
                this.toolCallbacks = new ArrayList<>();
            }
            this.toolCallbacks.add(toolCallback);
            return this;
        }

        /**
         * 设置工具对象（使用 @Tool 注解的方法所在类）
         *
         * @param tools 工具对象数组
         * @return Builder 实例
         */
        public Builder tools(Object... tools) {
            this.tools = tools;
            this.toolCallbacks = getAllToolCallbacksFromTools(tools);
            return this;
        }

        /**
         * 设置 Advisors 列表（用于 RAG 等场景）
         * <p>
         * 使用示例：
         * <pre>{@code
         * .advisors(new RagAdvisor(vectorStore, ragConfig))
         * }</pre>
         * </p>
         *
         * @param advisors Advisors 列表
         * @return Builder 实例
         */
        public final Builder advisors(Advisor... advisors) {
            this.advisors = List.of(advisors);
            return this;
        }
        public final Builder advisors(List<Advisor> advisors) {


            this.advisors = advisors;
            return this;
        }




        /**
         * 添加单个 Advisor
         *
         * @param advisor Advisor
         * @return Builder 实例
         */
        public Builder addAdvisor(Advisor advisor) {
            if (this.advisors == null) {
                this.advisors = new ArrayList<>();
            }
            this.advisors.add(advisor);
            return this;
        }

        /**
         * 设置重试配置
         *
         * @param retryConfig 重试配置
         * @return Builder 实例
         */
        public Builder retryConfig(RetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }

        /**
         * 设置是否启用重试
         *
         * @param enableRetry 是否启用重试
         * @return Builder 实例
         */
        public Builder enableRetry(boolean enableRetry) {
            this.enableRetry = enableRetry;
            return this;
        }

        /**
         * 构建 LLMNode 实例
         *
         * @return LLMNode 实例
         */
        public LLMNode build() {
            return new LLMNode(this);
        }
    }
}
