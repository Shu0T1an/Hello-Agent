package cn.ts.web.agent.deepsearch;

import cn.ts.agent.core.ReactAgent;
import cn.ts.agent.extension.interceptor.RuntimeContextPromptInterceptor;
import cn.ts.agent.extension.interceptor.SubAgentInterceptor;
import cn.ts.agent.extension.interceptor.ToolPolicyInterceptor;
import cn.ts.agent.extension.tools.TaskTool;
import cn.ts.agent.hook.ClarificationQaHook;
import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.agent.interceptor.ModelInvocationContext;
import cn.ts.agent.interceptor.ModelInvocationResult;
import cn.ts.agent.interceptor.ModelInvoker;
import cn.ts.agent.model.ChatModelRequest;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.hook.Hook;
import cn.ts.web.agent.interceptor.PromptAuditInterceptor;
import cn.ts.web.agent.service.SubAgentProgressBus;
import cn.ts.web.shared.config.AgentExecutionConfig;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Builds the built-in DeepSearch runtime agent.
 */
@Component
public class DeepSearchAgentBuilder {

    private final DeepSearchProperties properties;
    private final CheckpointManager checkpointManager;
    private final SubAgentProgressBus subAgentProgressBus;
    private final SubAgentToolPolicyResolver toolPolicyResolver;
    private final PromptAuditInterceptor promptAuditInterceptor;
    private final RuntimeContextPromptInterceptor runtimeContextPromptInterceptor;

    public DeepSearchAgentBuilder(DeepSearchProperties properties,
                                  CheckpointManager checkpointManager,
                                  SubAgentProgressBus subAgentProgressBus,
                                  AgentExecutionConfig executionConfig,
                                  PromptAuditInterceptor promptAuditInterceptor) {
        this.properties = properties;
        this.checkpointManager = checkpointManager;
        this.subAgentProgressBus = subAgentProgressBus;
        this.promptAuditInterceptor = promptAuditInterceptor;
        this.toolPolicyResolver = new SubAgentToolPolicyResolver(properties);
        this.runtimeContextPromptInterceptor = createRuntimeContextPromptInterceptor(executionConfig);
    }

    public ReactAgent build(ChatModel chatModel, Object[] tools) {
        String agentName = properties.getAgentName();
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("agent.deep-search.agent-name must not be blank");
        }
        if (chatModel == null) {
            throw new IllegalArgumentException("chatModel must not be null");
        }

        Object[] safeTools = tools != null ? tools : new Object[0];

        Map<String, ReactAgent> subAgents = buildSubAgents(chatModel, safeTools);
        Object[] toolsWithTask = appendTaskTool(safeTools, subAgents);
        String mainPrompt = DeepSearchPrompts.mainPrompt(properties.getMaxIterations());
        SubAgentInterceptor subAgentInterceptor = new SubAgentInterceptor(mainPrompt, subAgents, subAgentProgressBus);

