package cn.ts.agent.extension.interceptor;

import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.agent.interceptor.ModelInvocationContext;
import cn.ts.agent.interceptor.ModelInvocationResult;
import cn.ts.agent.interceptor.ModelInvoker;
import cn.ts.agent.model.ChatModelRequest;
import org.springframework.ai.tool.ToolCallback;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Injects runtime context into the model system prompt.
 */
public class RuntimeContextPromptInterceptor implements ModelInterceptor {

    private static final String INTERCEPTOR_NAME = "RuntimeContextPrompt";

    private final boolean enabled;
    private final boolean includeTime;
    private final boolean includeCapabilities;
    private final Clock clock;
    private final ZoneId zoneId;

    public RuntimeContextPromptInterceptor(
            boolean enabled,
            boolean includeTime,
            boolean includeCapabilities) {
        this(enabled, includeTime, includeCapabilities, Clock.systemDefaultZone(), ZoneId.systemDefault());
    }

    public RuntimeContextPromptInterceptor(
            boolean enabled,
            boolean includeTime,
            boolean includeCapabilities,
            Clock clock,
            ZoneId zoneId) {
        this.enabled = enabled;
        this.includeTime = includeTime;
        this.includeCapabilities = includeCapabilities;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
        this.zoneId = zoneId != null ? zoneId : ZoneId.systemDefault();
    }

    @Override
    public String getName() {
        return INTERCEPTOR_NAME;
    }

    @Override
    public int getOrder() {
        return -200;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public CompletableFuture<ModelInvocationResult> intercept(
            ModelInvocationContext context,
            ModelInvoker next) {
        if (!enabled) {
            return next.proceed(context);
        }

        ChatModelRequest request = context.request();
        String runtimeContextPrompt = buildRuntimeContextPrompt(context, request);
        if (runtimeContextPrompt == null || runtimeContextPrompt.isBlank()) {
            return next.proceed(context);
        }

        String mergedPrompt = mergePrompt(request.getSystemPrompt(), runtimeContextPrompt);
        ChatModelRequest enhancedRequest = ChatModelRequest.builder(request.getState())
                .systemPrompt(mergedPrompt)
                .baseOptions(request.getBaseOptions())
                .toolCallbacks(request.getToolCallbacks())
                .build();

        return next.proceed(context.withRequest(enhancedRequest));
    }

    private String buildRuntimeContextPrompt(ModelInvocationContext context, ChatModelRequest request) {
        List<String> sections = new ArrayList<>();
        if (includeTime) {
            sections.add(buildTimeSection());
        }
        if (includeCapabilities) {
            sections.add(buildCapabilitySection(context, request));
        }
        if (sections.isEmpty()) {
            return "";
        }
        return "## Runtime Context (Auto-Generated)\n"
                + String.join("\n", sections)
                + "\n- Treat this context as execution metadata for this request.";
    }

    private String buildTimeSection() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId);
        String nowText = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return "- Current date-time: " + nowText + "\n"
                + "- Server timezone: " + zoneId.getId();
    }

    private String buildCapabilitySection(ModelInvocationContext context, ChatModelRequest request) {
        List<String> categories = new ArrayList<>();
        categories.add("General reasoning and response generation");
        if (context.streaming()) {
            categories.add("Streaming response generation");
        }
        List<ToolCallback> callbacks = request.getToolCallbacks();
        if (callbacks != null && !callbacks.isEmpty()) {
            categories.add("Tool Calling (structured JSON arguments)");
            if (hasTaskTool(callbacks)) {
                categories.add("Subagent Delegation (task.request object)");
            }
        } else {
            categories.add("No external tool access in this invocation");
        }

        StringBuilder builder = new StringBuilder("### Capability Categories");
        for (String category : categories) {
            builder.append("\n- ").append(category);
        }
        return builder.toString();
    }

    private boolean hasTaskTool(List<ToolCallback> callbacks) {
        for (ToolCallback callback : callbacks) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            if ("task".equals(callback.getToolDefinition().name())) {
                return true;
            }
        }
        return false;
    }

    private String mergePrompt(String existing, String runtimeContext) {
        if (existing == null || existing.isBlank()) {
            return runtimeContext;
        }
        return existing + "\n\n" + runtimeContext;
    }
}
