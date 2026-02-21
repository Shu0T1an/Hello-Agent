package cn.ts.agent.extension.interceptor;

import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.agent.interceptor.ModelInvocationContext;
import cn.ts.agent.interceptor.ModelInvocationResult;
import cn.ts.agent.interceptor.ModelInvoker;
import cn.ts.agent.model.ChatModelRequest;
import org.springframework.ai.tool.ToolCallback;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Filters tool callbacks by allow/block policy at model invocation time.
 */
public class ToolPolicyInterceptor implements ModelInterceptor {

    private final String name;
    private final Set<String> allowlistLowerCase;
    private final Set<String> blocklistLowerCase;

    public ToolPolicyInterceptor(String name, List<String> allowlist, List<String> blocklist) {
        this.name = (name == null || name.isBlank()) ? "ToolPolicy" : name;
        this.allowlistLowerCase = normalize(allowlist);
        this.blocklistLowerCase = normalize(blocklist);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public CompletableFuture<ModelInvocationResult> intercept(ModelInvocationContext context, ModelInvoker next) {
        ChatModelRequest request = context.request();
        List<ToolCallback> existing = request.getToolCallbacks();
        if (existing == null || existing.isEmpty()) {
            return next.proceed(context);
        }
        List<ToolCallback> filtered = existing.stream()
                .filter(this::allowed)
                .toList();

        ChatModelRequest updated = ChatModelRequest.builder(request.getState())
                .systemPrompt(request.getSystemPrompt())
                .baseOptions(request.getBaseOptions())
                .toolCallbacks(filtered)
                .build();
        return next.proceed(context.withRequest(updated));
    }

    private boolean allowed(ToolCallback callback) {
        if (callback == null || callback.getToolDefinition() == null || callback.getToolDefinition().name() == null) {
            return false;
        }
        String name = callback.getToolDefinition().name().toLowerCase(Locale.ROOT);
        if (!allowlistLowerCase.isEmpty() && !allowlistLowerCase.contains(name)) {
            return false;
        }
        return !blocklistLowerCase.contains(name);
    }

    private Set<String> normalize(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            result.add(value.toLowerCase(Locale.ROOT));
        }
        return result;
    }
}