        return ReactAgent.builder()
                .name(agentName)
                .description(resolveDescription())
                .chatModel(chatModel)
                .streaming(properties.isStreamEnabled())
                .tools(toolsWithTask)
                .hooks(buildGlobalHooks())
                .modelInterceptors(withRuntimeContext(
                        subAgentInterceptor,
                        new ToolPolicyInterceptor(
                                "DeepSearchMainToolPolicy",
                                List.of(),
                                toolPolicyResolver.blockedToolNames()
                        ),
                        promptAuditInterceptor
                ))
                .checkpointManager(checkpointManager)
                .build();
    }

    private Object[] appendTaskTool(Object[] tools, Map<String, ReactAgent> subAgents) {
        Object[] merged = new Object[tools.length + 1];
        System.arraycopy(tools, 0, merged, 0, tools.length);
        merged[tools.length] = new TaskTool(subAgents, subAgentProgressBus, properties.getMaxParallelSubagents());
        return merged;
    }

    private String resolveDescription() {
        if (properties.getDescription() != null && !properties.getDescription().isBlank()) {
            return properties.getDescription();
        }
        return properties.getDisplayName();
    }

    private Map<String, ReactAgent> buildSubAgents(ChatModel chatModel, Object[] tools) {
        Map<String, ReactAgent> subAgents = new LinkedHashMap<>();

        ReactAgent researchAgent = ReactAgent.builder()
                .name("research-agent")
                .description("Used for deep, topic-specific research tasks.")
                .chatModel(chatModel)
                .streaming(false)
                .tools(tools)
                .hooks(buildGlobalHooks())
                .modelInterceptors(withRuntimeContext(new PromptInjectingInterceptor(
                        "DeepSearchResearchPrompt",
                        DeepSearchPrompts.RESEARCH_SUBAGENT_PROMPT
                ), toolPolicyResolver.forResearchAgent(), promptAuditInterceptor))
                .build();
        subAgents.put("research-agent", researchAgent);

        ReactAgent critiqueAgent = ReactAgent.builder()
                .name("critique-agent")
                .description("Used to review and critique drafts with improvement suggestions.")
                .chatModel(chatModel)
                .streaming(false)
                .tools(tools)
                .hooks(buildGlobalHooks())
                .modelInterceptors(withRuntimeContext(new PromptInjectingInterceptor(
                        "DeepSearchCritiquePrompt",
                        DeepSearchPrompts.CRITIQUE_SUBAGENT_PROMPT
                ), toolPolicyResolver.forCritiqueAgent(), promptAuditInterceptor))
                .build();
        subAgents.put("critique-agent", critiqueAgent);

        if (properties.isIncludeGeneralPurposeSubagent()) {
            ReactAgent generalPurposeAgent = ReactAgent.builder()
                    .name("general-purpose")
                    .description("General-purpose subagent for complex isolated tasks.")
                    .chatModel(chatModel)
                    .streaming(false)
                    .tools(tools)
                    .hooks(buildGlobalHooks())
                    .modelInterceptors(withRuntimeContext(new PromptInjectingInterceptor(
                            "DeepSearchGeneralPrompt",
                            DeepSearchPrompts.GENERAL_PURPOSE_SUBAGENT_PROMPT
                    ), toolPolicyResolver.forGeneralAgent(), promptAuditInterceptor))
                    .build();
            subAgents.put("general-purpose", generalPurposeAgent);
        }

        return subAgents;
    }

    private List<Hook> buildGlobalHooks() {
        return List.of(ClarificationQaHook.builder().build());
    }

    private List<ModelInterceptor> withRuntimeContext(ModelInterceptor... interceptors) {
        List<ModelInterceptor> all = new ArrayList<>();
        if (runtimeContextPromptInterceptor.isEnabled()) {
            all.add(runtimeContextPromptInterceptor);
        }
        all.addAll(List.of(interceptors));
        return List.copyOf(all);
    }

    private RuntimeContextPromptInterceptor createRuntimeContextPromptInterceptor(AgentExecutionConfig executionConfig) {
        AgentExecutionConfig.ContextPromptConfig contextPromptConfig = executionConfig != null
                ? executionConfig.getContextPrompt()
                : null;
        boolean enabled = contextPromptConfig == null || contextPromptConfig.isEnabled();
        boolean includeTime = contextPromptConfig == null || contextPromptConfig.isIncludeTime();
        boolean includeCapabilities = contextPromptConfig == null || contextPromptConfig.isIncludeCapabilities();
        return new RuntimeContextPromptInterceptor(enabled, includeTime, includeCapabilities);
    }

    static class PromptInjectingInterceptor implements ModelInterceptor {

        private final String name;
        private final String prompt;

        PromptInjectingInterceptor(String name, String prompt) {
            this.name = name;
            this.prompt = prompt;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public CompletableFuture<ModelInvocationResult> intercept(ModelInvocationContext context, ModelInvoker next) {
            ChatModelRequest request = context.request();
            String merged = mergePrompt(request.getSystemPrompt(), prompt);
            ChatModelRequest enhanced = ChatModelRequest.builder(request.getState())
                    .systemPrompt(merged)
                    .baseOptions(request.getBaseOptions())
                    .toolCallbacks(request.getToolCallbacks())
                    .build();
            return next.proceed(context.withRequest(enhanced));
        }

        private String mergePrompt(String existing, String extra) {
            if (existing == null || existing.isBlank()) {
                return extra;
            }
            return existing + "\n\n" + extra;
        }
    }
}
