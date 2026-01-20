package cn.ts.web.service;

import cn.ts.web.tools.SimpleTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * ChatModel 测试服务
 * 用于验证 Spring AI 模型是否正常工作
 */
@Service
@Slf4j
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
                .tools(new SimpleTools())
                .user(message)
                .call()
                .content();
    }

    public Flux<String> streamChat(String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }

    public Flux<String> streamHistoryChat(String message){
        StringBuffer sb = new StringBuffer();

        return chatClient.prompt()
                .user(message)
                .tools(new SimpleTools())
                .options(ToolCallingChatOptions.builder()
                        .internalToolExecutionEnabled(false)
                        .build())
                .stream()
                .content()
                .doOnNext(log::info)
                .doOnNext(t-> log.info("nihao "))
                .doOnNext(sb::append)
                .doOnComplete(() -> log.info(sb.toString()));
    }
}
