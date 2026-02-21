package cn.ts.web.agent.deepsearch;

import cn.ts.agent.core.ReactAgent;
import cn.ts.agent.extension.interceptor.SubAgentInterceptor;
import cn.ts.agent.extension.interceptor.ToolPolicyInterceptor;
import cn.ts.agent.extension.tools.TaskTool;
import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.agent.interceptor.ModelInvocationContext;
import cn.ts.agent.interceptor.ModelInvocationResult;
import cn.ts.agent.interceptor.ModelInvoker;
import cn.ts.agent.model.ChatModelRequest;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.web.service.SubAgentProgressBus;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

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

    public DeepSearchAgentBuilder(DeepSearchProperties properties,
                                  CheckpointManager checkpointManager,
                                  SubAgentProgressBus subAgentProgressBus) {
        this.properties = properties;
        this.checkpointManager = checkpointManager;
        this.subAgentProgressBus = subAgentProgressBus;
        this.toolPolicyResolver = new SubAgentToolPolicyResolver(properties);
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
                .modelInterceptors(List.of(
                        subAgentInterceptor,
                        new ToolPolicyInterceptor(
                                "DeepSearchMainToolPolicy",
                                List.of(),
                                toolPolicyResolver.blockedToolNames()
                        )
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
                .modelInterceptors(List.of(new PromptInjectingInterceptor(
                        "DeepSearchResearchPrompt",
                        DeepSearchPrompts.RESEARCH_SUBAGENT_PROMPT
                ), toolPolicyResolver.forResearchAgent()))
                .checkpointManager(checkpointManager)
                .build();
        subAgents.put("research-agent", researchAgent);

        ReactAgent critiqueAgent = ReactAgent.builder()
                .name("critique-agent")
                .description("Used to review and critique drafts with improvement suggestions.")
                .chatModel(chatModel)
                .streaming(false)
                .tools(tools)
                .modelInterceptors(List.of(new PromptInjectingInterceptor(
                        "DeepSearchCritiquePrompt",
                        DeepSearchPrompts.CRITIQUE_SUBAGENT_PROMPT
                ), toolPolicyResolver.forCritiqueAgent()))
                .checkpointManager(checkpointManager)
                .build();
        subAgents.put("critique-agent", critiqueAgent);

        if (properties.isIncludeGeneralPurposeSubagent()) {
            ReactAgent generalPurposeAgent = ReactAgent.builder()
                    .name("general-purpose")
                    .description("General-purpose subagent for complex isolated tasks.")
                    .chatModel(chatModel)
                    .streaming(false)
                    .tools(tools)
                    .modelInterceptors(List.of(new PromptInjectingInterceptor(
                            "DeepSearchGeneralPrompt",
                            DeepSearchPrompts.GENERAL_PURPOSE_SUBAGENT_PROMPT
                    ), toolPolicyResolver.forGeneralAgent()))
                    .checkpointManager(checkpointManager)
                    .build();
            subAgents.put("general-purpose", generalPurposeAgent);
        }

        return subAgents;
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
