package cn.ts.web.memory.interceptor;

import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.agent.interceptor.ModelInvocationContext;
import cn.ts.agent.interceptor.ModelInvocationResult;
import cn.ts.agent.interceptor.ModelInvoker;
import cn.ts.agent.model.ChatModelRequest;
import cn.ts.web.memory.config.MemoryProperties;
import cn.ts.web.memory.service.MemoryService;
import cn.ts.web.memory.spi.MemoryPayload;
import cn.ts.web.memory.spi.MemoryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
public class MemoryPromptInterceptor implements ModelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(MemoryPromptInterceptor.class);

    private final MemoryProperties properties;
    private final MemoryService memoryService;

    public MemoryPromptInterceptor(MemoryProperties properties, MemoryService memoryService) {
        this.properties = properties;
        this.memoryService = memoryService;
    }

    @Override
    public String getName() {
        return "MemoryPrompt";
    }

    @Override
    public int getOrder() {
        return -175;
    }

    @Override
    public CompletableFuture<ModelInvocationResult> intercept(ModelInvocationContext context, ModelInvoker next) {
        if (!properties.isEnabled()) {
            return next.proceed(context);
        }

        try {
            MemoryRequest request = new MemoryRequest(
                    context.config().threadId(),
                    context.config().executionId()
            );
            Optional<MemoryPayload> payloadOpt = memoryService.loadForInvocation(request);
            if (payloadOpt.isEmpty()) {
                return next.proceed(context);
            }

            ChatModelRequest original = context.request();
            String mergedPrompt = mergePrompt(original.getSystemPrompt(), render(payloadOpt.get()));

            ChatModelRequest enhanced = ChatModelRequest.builder(original.getState())
                    .systemPrompt(mergedPrompt)
                    .baseOptions(original.getBaseOptions())
                    .toolCallbacks(original.getToolCallbacks())
                    .build();

            return next.proceed(context.withRequest(enhanced));
        } catch (Exception ex) {
            if (properties.isFailOpen()) {
                logger.warn("Memory prompt injection skipped due to error: {}", ex.getMessage());
                return next.proceed(context);
            }
            return CompletableFuture.failedFuture(ex);
        }
    }

    private String render(MemoryPayload payload) {
        if (!properties.isInjectHeader()) {
            return payload.content();
        }
        StringBuilder builder = new StringBuilder();
        builder.append("## Memory Rules (Auto-Loaded)\n");
        builder.append("- Source: ").append(payload.source()).append("\n");
        if (payload.truncated()) {
            builder.append("- Note: memory content was truncated by maxChars.\n");
        }
        builder.append(payload.content());
        return builder.toString();
    }

    private String mergePrompt(String existing, String extra) {
        if (existing == null || existing.isBlank()) {
            return extra;
        }
        return existing + "\n\n" + extra;
    }
}
