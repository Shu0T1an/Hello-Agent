package cn.ts.web.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * ChatModel 测试服务
 * 用于验证 Spring AI 模型是否正常工作
 */
@Service
public class ChatTestService {

    private final ChatClient chatClient;

    public ChatTestService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 简单的聊天测试
     *
     * @param message 用户消息
     * @return AI 模型的响应
     */
    public String simpleChat(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
