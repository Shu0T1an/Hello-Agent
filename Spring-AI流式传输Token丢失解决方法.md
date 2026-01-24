# 针对 Spring AI 流式传输 Token 丢失的解决方法

## 前言

在使用 Spring AI 进行大语言模型（LLM）应用开发时，流式传输（Streaming）是实现实时用户体验的重要技术。然而，许多开发者在使用流式 API 时遇到了一个棘手的问题：**Token 使用统计（Usage）数据丢失**。

本文将详细分析这个问题，并提供完整的解决方案。

---

## 一、问题背景

### 1.1 问题表现

在非流式调用中，我们可以正常获取到 `ChatResponse` 中的 Token 使用情况：

```java
ChatResponse response = chatClient.prompt()
    .user(message)
    .call()
    .chatResponse();

Usage usage = response.getMetadata().getUsage();
System.out.println("Total Tokens: " + usage.getTotalTokens());  // 正常输出
```

但在流式调用中，同样的代码却无法获取到有效的 Token 数据：

```java
Flux<ChatResponse> stream = chatClient.prompt()
    .user(message)
    .stream()
    .chatResponse();

stream.doOnNext(response -> {
    Usage usage = response.getMetadata().getUsage();
    // usage 为 null 或者 getTotalTokens() 返回 0
    System.out.println("Total Tokens: " + usage.getTotalTokens());
});
```

### 1.2 问题根因

这个问题的根本原因在于 **OpenAI API 的默认行为**。

在流式传输模式下，OpenAI API 默认不会在每个响应帧（chunk）中包含 Token 使用统计信息。这是为了减少流式传输的数据量，提升传输效率。

然而，对于需要精确计费、成本控制或监控 Token 消耗的应用来说，这是一个严重的问题。

---

## 二、解决方案

Spring AI 提供了 `OpenAiChatOptions` 配置类，通过设置 `streamUsage` 属性为 `true`，可以强制 OpenAI 在流式传输中也返回 Token 使用情况。

### 2.1 核心配置代码

```java
OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
    .streamUsage(true)  // 关键配置
    .build();
```

---

## 三、实现方式

### 方式一：在 ChatClient 调用时动态配置

适用于临时需要 Token 统计的场景：

```java
public Flux<ChatResponse> streamChat(String message) {
    // 构建带有 streamUsage 配置的 ChatOptions
    OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
            .streamUsage(true)
            .build();

    return chatClient.prompt()
            .user(message)
            .options(openAiChatOptions)  // 应用配置
            .stream()
            .chatResponse();
}
```

### 方式二：在 ChatClient 构建时设置为默认配置

**推荐方式**，适用于整个应用都需要 Token 统计的场景：

```java
public LLMNode(ChatModel chatModel, String systemPrompt, boolean streaming, Object... tools) {
    this.systemPrompt = systemPrompt;
    this.streaming = streaming;

    // 构建默认的 ChatOptions，启用 streamUsage
    this.chatOptions = OpenAiChatOptions.builder()
            .streamUsage(true)
            .internalToolExecutionEnabled(false)
            .build();

    // 使用 ChatClient.Builder 设置默认选项
    this.chatClient = ChatClient.builder(chatModel)
            .defaultOptions(this.chatOptions)
            .defaultTools(tools)
            .build();
}
```

**优点**：配置一次，所有使用该 `ChatClient` 的调用都会自动包含 Token 统计。

### 方式三：结合其他配置

`streamUsage` 配置可以与其他 OpenAI 配置项结合使用：

```java
OpenAiChatOptions options = OpenAiChatOptions.builder()
        .streamUsage(true)                    // 启用 Token 统计
        .model("gpt-4")                        // 指定模型
        .temperature(0.7)                      // 设置温度
        .internalToolExecutionEnabled(false)   // 禁用内部工具执行
        .build();
```

---

## 四、Usage 数据处理

配置 `streamUsage(true)` 后，需要注意 Usage 数据的接收时机和处理方式。

### 4.1 Usage 的返回时机

> **重要**：OpenAI 不会在每一个流式 chunk 中都返回 Usage 数据。

