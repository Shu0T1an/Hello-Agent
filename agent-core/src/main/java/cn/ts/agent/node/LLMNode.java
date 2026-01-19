package cn.ts.agent.node;

import cn.ts.agent.model.ChatModelRequest;
import cn.ts.graph.node.NodeAction;
import cn.ts.graph.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 节点：调用 Spring AI ChatClient
 * <p>
 * 功能：
 * 1. 从 State 中获取对话历史
 * 2. 通过 ModelRequest 构建 ChatClient 请求
 * 3. 调用 ChatClient 进行 LLM 推理
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

    /**
     * 创建 LLM 节点
     *
     * @param chatModel ChatModel 实例
     * @param tools Spring AI 工具对象（使用 @Tool 注解的方法所在类）
     */
    public LLMNode(ChatModel chatModel, Object... tools) {
        this(chatModel, "You are a helpful assistant.", tools);
    }

    /**
     * 创建 LLM 节点（带系统提示词）
     *
     * @param chatModel ChatModel 实例
     * @param systemPrompt 系统提示词
     * @param tools Spring AI 工具对象
     */
    public LLMNode(ChatModel chatModel, String systemPrompt, Object... tools) {
        this.systemPrompt = systemPrompt;
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
     * 创建 LLM 节点（直接使用 ChatClient）
     *
     * @param chatClient ChatClient 实例
     */
    public LLMNode(ChatClient chatClient) {
        this(chatClient, "You are a helpful assistant.");
    }

    /**
     * 创建 LLM 节点（带系统提示词，直接使用 ChatClient）
     *
     * @param chatClient ChatClient 实例
     * @param systemPrompt 系统提示词
     */
    public LLMNode(ChatClient chatClient, String systemPrompt) {
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
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
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.chatOptions = chatOptions;
        this.toolCallbacks = toolCallbacks != null ? toolCallbacks : new ArrayList<>();
    }

    @Override
    public Map<String, Object> apply(State state) throws Exception {
        logger.debug("LLMNode processing state: {}", state);

        // 1. 构建请求
        ChatModelRequest request = ChatModelRequest.builder(state)
                .systemPrompt(systemPrompt)
                .baseOptions(chatOptions)
                .toolCallbacks(toolCallbacks)
                .build();

        ChatClient.ChatClientRequestSpec requestSpec = request.buildRequest(chatClient);

        // 2. 调用 ChatClient
        ChatResponse response = requestSpec.call().chatResponse();

        // 3. 构建新的消息列表（添加 AssistantMessage）
        List<Message> updatedMessages = new ArrayList<>(request.getMessages());
        updatedMessages.add(new AssistantMessage(response.getResult().getOutput().getText()));

        // 4. 返回状态更新
        return Map.of(
                "messages", updatedMessages,
                "chat_response", response,
                "content", response.getResult().getOutput().getText()
        );
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
}
