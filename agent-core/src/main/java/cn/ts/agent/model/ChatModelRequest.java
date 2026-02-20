package cn.ts.agent.model;

import cn.ts.graph.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 聊天模型请求构建器
 * <p>
 * 从 State 中提取数据并构建 ChatClient 请求
 * </p>
 *
 * @author tianshuo
 */
public class ChatModelRequest implements ModelRequest {

    private static final Logger logger = LoggerFactory.getLogger(ChatModelRequest.class);

    private final State state;
    private final String systemPrompt;
    private final ChatOptions baseOptions;
    private final List<ToolCallback> toolCallbacks;
    private final List<Message> messages;

    private ChatModelRequest(Builder builder) {
        this.state = builder.state;
        this.systemPrompt = builder.systemPrompt;
        this.baseOptions = builder.baseOptions;
        this.toolCallbacks = builder.toolCallbacks != null
                ? builder.toolCallbacks
                : new ArrayList<>();
        this.messages = buildMessages();
    }

    @Override
    public ChatClient.ChatClientRequestSpec buildRequest(ChatClient chatClient) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt();

        // 添加系统消息（包含工具提示）
        List<Message> totalMessages = new ArrayList<>();
        String enhancedSystemPrompt = buildEnhancedSystemPrompt();
        if (enhancedSystemPrompt != null && !enhancedSystemPrompt.isEmpty()) {
            totalMessages.add(new SystemMessage(enhancedSystemPrompt));
        }

        // 添加对话消息
        if (!messages.isEmpty()) {
            totalMessages.addAll(messages);
        }

        spec.messages(totalMessages);
        // 配置选项和工具
        ToolCallingChatOptions options = buildOptions();
        if (options != null) {
            spec = spec.options(options);
        }

        // 添加工具回调
        if (!toolCallbacks.isEmpty()) {
            spec = spec.toolCallbacks(toolCallbacks);
        }

        return spec;
    }

    @Override
    public List<Message> getMessages() {
        return new ArrayList<>(messages);
    }

    public State getState() {
        return state;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public ChatOptions getBaseOptions() {
        return baseOptions;
    }

    public List<ToolCallback> getToolCallbacks() {
        return new ArrayList<>(toolCallbacks);
    }

    /**
     * 构建增强的系统提示词（包含工具使用指南）
     */
    private String buildEnhancedSystemPrompt() {
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            return null;
        }

        StringBuilder enhancedPrompt = new StringBuilder(systemPrompt);

        // 检查是否有 todolist 相关工具
        if (hasTool("upsert_todos") || hasTool("list_todos")
                || hasTool("complete_todo") || hasTool("delete_todo")
                || hasTool("clear_todos")) {
            enhancedPrompt.append("\n\n## Todo List Management\n");
            enhancedPrompt.append("You have access to todo list management tools. Use them to track complex multi-step tasks:\n\n");
            enhancedPrompt.append("- **upsert_todos**: Preferred API. Incrementally create/update tasks.\n");
            enhancedPrompt.append("- **complete_todo**: Mark one task completed immediately when done.\n");
            enhancedPrompt.append("- **list_todos**: Retrieve current tasks with optional filters.\n");
            enhancedPrompt.append("- **delete_todo / clear_todos**: destructive operations.\n\n");
            enhancedPrompt.append("Rules:\n");
            enhancedPrompt.append("1) Prefer incremental updates over full replacement.\n");
            enhancedPrompt.append("2) Do not call todo-writing tools multiple times in parallel.\n");
            enhancedPrompt.append("3) Respect status transitions: pending -> in_progress -> completed (blocked allowed).\n");
        }

        return enhancedPrompt.toString();
    }

    /**
     * 检查是否存在指定名称的工具
     */
    private boolean hasTool(String toolName) {
        return toolCallbacks.stream()
                .anyMatch(tc -> tc.getToolDefinition().name().equals(toolName));
    }

    /**
     * 构建消息列表
     */
    @SuppressWarnings("unchecked")
    private List<Message> buildMessages() {
        List<Message> result = new ArrayList<>();

        // 优先使用 state 中的 messages（如果非空）
        Optional<List<Message>> existingMessages = state.value("messages");
        if (existingMessages.isPresent() && existingMessages.get() != null && !existingMessages.get().isEmpty()) {
            result.addAll(existingMessages.get());
        } else {
            // 如果 messages 不存在或为空，从 input 构建 UserMessage
            Optional<String> input = state.value("input");
            if (input.isPresent()) {
                result.add(new UserMessage(input.get()));
            }
        }

        return result;
    }

    /**
     * 构建选项配置
     */
    private ToolCallingChatOptions buildOptions() {
        List<ToolCallback> allCallbacks = new ArrayList<>(toolCallbacks);

        // 合并 baseOptions 中的工具
        if (baseOptions instanceof ToolCallingChatOptions toolCallingOptions) {
            for (ToolCallback callback : toolCallingOptions.getToolCallbacks()) {
                boolean exists = allCallbacks.stream()
                        .anyMatch(tc -> tc.getToolDefinition().name()
                                .equals(callback.getToolDefinition().name()));
                if (!exists) {
                    allCallbacks.add(callback);
                }
            }
            // 禁用内部工具执行
            toolCallingOptions.setInternalToolExecutionEnabled(false);
            toolCallingOptions.setToolCallbacks(allCallbacks);
            return toolCallingOptions;
        }

        // 创建新的选项
        if (!allCallbacks.isEmpty()) {
            return ToolCallingChatOptions.builder()
                    .toolCallbacks(allCallbacks)
                    .internalToolExecutionEnabled(false)
                    .build();
        }

        return null;
    }

    public static Builder builder(State state) {
        return new Builder(state);
    }

    public static class Builder {
        private final State state;
        private String systemPrompt;
        private ChatOptions baseOptions;
        private List<ToolCallback> toolCallbacks;

        private Builder(State state) {
            this.state = state;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder baseOptions(ChatOptions baseOptions) {
            this.baseOptions = baseOptions;
            return this;
        }

        public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
            this.toolCallbacks = toolCallbacks;
            return this;
        }

        public ChatModelRequest build() {
            return new ChatModelRequest(this);
        }
    }
}
