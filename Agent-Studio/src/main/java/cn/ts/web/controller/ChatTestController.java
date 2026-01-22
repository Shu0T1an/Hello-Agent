package cn.ts.web.controller;

import cn.ts.web.service.ChatTestService;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * ChatModel 测试控制器
 * 提供简单的聊天测试端点
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatTestController {

    private final ChatTestService chatTestService;

    public ChatTestController(ChatTestService chatTestService) {
        this.chatTestService = chatTestService;
    }

    /**
     * 简单聊天测试端点
     * GET /api/chat/test?message=你好
     *
     * @param message 用户消息
     * @return AI 模型的响应
     */
    @GetMapping("/test")
    public String testChat(@RequestParam String message) {
        return chatTestService.simpleChat(message);
    }

    /**
     * POST 方式的聊天测试
     * POST /api/chat/test
     * Body: {"message": "你好"}
     *
     * @param request 聊天请求
     * @return AI 模型的响应
     */
    @PostMapping("/test")
    public String testChatPost(@RequestBody ChatRequest request) {
        return chatTestService.simpleChat(request.message());
    }

    /**
     * 聊天请求记录
     */
    record ChatRequest(String message) {}


    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@RequestParam String message) {
        return chatTestService.streamChat(message)
                .doOnNext(chatResponse -> {
                    // 检查元数据中是否有 Usage
                    Usage usage = chatResponse.getMetadata().getUsage();
                    if (usage != null && usage.getTotalTokens() > 0) {
                        System.out.println("Input Tokens: " + usage.getPromptTokens());
                        System.out.println("Output Tokens: " + usage.getCompletionTokens());
                        System.out.println("Total Tokens: " + usage.getTotalTokens());
                    }
                })
                .map(chatResponse -> chatResponse.getResult().getOutput().getText())
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .event("message")
                        .build());
    }


    @GetMapping(value = "/streamHistory", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamHistoryChat(@RequestParam String message) {
        return chatTestService.streamHistoryChat(message)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .event("message")
                        .build());
    }

}