通常，Usage 只在流式传输的**最后一个或几个响应帧**中返回，包含整个请求的完整 Token 消耗统计。

因此，在处理流式响应时，需要进行空值检查和有效值判断：

```java
// 正确的 Usage 处理方式
Usage usageObj = chatResponse.getMetadata().getUsage();

// 只有当有实际 Token 消耗时才赋值
if (usageObj != null
    && usageObj.getTotalTokens() != null
    && usageObj.getTotalTokens() > 0) {
    usage = usageObj;
    logger.info("Token Usage - Prompt: {}, Completion: {}, Total: {}",
        usage.getPromptTokens(),
        usage.getCompletionTokens(),
        usage.getTotalTokens());
}
```

### 4.2 完整的流式处理示例

```java
public Flux<ServerSentEvent<String>> streamChat(@RequestParam String message) {
    return chatTestService.streamChat(message)
        .doOnNext(chatResponse -> {
            Usage usage = chatResponse.getMetadata().getUsage();

            // 只有在最后一个帧中才会有有效的 Usage 数据
            if (usage != null && usage.getTotalTokens() > 0) {
                log.info("Input Tokens: {}", usage.getPromptTokens());
                log.info("Output Tokens: {}", usage.getCompletionTokens());
                log.info("Total Tokens: {}", usage.getTotalTokens());
            }
        })
        .map(chatResponse -> chatResponse.getResult().getOutput().getText())
        .map(chunk -> ServerSentEvent.<String>builder()
            .data(chunk)
            .build());
}
```

---

## 五、完整示例项目

以下是一个完整的 Spring Boot Controller 示例，展示了如何在 SSE（Server-Sent Events）场景下正确处理流式响应和 Token 统计：

```java
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatTestController {

    private final ObjectMapper objectMapper;
    private final ChatTestService chatTestService;

    public ChatTestController(ObjectMapper objectMapper,
                             ChatTestService chatTestService) {
        this.objectMapper = objectMapper;
        this.chatTestService = chatTestService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@RequestParam String message) {
        return chatTestService.streamChat(message)
            .doOnNext(chatResponse -> {
                // 打印完整数据帧用于调试
                String fullJson = objectMapper.writeValueAsString(chatResponse);
                System.out.println(">>> 完整原始数据帧: " + fullJson);

                // 提取并打印 Token 使用情况
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
                .build());
    }
}
```

对应的 Service 层实现：

```java
@Service
@Slf4j
public class ChatTestService {

    private final ChatClient chatClient;

    public ChatTestService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public Flux<ChatResponse> streamChat(String message) {
        // 关键配置：启用 streamUsage
        OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
                .streamUsage(true)
                .build();

        return chatClient.prompt()
                .user(message)
                .options(openAiChatOptions)
                .stream()
                .chatResponse();
    }
}
```

---

## 六、总结

| 项目 | 说明 |
|------|------|
| **问题** | Spring AI 流式传输时 Token 使用统计（Usage）丢失 |
| **根因** | OpenAI API 默认不在流式响应中返回 Usage 信息 |
| **解决方法** | 配置 `OpenAiChatOptions.builder().streamUsage(true)` |
| **配置时机** | 可在调用时动态配置，也可在 ChatClient 构建时设置为默认配置 |
| **数据接收** | Usage 通常只在流式传输的最后几个帧中返回 |
| **处理建议** | 进行空值检查和有效值判断（`totalTokens > 0`） |

---

## 七、技术要点回顾

1. **一行配置解决核心问题**：`.streamUsage(true)`

2. **Usage 返回时机**：只在流式传输的最后几个帧中返回

3. **空值处理**：务必检查 `usage != null && totalTokens > 0`

4. **推荐做法**：在 ChatClient 构建时设置为默认配置，避免遗漏

通过配置 `streamUsage(true)`，我们可以在不牺牲流式传输实时体验的同时，获得完整的 Token 使用统计，这对于成本控制、性能监控和用户计费都非常重要。

---

> **作者**：tianshuo
>
> **原文链接**：[Hello-Agent 项目源码](https://github.com/your-repo/hello-agent)
>
> **Spring AI 版本**：1.0.0+
>
> **OpenAI API 版本**：2024+
