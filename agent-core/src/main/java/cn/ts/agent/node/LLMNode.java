package cn.ts.agent.node;

import cn.ts.agent.model.ChatModelRequest;
import cn.ts.graph.flux.GraphFlux;
import cn.ts.graph.node.NodeAction;
import cn.ts.graph.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM 节点：调用 Spring AI ChatClient
 * <p>
 * 功能：
 * 1. 从 State 中获取对话历史
 * 2. 通过 ModelRequest 构建 ChatClient 请求
 * 3. 调用 ChatClient 进行 LLM 推理（支持流式和非流式）
 * 4. 返回包含响应内容和更新后消息列表的状态
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

    /**
     * 创建 LLM 节点（非流式）
     *
     * @param chatModel ChatModel 实例
     * @param tools Spring AI 工具对象（使用 @Tool 注解的方法所在类）
     */
    public LLMNode(ChatModel chatModel, Object... tools) {
        this(chatModel, "You are a helpful assistant.", false, tools);
    }

    /**
     * 创建 LLM 节点（带系统提示词）
     *
     * @param chatModel ChatModel 实例
     * @param systemPrompt 系统提示词
     * @param tools Spring AI 工具对象
     */
    public LLMNode(ChatModel chatModel, String systemPrompt, Object... tools) {
        this(chatModel, systemPrompt, false, tools);
    }

    /**
     * 创建 LLM 节点（支持流式配置）
     *
     * @param chatModel ChatModel 实例
     * @param systemPrompt 系统提示词
     * @param streaming 是否启用流式输出
     * @param tools Spring AI 工具对象
     */
    public LLMNode(ChatModel chatModel, String systemPrompt, boolean streaming, Object... tools) {
        this.systemPrompt = systemPrompt;
        this.streaming = streaming;
        this.chatOptions = ToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .build();
        this.toolCallbacks = new ArrayList<>();
        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(this.chatOptions)
                .defaultTools(tools)
                .build();
    }

    /**
     * 创建 LLM 节点（直接使用 ChatClient，非流式）
     *
     * @param chatClient ChatClient 实例
     */
    public LLMNode(ChatClient chatClient) {
        this(chatClient, "You are a helpful assistant.", false);
    }

    /**
     * 创建 LLM 节点（带系统提示词，直接使用 ChatClient，非流式）
     *
     * @param chatClient ChatClient 实例
     * @param systemPrompt 系统提示词
     */
    public LLMNode(ChatClient chatClient, String systemPrompt) {
        this(chatClient, systemPrompt, false);
    }

    /**
     * 创建 LLM 节点（带系统提示词和流式配置，直接使用 ChatClient）
     *
     * @param chatClient ChatClient 实例
     * @param systemPrompt 系统提示词
     * @param streaming 是否启用流式输出
     */
    public LLMNode(ChatClient chatClient, String systemPrompt, boolean streaming) {
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.streaming = streaming;
        this.chatOptions = null;
        this.toolCallbacks = new ArrayList<>();
    }

    /**
     * 创建 LLM 节点（完整配置）
     *
     * @param chatClient ChatClient 实例
     * @param systemPrompt 系统提示词
     * @param chatOptions 聊天选项
     * @param toolCallbacks 工具回调列表
     */
    public LLMNode(ChatClient chatClient, String systemPrompt,
                   ChatOptions chatOptions, List<ToolCallback> toolCallbacks) {
        this(chatClient, systemPrompt, chatOptions, toolCallbacks, false);
    }

    /**
     * 创建 LLM 节点（完整配置，支持流式）
     *
     * @param chatClient ChatClient 实例
     * @param systemPrompt 系统提示词
     * @param chatOptions 聊天选项
     * @param toolCallbacks 工具回调列表
     * @param streaming 是否启用流式输出
     */
    public LLMNode(ChatClient chatClient, String systemPrompt,
                   ChatOptions chatOptions, List<ToolCallback> toolCallbacks, boolean streaming) {
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.chatOptions = chatOptions;
        this.toolCallbacks = toolCallbacks != null ? toolCallbacks : new ArrayList<>();
        this.streaming = streaming;
    }

    @Override
    public Map<String, Object> apply(State state) throws Exception {
        logger.debug("LLMNode processing state: {}, streaming: {}", state, streaming);

        // 1. 构建请求
        ChatModelRequest request = ChatModelRequest.builder(state)
                .systemPrompt(systemPrompt)
                .baseOptions(chatOptions)
                .toolCallbacks(toolCallbacks)
                .build();

        ChatClient.ChatClientRequestSpec requestSpec = request.buildRequest(chatClient);

        // 2. 根据 streaming 配置选择调用方式
        if (streaming) {
            return applyStreaming(request, requestSpec);
        } else {
            return applyNonStreaming(request, requestSpec);
        }
    }

    /**
     * 非流式调用
     */
    private Map<String, Object> applyNonStreaming(ChatModelRequest request, ChatClient.ChatClientRequestSpec requestSpec) {
        // 调用 ChatClient
        ChatResponse response = requestSpec.call().chatResponse();

        // 获取响应中的 AssistantMessage（包含 toolCalls）
        AssistantMessage assistantMessage = response.getResult().getOutput();

        // 只返回新增的 AssistantMessage，让 AppendStrategy 追加到现有列表
        return Map.of(
                "messages", List.of(assistantMessage),
                "chat_response", response
        );
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

        // 创建流式输出包装
        GraphFlux<ChatResponse> graphFlux = GraphFlux.of("llm", stream);

        // 返回 GraphFlux
        // NodeExecutor 会：
        // 1. 实时发射每个 ChatResponse 包装的 StreamingOutput
        // 2. 流完成时自动聚合并更新 state["messages"]
        return Map.of("llm_stream", graphFlux);
    }

    /**
     * 获取系统提示词
     *
     * @return 系统提示词
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * 获取 ChatClient
     *
     * @return ChatClient 实例
     */
    public ChatClient getChatClient() {
        return chatClient;
    }

    /**
     * 是否启用流式输出
     *
     * @return true 如果启用流式输出
     */
    public boolean isStreaming() {
        return streaming;
    }
}
