package cn.ts.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 节点配置类
 * <p>
 * 使用 record + 静态工厂方法简化 LLMNode 构造
 * </p>
 *
 * @author tianshuo
 */
public record LLMNodeConfig(
        ChatModel chatModel,
        ChatClient chatClient,
        String systemPrompt,
        ChatOptions chatOptions,
        List<ToolCallback> toolCallbacks,
        boolean streaming
) {

    /**
     * 从 ChatModel 创建配置
     *
     * @param chatModel ChatModel 实例
     * @param tools     Spring AI 工具对象
     * @return 配置实例
     */
    public static LLMNodeConfig of(ChatModel chatModel, Object... tools) {
        return new LLMNodeConfig(
                chatModel,
                null,
                "You are a helpful assistant.",
                ToolCallingChatOptions.builder()
                        .internalToolExecutionEnabled(false)
                        .build(),
                new ArrayList<>(),
                false
        );
    }

    /**
     * 从 ChatClient 创建配置
     *
     * @param chatClient ChatClient 实例
     * @return 配置实例
     */
    public static LLMNodeConfig of(ChatClient chatClient) {
        return new LLMNodeConfig(
                null,
                chatClient,
                "You are a helpful assistant.",
                null,
                new ArrayList<>(),
                false
        );
    }

    /**
     * 设置系统提示词
     *
     * @param prompt 系统提示词
     * @return 新配置实例
     */
    public LLMNodeConfig withSystemPrompt(String prompt) {
        return new LLMNodeConfig(chatModel, chatClient, prompt, chatOptions, toolCallbacks, streaming);
    }

    /**
     * 设置流式输出
     *
     * @param streaming 是否启用流式输出
     * @return 新配置实例
     */
    public LLMNodeConfig withStreaming(boolean streaming) {
        return new LLMNodeConfig(chatModel, chatClient, systemPrompt, chatOptions, toolCallbacks, streaming);
    }

    /**
     * 设置聊天选项
     *
     * @param options 聊天选项
     * @return 新配置实例
     */
    public LLMNodeConfig withChatOptions(ChatOptions options) {
        return new LLMNodeConfig(chatModel, chatClient, systemPrompt, options, toolCallbacks, streaming);
    }

    /**
     * 设置工具回调列表
     *
     * @param callbacks 工具回调列表
     * @return 新配置实例
     */
    public LLMNodeConfig withToolCallbacks(List<ToolCallback> callbacks) {
        return new LLMNodeConfig(chatModel, chatClient, systemPrompt, chatOptions, callbacks, streaming);
    }

    /**
     * 构建 LLMNode 实例
     *
     * @return LLMNode 实例
     */
    public cn.ts.agent.node.LLMNode build() {
        if (chatClient != null) {
            return new cn.ts.agent.node.LLMNode(chatClient, systemPrompt, chatOptions, toolCallbacks, streaming);
        } else if (chatModel != null) {
            // 使用 LLMNode(ChatModel, String, boolean, Object...) 构造函数
            // tools 传空数组，因为工具已经通过 ChatOptions 设置
            return new cn.ts.agent.node.LLMNode(chatModel, systemPrompt, streaming, new Object[0]);
        } else {
            throw new IllegalStateException("Either chatModel or chatClient must be set");
        }
    }
}
