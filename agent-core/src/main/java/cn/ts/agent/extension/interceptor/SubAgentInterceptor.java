package cn.ts.agent.extension.interceptor;

import cn.ts.agent.core.ReactAgent;
import cn.ts.agent.extension.progress.SubAgentProgressReporter;
import cn.ts.agent.extension.tools.TaskTool;
import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.agent.interceptor.ModelInvocationContext;
import cn.ts.agent.interceptor.ModelInvocationResult;
import cn.ts.agent.interceptor.ModelInvoker;
import cn.ts.agent.model.ChatModelRequest;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Injects subagent prompt guidance and task delegation tool into model request.
 */
public class SubAgentInterceptor implements ModelInterceptor {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            ## task tool (subagent spawner)
            You can use the `task` tool to delegate complex, isolated tasks to a subagent.
            Use `task` when a task is independent, multi-step, or context-heavy.
            Avoid `task` for trivial requests.
            Provide complete context in `description`.
            """;

    private static final String TASK_TOOL_DESCRIPTION_TEMPLATE = """
            Delegate an isolated task to a subagent.
            Available subagent types:
            %s
            Input must include:
            - description: full task details
            - subagent_type: one of the available subagent types
            """;

    private final String systemPrompt;
    private final Map<String, ReactAgent> subAgents;
    private final TaskTool taskTool;

    public SubAgentInterceptor(String systemPrompt, Map<String, ReactAgent> subAgents) {
        this(systemPrompt, subAgents, SubAgentProgressReporter.noop());
    }

    public SubAgentInterceptor(
            String systemPrompt,
            Map<String, ReactAgent> subAgents,
            SubAgentProgressReporter progressReporter) {
        this.systemPrompt = (systemPrompt == null || systemPrompt.isBlank()) ? DEFAULT_SYSTEM_PROMPT : systemPrompt;
        this.subAgents = new LinkedHashMap<>(subAgents != null ? subAgents : Map.of());
        this.taskTool = new TaskTool(this.subAgents, progressReporter);
    }

    @Override
    public String getName() {
        return "SubAgent";
    }

    @Override
    public CompletableFuture<ModelInvocationResult> intercept(
            ModelInvocationContext context,
            ModelInvoker next) {
        ChatModelRequest oldRequest = context.request();

        String mergedSystemPrompt = mergeSystemPrompt(oldRequest.getSystemPrompt(), systemPrompt);
        List<ToolCallback> mergedTools = mergeTools(oldRequest.getToolCallbacks(), taskTool);

        ChatModelRequest enhancedRequest = ChatModelRequest.builder(oldRequest.getState())
                .systemPrompt(mergedSystemPrompt)
                .baseOptions(oldRequest.getBaseOptions())
                .toolCallbacks(mergedTools)
                .build();

        ModelInvocationContext enhancedContext = context.withRequest(enhancedRequest)
                .withAttribute("subagent_types", List.copyOf(subAgents.keySet()));
        return next.proceed(enhancedContext);
    }

    private String mergeSystemPrompt(String existing, String extra) {
        String taskToolDescription = buildTaskToolDescription();
        if (existing == null || existing.isBlank()) {
            return extra + "\n\n" + taskToolDescription;
        }
        return existing + "\n\n" + extra + "\n\n" + taskToolDescription;
    }

    private String buildTaskToolDescription() {
        StringBuilder available = new StringBuilder();
        if (subAgents.isEmpty()) {
            available.append("- none");
        } else {
            subAgents.forEach((type, agent) -> {
                String desc = agent != null && agent.getDescription() != null
                        ? agent.getDescription()
                        : "No description";
                available.append("- ").append(type).append(": ").append(desc).append('\n');
            });
        }
        return TASK_TOOL_DESCRIPTION_TEMPLATE.formatted(available);
    }

    private List<ToolCallback> mergeTools(List<ToolCallback> existing, TaskTool taskTool) {
        List<ToolCallback> merged = new ArrayList<>(existing != null ? existing : List.of());

        boolean hasTask = merged.stream().anyMatch(tc -> "task".equals(tc.getToolDefinition().name()));
        if (!hasTask) {
            // taskTool is exposed through @Tool and will be converted by ToolUtils in LLMNode builder path.
            // Here we rely on ChatModelRequest receiving ToolCallback list; inject via callback conversion in caller.
            ToolCallback callback = org.springframework.ai.tool.method.MethodToolCallbackProvider.builder()
                    .toolObjects(taskTool)
                    .build()
                    .getToolCallbacks()[0];
            merged.add(callback);
        }
        return merged;
    }
}
