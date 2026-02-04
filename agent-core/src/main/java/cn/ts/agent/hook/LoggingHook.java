package cn.ts.agent.hook;

import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.hook.HookPosition;
import cn.ts.graph.hook.HookPositions;
import cn.ts.graph.hook.ModelHook;
import cn.ts.graph.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 日志记录 Hook
 * <p>
 * 记录 LLM 调用前后的状态信息，用于调试和监控
 * </p>
 *
 * @author tianshuo
 */
@HookPositions({HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL})
public class LoggingHook extends ModelHook {

    private static final Logger logger = LoggerFactory.getLogger(LoggingHook.class);

    private final boolean logMessages;
    private final boolean logState;
    private final String prefix;

    private LoggingHook(Builder builder) {
        this.logMessages = builder.logMessages;
        this.logState = builder.logState;
        this.prefix = builder.prefix != null ? builder.prefix : "[LoggingHook]";
    }

    @Override
    public String getName() {
        return "LoggingHook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(State state, RunnableConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("{} ========== BEFORE_MODEL ==========", prefix);

            if (logMessages) {
                List<Message> messages = state.value("messages", List.of());
                logger.info("{} 当前消息数量: {}", prefix, messages.size());
                for (int i = 0; i < messages.size(); i++) {
                    Message msg = messages.get(i);
                    String content = getMessageText(msg);
                    logger.info("{} 消息 {}: role={}, content={}",
                            prefix, i, msg.getMessageType().getValue(),
                            content != null ? content.substring(0, Math.min(100, content.length())) : "");
                }
            }

            if (logState) {
                logger.info("{} 当前状态键: {}", prefix, String.join(", ", toList(state.keys())));
                state.value("iteration").ifPresent(i -> logger.info("{} 迭代次数: {}", prefix, i));
            }

            return Map.of();
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(State state, RunnableConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("{} ========== AFTER_MODEL ==========", prefix);

            if (logMessages) {
                List<Message> messages = state.value("messages", List.of());
                logger.info("{} 消息数量: {} -> {}", prefix, messages.size() - 1, messages.size());

                if (!messages.isEmpty()) {
                    Message last = messages.get(messages.size() - 1);
                    String content = getMessageText(last);
                    logger.info("{} 最后一条消息: role={}, content={}",
                            prefix, last.getMessageType().getValue(),
                            content != null ? content.substring(0, Math.min(200, content.length())) : "");
                }
            }

            return Map.of();
        });
    }

    /**
     * 获取消息文本内容
     *
     * @param msg 消息
     * @return 文本内容
     */
    private String getMessageText(Message msg) {
        // 尝试获取不同类型消息的文本内容
        if (msg instanceof org.springframework.ai.chat.messages.UserMessage userMessage) {
            return userMessage.getText();
        } else if (msg instanceof org.springframework.ai.chat.messages.SystemMessage systemMessage) {
            return systemMessage.getText();
        } else if (msg instanceof org.springframework.ai.chat.messages.AssistantMessage assistantMessage) {
            return assistantMessage.getText();
        } else if (msg instanceof org.springframework.ai.chat.messages.ToolResponseMessage toolResponseMessage) {
            return toolResponseMessage.getResponses().toString();
        }
        return "";
    }

    private List<String> toList(Iterable<String> iterable) {
        List<String> result = new java.util.ArrayList<>();
        for (String item : iterable) {
            result.add(item);
        }
        return result;
    }

    /**
     * 创建 Builder
     *
     * @return Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder 类
     */
    public static class Builder {
        private boolean logMessages = true;
        private boolean logState = true;
        private String prefix;

        public Builder logMessages(boolean log) {
            this.logMessages = log;
            return this;
        }

        public Builder logState(boolean log) {
            this.logState = log;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public LoggingHook build() {
            return new LoggingHook(this);
        }
    }
}
