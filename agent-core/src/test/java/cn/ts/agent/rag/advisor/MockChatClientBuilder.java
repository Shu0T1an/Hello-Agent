package cn.ts.agent.rag.advisor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Mock ChatClient 构建器
 * <p>
 * 用于测试时创建模拟的 ChatClient，支持配置响应内容和流式响应
 * </p>
 *
 * @author tianshuo
 */
public class MockChatClientBuilder {

    private String fixedResponse = "This is a mock response.";
    private boolean shouldThrowError = false;
    private Exception errorToThrow;
    private int callCount = 0;
    private boolean streamingEnabled = false;
    private String[] streamingChunks;

    private MockChatClientBuilder() {
    }

    /**
     * 创建新的构建器实例
     */
    public static MockChatClientBuilder builder() {
        return new MockChatClientBuilder();
    }

    /**
     * 设置固定的响应内容
     */
    public MockChatClientBuilder withFixedResponse(String response) {
        this.fixedResponse = response;
        return this;
    }

    /**
     * 设置为抛出错误
     */
    public MockChatClientBuilder withError(Exception error) {
        this.shouldThrowError = true;
        this.errorToThrow = error;
        return this;
    }

    /**
     * 启用流式响应
     */
    public MockChatClientBuilder withStreaming(String... chunks) {
        this.streamingEnabled = true;
        this.streamingChunks = chunks;
        return this;
    }

    /**
     * 获取调用次数
     */
    public int getCallCount() {
        return callCount;
    }

    /**
     * 重置调用计数
     */
    public void resetCallCount() {
        this.callCount = 0;
    }

    /**
     * 构建 Mock ChatModel
     */
    public ChatModel buildChatModel() {
        return new MockChatModelImpl(this);
    }

    /**
     * 构建 Mock ChatClient
     */
    public ChatClient buildChatClient() {
        ChatModel mockModel = buildChatModel();
        return ChatClient.builder(mockModel).build();
    }

    /**
     * 构建带 Advisor 的 Mock ChatClient
     */
    public ChatClient buildChatClient(Advisor... advisors) {
        ChatModel mockModel = buildChatModel();
        ChatClient.Builder builder = ChatClient.builder(mockModel);
        for (Advisor advisor : advisors) {
            builder.defaultAdvisors(advisor);
        }
        return builder.build();
    }

    // ==================== 内部实现类 ====================

    /**
     * Mock ChatModel 实现
     */
    private static class MockChatModelImpl implements ChatModel {

        private final MockChatClientBuilder builder;

        MockChatModelImpl(MockChatClientBuilder builder) {
            this.builder = builder;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            builder.callCount++;

            if (builder.shouldThrowError) {
                throw new RuntimeException("Mock chat error", builder.errorToThrow);
            }

            // 验证消息内容
            List<Message> messages = prompt.getInstructions();
            if (messages != null && !messages.isEmpty()) {
                for (Message message : messages) {
                    if (message instanceof UserMessage userMessage) {
                        String content = userMessage.getText();
                        // 可以在这里记录或验证内容
                    }
                }
            }

            AssistantMessage assistantMessage = new AssistantMessage(builder.fixedResponse);
            Generation generation = new Generation(assistantMessage);
            return new ChatResponse(List.of(generation));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            builder.callCount++;

            if (builder.shouldThrowError) {
                return Flux.error(new RuntimeException("Mock stream error", builder.errorToThrow));
            }

            if (!builder.streamingEnabled || builder.streamingChunks == null) {
                // 如果没有配置流式块，返回单块响应
                AssistantMessage assistantMessage = new AssistantMessage(builder.fixedResponse);
                Generation generation = new Generation(assistantMessage);
                return Flux.just(new ChatResponse(List.of(generation)));
            }

            // 返回配置的流式块
            return Flux.fromArray(builder.streamingChunks)
                    .map(chunk -> {
                        AssistantMessage message = new AssistantMessage(chunk);
                        Generation generation = new Generation(message);
                        return new ChatResponse(List.of(generation));
                    });
        }

        @Override
        public String toString() {
            return "MockChatModel";
        }
    }

    // ==================== 预定义配置 ====================

    /**
     * 创建默认的 Mock ChatClient（简单响应）
     */
    public static ChatClient createDefault() {
        return builder()
                .withFixedResponse("Default mock response for testing.")
                .buildChatClient();
    }

    /**
     * 创建带有 RAG 风格响应的 Mock ChatClient
     */
    public static ChatClient createWithRagResponse() {
        return builder()
                .withFixedResponse("Based on the retrieved documents, here is the answer to your question.")
                .buildChatClient();
    }

    /**
     *创建流式响应 Mock ChatClient
     */
    public static ChatClient createStreaming(String... chunks) {
        return builder()
                .withStreaming(chunks)
                .buildChatClient();
    }

    /**
     * 创建错误抛出的 Mock ChatClient
     */
    public static ChatClient createWithError(Exception error) {
        return builder()
                .withError(error)
                .buildChatClient();
    }
}
