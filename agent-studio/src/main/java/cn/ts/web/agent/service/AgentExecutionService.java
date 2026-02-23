package cn.ts.web.agent.service;

import cn.ts.agent.constant.EventConstants;
import cn.ts.agent.constant.StateKeys;
import cn.ts.graph.*;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.checkpoint.StateSnapshot;
import cn.ts.web.shared.config.AgentExecutionConfig;
import cn.ts.web.shared.constant.ApiConstants;
import cn.ts.web.shared.constant.SessionConstants;
import cn.ts.web.agent.dto.AgentResponse;
import cn.ts.web.session.service.MessageConversionService;
import cn.ts.web.session.service.SessionService;
import cn.ts.web.session.service.TitleGeneratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Agent 执行服务（重构版）
 * <p>
 * 提供流式执行 Agent 的能力，通过 SSE 向前端推送执行事件
 * </p>
 *
 * @author tianshuo
 */
@Service
public class AgentExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(AgentExecutionService.class);

    private final AgentRegistry agentRegistry;
    private final SessionService sessionService;
    private final CheckpointManager checkpointManager;
    private final AgentExecutionConfig config;
    private final ObjectMapper objectMapper;
    private final MessageConversionService messageConversionService;
    private final AgentResponseBuilder responseBuilder;
    private final SubAgentProgressBus subAgentProgressBus;
    private final TitleGeneratorService titleGeneratorService;

    public AgentExecutionService(AgentRegistry agentRegistry,
                                 SessionService sessionService,
                                 CheckpointManager checkpointManager,
                                 AgentExecutionConfig config,
                                 MessageConversionService messageConversionService,
                                 AgentResponseBuilder responseBuilder,
                                 SubAgentProgressBus subAgentProgressBus) {
        this(agentRegistry, sessionService, checkpointManager, config,
                messageConversionService, responseBuilder, subAgentProgressBus, null);
    }

    @Autowired
    public AgentExecutionService(AgentRegistry agentRegistry,
                                 SessionService sessionService,
                                 CheckpointManager checkpointManager,
                                 AgentExecutionConfig config,
                                 MessageConversionService messageConversionService,
                                 AgentResponseBuilder responseBuilder,
                                 SubAgentProgressBus subAgentProgressBus,
                                 TitleGeneratorService titleGeneratorService) {
        this.agentRegistry = agentRegistry;
        this.checkpointManager = checkpointManager;
        this.sessionService = sessionService;
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.messageConversionService = messageConversionService;
        this.responseBuilder = responseBuilder;
        this.subAgentProgressBus = subAgentProgressBus;
        this.titleGeneratorService = titleGeneratorService;
    }

    /**
     * 注册一个图
     *
     * @param agentName Agent 名称
     * @param graph    编译后的图
     */
    public void registerGraph(String agentName, CompiledGraph graph) {
        agentRegistry.register(agentName, graph);
    }

    /**
     * 流式执行 Agent
     *
     * @param agentName    Agent 名称
     * @param initialState 初始状态
     * @return SSE 事件流
     */
    public Flux<AgentResponse> executeAgentStream(String agentName, Map<String, Object> initialState) {
        return executeAgentStreamWithSession(agentName, initialState, "", config.getTimeout());
    }

    /**
     * 流式执行 Agent（支持会话）
     *
     * @param agentName    Agent 名称
     * @param initialState 初始状态
     * @param sessionId    会话ID（用于保持连续对话）
     * @param timeout      超时时间
     * @return SSE 事件流
     */
    public Flux<AgentResponse> executeAgentStreamWithSession(
            String agentName,
            Map<String, Object> initialState,
            String sessionId,
            Duration timeout) {

        CompiledGraph graph = agentRegistry.get(agentName);
        if (graph == null) {
            return Flux.error(new IllegalArgumentException(ApiConstants.ErrorMessages.AGENT_NOT_FOUND + agentName));
        }

        String executionId = UUID.randomUUID().toString();

        // 将 executionId 添加到初始状态中，以便监听器可以获取
        Map<String, Object> stateWithExecutionId = new HashMap<>(initialState);
        stateWithExecutionId.put("executionId", executionId);

        // 构建 RunnableConfig，支持 threadId
        cn.ts.graph.config.RunnableConfig.Builder configBuilder = cn.ts.graph.config.RunnableConfig.builder()
                .executionId(executionId);

        // 如果提供了 sessionId，设置为 threadId 以支持会话管理
        if (sessionId != null && !sessionId.isEmpty()) {
            configBuilder.threadId(sessionId);

            // 如果会话不存在，先创建
            if (!sessionService.sessionExists(sessionId)) {
                sessionService.createSession(agentName, SessionConstants.DEFAULT_SESSION_TITLE);
            }
        }

        Flux<AgentResponse> titleStream = generateTitleEventIfNeeded(sessionId, initialState, executionId);
        Flux<AgentResponse> subAgentStream = subAgentProgressBus.stream(executionId);
        Flux<AgentResponse> graphStream = graph.stream(stateWithExecutionId, configBuilder.timeout(timeout).build())
                .map(response -> responseBuilder.build(response, executionId))
                .onErrorResume(throwable -> Flux.just(responseBuilder.buildErrorResponse(throwable.getMessage(), executionId)))
                .doFinally(signalType -> subAgentProgressBus.complete(executionId));

        return Flux.merge(graphStream, subAgentStream, titleStream);
    }

    /**
     * 保存到会话（已废弃）
     * <p>
     * <b>已废弃：</b>由于状态保存已移至每个节点执行完成后，此方法不再需要。
     * 消息会通过 NodeExecutor 中的检查点机制自动保存。
     * </p>
     *
     * @param sessionId   会话ID
     * @param userInput   用户输入
     * @param fullResponse 完整响应
     * @deprecated 不再需要手动保存到会话，消息通过节点检查点自动保存
     */
    @Deprecated(forRemoval = true)
    private void saveToSession(String sessionId, String userInput, String fullResponse) {
        // 保留空实现以保持二进制兼容性
    }

    private Flux<AgentResponse> generateTitleEventIfNeeded(
            String sessionId,
            Map<String, Object> initialState,
            String executionId) {

        if (titleGeneratorService == null || sessionId == null || sessionId.isEmpty()) {
            return Flux.empty();
        }

        Optional<String> userInputOptional = extractUserInput(initialState);
        if (userInputOptional.isEmpty()) {
            return Flux.empty();
        }

        Optional<String> currentTitleOptional = sessionService.getSession(sessionId)
                .map(session -> session.getTitle());
        if (currentTitleOptional.isEmpty()
                || !SessionConstants.DEFAULT_SESSION_TITLE.equals(currentTitleOptional.get())) {
            return Flux.empty();
        }

        String userInput = userInputOptional.get();
        return Mono.fromCallable(() -> {
                    String generatedTitle = normalizeTitle(titleGeneratorService.generateTitle(userInput), userInput);
                    if (generatedTitle.isEmpty()) {
                        return null;
                    }

                    sessionService.updateSession(sessionId, generatedTitle);
                    logger.info("Auto generated title for session {}: {}", sessionId, generatedTitle);

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("title", generatedTitle);
                    metadata.put("sessionId", sessionId);

                    return AgentResponse.builder()
                            .eventType(EventConstants.TITLE_GENERATED)
                            .timestamp(Instant.now())
                            .executionId(executionId)
                            .metadata(metadata)
                            .build();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    logger.warn("Failed to auto-generate title for session {}", sessionId, error);
                    return Mono.empty();
                })
                .flux()
                .filter(Objects::nonNull);
    }

    private Optional<String> extractUserInput(Map<String, Object> initialState) {
        if (initialState == null || initialState.isEmpty()) {
            return Optional.empty();
        }
        Object input = initialState.get(StateKeys.INPUT);
        if (!(input instanceof String inputText)) {
            return Optional.empty();
        }
        String trimmed = inputText.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }

    private String normalizeTitle(String generatedTitle, String fallbackText) {
        String title = generatedTitle == null ? "" : generatedTitle.trim();
        if (title.isEmpty()) {
            title = fallbackText == null ? "" : fallbackText.trim();
        }
        if (title.isEmpty()) {
            return "";
        }

        title = title.replaceAll("[\\r\\n]+", " ").trim();
        int maxLength = config.getMaxTitleLength();
        if (maxLength > 0 && title.length() > maxLength) {
            title = title.substring(0, maxLength);
        }
        return title;
    }

    /**
     * 使用自定义配置流式执行 Agent
     *
     * @param agentName    Agent 名称
     * @param initialState 初始状态
     * @param timeout      超时时间
     * @return SSE 事件流
     */
    public Flux<AgentResponse> executeAgentStream(
            String agentName,
            Map<String, Object> initialState,
            Duration timeout) {

        CompiledGraph graph = agentRegistry.get(agentName);
        if (graph == null) {
            return Flux.error(new IllegalArgumentException(ApiConstants.ErrorMessages.AGENT_NOT_FOUND + agentName));
        }

        String executionId = UUID.randomUUID().toString();

        // 将 executionId 添加到初始状态中，以便监听器可以获取
        Map<String, Object> stateWithExecutionId = new HashMap<>(initialState);
        stateWithExecutionId.put("executionId", executionId);

        Flux<AgentResponse> subAgentStream = subAgentProgressBus.stream(executionId);
        Flux<AgentResponse> graphStream = graph.stream(stateWithExecutionId,
                        cn.ts.graph.config.RunnableConfig.builder()
                                .executionId(executionId)
                                .timeout(timeout)
                                .build())
                .map(response -> responseBuilder.build(response, executionId))
                .onErrorResume(throwable -> Flux.just(responseBuilder.buildErrorResponse(throwable.getMessage(), executionId)))
                .doFinally(signalType -> subAgentProgressBus.complete(executionId));

        return Flux.merge(graphStream, subAgentStream);
    }

    /**
     * 异步执行 Agent（非流式）
     *
     * @param agentName    Agent 名称
     * @param initialState 初始状态
     * @return CompletableFuture 包含执行结果
     */
    public Mono<GraphResult> executeAgentAsync(String agentName, Map<String, Object> initialState) {
        CompiledGraph graph = agentRegistry.get(agentName);
        if (graph == null) {
            return Mono.error(new IllegalArgumentException("Agent not found: " + agentName));
        }

        return Mono.fromCallable(() -> graph.invoke(initialState));
    }

    /**
     * 恢复执行（从中断处继续）
     * <p>
     * 当用户提交反馈后，从检查点恢复执行
     * </p>
     *
     * @param agentName    Agent 名称
     * @param checkpointId 检查点ID
     * @param feedbackData 反馈数据
     * @param sessionId    会话ID（必需，用于获取检查点）
     * @param timeout      超时时间
     * @return SSE 事件流
     */
    public Flux<AgentResponse> resumeAgentStream(
            String agentName,
            String checkpointId,
            Map<String, Object> feedbackData,
            String sessionId,
            Duration timeout) {

        // 验证 sessionId（必需，因为需要用它来获取检查点）
        if (sessionId == null || sessionId.isEmpty()) {
            return Flux.error(new IllegalArgumentException(ApiConstants.ErrorMessages.SESSION_ID_REQUIRED));
        }

        CompiledGraph graph = agentRegistry.get(agentName);
        if (graph == null) {
            return Flux.error(new IllegalArgumentException(ApiConstants.ErrorMessages.AGENT_NOT_FOUND + agentName));
        }

        String executionId = UUID.randomUUID().toString();

        // 构建 RunnableConfig
        cn.ts.graph.config.RunnableConfig.Builder configBuilder = cn.ts.graph.config.RunnableConfig.builder()
                .executionId(executionId)
                .checkpointId(checkpointId)
                .feedbackData(feedbackData)
                .threadId(sessionId)
                .timeout(timeout);

        // 使用 CheckpointStorage 通过 threadId 和 checkpointId 获取检查点
        Optional<StateSnapshot> stateSnapshot = checkpointManager.getStorage()
                .getCheckpoint(sessionId, checkpointId);
        if (stateSnapshot.isEmpty()){
            return Flux.error(new IllegalArgumentException(
                    ApiConstants.ErrorMessages.CHECKPOINT_NOT_FOUND + checkpointId + " for session: " + sessionId));
        }

        // 从 StateSnapshot 获取 nodeId 作为起始节点
        String resumeNodeId = stateSnapshot.get().getNodeId();

        logger.info("resumeAgentStream: 从检查点 {} 恢复，起始节点: {}", checkpointId, resumeNodeId);

        // 构建 RunnableConfig，设置 startNode
        configBuilder.startNode(resumeNodeId);

        // 获取状态并处理 messages 字段的反序列化
        Map<String, Object> initState = new HashMap<>(stateSnapshot.get().getState());

        // 将 executionId 添加到初始状态中，以便监听器可以获取
        initState.put("executionId", executionId);

        // 处理 messages 字段：将 LinkedHashMap 转换回 Message 对象
        Object messagesObj = initState.get(StateKeys.MESSAGES);
        if (messagesObj instanceof List<?> messagesList) {
            List<Message> deserializedMessages = messageConversionService.convertStateToMessages(messagesList);
            initState.put(StateKeys.MESSAGES, deserializedMessages);
        }

        Flux<AgentResponse> subAgentStream = subAgentProgressBus.stream(executionId);
        Flux<AgentResponse> graphStream = graph.stream(initState, configBuilder.build())
                .map(response -> responseBuilder.build(response, executionId))
                .onErrorResume(throwable -> Flux.just(responseBuilder.buildErrorResponse(throwable.getMessage(), executionId)))
                .doFinally(signalType -> subAgentProgressBus.complete(executionId));

        return Flux.merge(graphStream, subAgentStream);
    }

    /**
     * 检查 Agent 是否已注册
     */
    public boolean isAgentRegistered(String agentName) {
        return agentRegistry.isRegistered(agentName);
    }

    /**
     * 获取所有已注册的 Agent 名称
     */
    public java.util.Set<String> getRegisteredAgents() {
        return agentRegistry.getRegisteredAgentNames();
    }

    /**
     * 注销 Agent
     */
    public void unregisterAgent(String agentName) {
        agentRegistry.unregister(agentName);
    }
}
