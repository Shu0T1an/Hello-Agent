package cn.ts.web.skills.interceptor;

import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.agent.interceptor.ModelInvocationContext;
import cn.ts.agent.interceptor.ModelInvocationResult;
import cn.ts.agent.interceptor.ModelInvoker;
import cn.ts.agent.model.ChatModelRequest;
import cn.ts.web.skills.config.SkillsProperties;
import cn.ts.web.skills.service.SkillRegistryService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
public class SkillsPromptInterceptor implements ModelInterceptor {

    private static final String NAME = "SkillsPrompt";
    private static final Set<String> SKILL_TOOL_NAMES = Set.of(
            "list_skills",
            "get_skill_detail",
            "get_skill_reference"
    );
    private static final String SKILLS_PROTOCOL_TEMPLATE = """
            ## Skills Protocol
            You can read skill specifications through tools:
            - list_skills: discover available skills by query.
            - get_skill_detail: load one skill's structured detail.
            - get_skill_reference: load a specific referenced file.

            Always follow progressive disclosure:
            1) list first
            2) then detail for selected skill(s)
            3) then reference files only when needed

            Do not load all skills or all references unless strictly necessary.
            """;

    private final SkillsProperties properties;
    private final SkillRegistryService skillRegistryService;

    public SkillsPromptInterceptor(SkillsProperties properties, SkillRegistryService skillRegistryService) {
        this.properties = properties;
        this.skillRegistryService = skillRegistryService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        return -150;
    }

    @Override
    public CompletableFuture<ModelInvocationResult> intercept(
            ModelInvocationContext context,
            ModelInvoker next) {
        if (!properties.isEnabled() || !properties.isPromptInjectionEnabled()) {
            return next.proceed(context);
        }

        ChatModelRequest request = context.request();
        if (!hasSkillReadTools(request.getToolCallbacks())) {
            return next.proceed(context);
        }

        String mergedPrompt = mergePrompt(request.getSystemPrompt(), buildPrompt());
        ChatModelRequest enhanced = ChatModelRequest.builder(request.getState())
                .systemPrompt(mergedPrompt)
                .baseOptions(request.getBaseOptions())
                .toolCallbacks(request.getToolCallbacks())
                .build();

        return next.proceed(context.withRequest(enhanced));
    }

    private boolean hasSkillReadTools(List<ToolCallback> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) {
            return false;
        }
        for (ToolCallback callback : callbacks) {
            if (callback == null || callback.getToolDefinition() == null || callback.getToolDefinition().name() == null) {
                continue;
            }
            if (SKILL_TOOL_NAMES.contains(callback.getToolDefinition().name())) {
                return true;
            }
        }
        return false;
    }

    private String mergePrompt(String existing, String extra) {
        if (existing == null || existing.isBlank()) {
            return extra;
        }
        return existing + "\n\n" + extra;
    }

    private String buildPrompt() {
        int count = skillRegistryService.count();
        return SKILLS_PROTOCOL_TEMPLATE + "\nAvailable skill entries: " + count + ".";
    }
}
