package cn.ts.web.agent.interceptor;

import cn.ts.agent.constant.StateKeys;
import cn.ts.agent.constant.AgentConstants;
import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.agent.interceptor.ModelInvocationContext;
import cn.ts.agent.interceptor.ModelInvocationResult;
import cn.ts.agent.interceptor.ModelInvoker;
import cn.ts.agent.model.ChatModelRequest;
import cn.ts.graph.flux.GraphFlux;
import cn.ts.web.infra.audit.entity.LlmPromptAuditEntity;
import cn.ts.web.infra.audit.service.LlmPromptAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import reactor.core.publisher.Flux;

/**
 * Captures model request/response/error at business layer and persists audit logs.
 */
@Component
public class PromptAuditInterceptor implements ModelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(PromptAuditInterceptor.class);

    static final String PHASE_REQUEST = "REQUEST";
    static final String PHASE_RESPONSE = "RESPONSE";
    static final String PHASE_ERROR = "ERROR";

    @Resource
    private LlmPromptAuditService auditService;

    @Resource
    private ObjectMapper objectMapper;

    @Value("${agent.audit.enabled:true}")
    private boolean enabled;

    @Value("${agent.audit.include-response:true}")
    private boolean includeResponse;

    @Value("${agent.audit.max-body-size:32768}")
    private int maxBodySize;

    public PromptAuditInterceptor() {
    }

    PromptAuditInterceptor(
            LlmPromptAuditService auditService,
            boolean enabled,
            boolean includeResponse,
            int maxBodySize) {
        this.auditService = auditService;
        this.objectMapper = new ObjectMapper();
        this.enabled = enabled;
        this.includeResponse = includeResponse;
        this.maxBodySize = Math.max(1024, maxBodySize);
    }

    @PostConstruct
    void init() {
        this.maxBodySize = Math.max(1024, this.maxBodySize);
    }

    @Override
    public String getName() {
        return "PromptAudit";
    }

    @Override
    public int getOrder() {
        return 1000;
    }

    @Override
    public CompletableFuture<ModelInvocationResult> intercept(ModelInvocationContext context, ModelInvoker next) {
        if (!enabled) {
            return next.proceed(context);
        }

        String traceId = UUID.randomUUID().toString();
        writeRequestAudit(context, traceId);

        return next.proceed(context)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        Throwable cause = unwrap(throwable);
                        writeErrorAudit(context, traceId, cause);
                        throw asRuntime(cause);
                    }
                    if (includeResponse) {
                        return attachStreamAuditIfNeeded(context, traceId, result);
                    }
                    return result;
                });
    }

    private ModelInvocationResult attachStreamAuditIfNeeded(
            ModelInvocationContext context,
            String traceId,
            ModelInvocationResult result) {
        Map<String, Object> updates = result != null ? result.updates() : Map.of();
        Object streamValue = updates.get(AgentConstants.OutputTypes.LLM_STREAM);
        if (!(streamValue instanceof GraphFlux<?> graphFlux)) {
            writeResponseAudit(context, traceId, updates, null, null);
            return result;
        }

        AtomicBoolean finalized = new AtomicBoolean(false);
        AtomicInteger chunkCount = new AtomicInteger(0);
        StringBuilder fullTextBuilder = new StringBuilder();
        Flux<?> sourceStream = graphFlux.getStream();
        Flux<?> auditedStream = sourceStream
                .doOnNext(chunk -> {
                    if (!(chunk instanceof ChatResponse response)) {
                        return;
                    }
                    if (response.getResult() == null || response.getResult().getOutput() == null) {
                        return;
                    }
                    String text = response.getResult().getOutput().getText();
                    if (text == null || text.isEmpty()) {
                        return;
                    }
                    fullTextBuilder.append(text);
                    chunkCount.incrementAndGet();
                })
                .doOnComplete(() -> {
                    if (!finalized.compareAndSet(false, true)) {
                        return;
                    }
                    writeResponseAudit(context, traceId, updates, fullTextBuilder.toString(), chunkCount.get());
                })
                .doOnError(error -> {
                    if (!finalized.compareAndSet(false, true)) {
                        return;
                    }
                    writeErrorAudit(context, traceId, unwrap(error));
                });

        Map<String, Object> updated = new LinkedHashMap<>(updates);
        updated.put(
                AgentConstants.OutputTypes.LLM_STREAM,
                GraphFlux.of(graphFlux.getNodeName(), auditedStream));
        return ModelInvocationResult.of(updated);
    }

    private void writeRequestAudit(ModelInvocationContext context, String traceId) {
        ChatModelRequest request = context.request();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("streaming", context.streaming());
        payload.put("systemPrompt", request.getSystemPrompt());
        payload.put("messages", summarizeMessages(request.getMessages()));
        payload.put("toolNames", summarizeToolNames(request.getToolCallbacks()));
        payload.put("baseOptions", summarizeObject(request.getBaseOptions()));
        save(buildBaseEntity(context, traceId, PHASE_REQUEST)
                .setRequestJson(toJson(payload)));
    }

    private void writeResponseAudit(
            ModelInvocationContext context,
            String traceId,
            Map<String, Object> updates,
            String fullText,
            Integer chunkCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("updateKeys", updates.keySet());
        payload.put("messages", summarizeMessagesObject(updates.get(StateKeys.MESSAGES)));
        payload.put("chatResponse", summarizeChatResponse(updates.get(StateKeys.CHAT_RESPONSE)));
        payload.put("hasStream", updates.containsKey(AgentConstants.OutputTypes.LLM_STREAM));
        if (fullText != null) {
            payload.put("fullText", truncate(fullText));
        }
        if (chunkCount != null) {
            payload.put("chunkCount", chunkCount);
        }
        save(buildBaseEntity(context, traceId, PHASE_RESPONSE)
                .setResponseJson(toJson(payload)));
    }

    private void writeErrorAudit(
            ModelInvocationContext context,
            String traceId,
            Throwable throwable) {
        save(buildBaseEntity(context, traceId, PHASE_ERROR)
                .setErrorMessage(truncate(throwable == null ? "unknown error" : throwable.toString())));
    }

    private LlmPromptAuditEntity buildBaseEntity(ModelInvocationContext context, String traceId, String phase) {
        String agentName = context.state().value(StateKeys.CURRENT_AGENT).map(Object::toString).orElse("ReactAgent");
        return new LlmPromptAuditEntity()
                .setTraceId(traceId)
                .setSessionId(context.config().threadId())
                .setExecutionId(context.config().executionId())
                .setAgentName(agentName)
                .setPhase(phase)
                .setCreatedAt(Instant.now());
    }

    private List<Map<String, Object>> summarizeMessagesObject(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Message message) {
                messages.add(message);
            }
        }
        return summarizeMessages(messages);
    }

    private List<Map<String, Object>> summarizeMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(messages.size());
        for (Message message : messages) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", message == null ? "UNKNOWN" : message.getClass().getSimpleName());
            row.put("text", message == null ? null : truncate(message.getText()));
            row.put("metadata", message == null ? Map.of() : message.getMetadata());
            result.add(row);
        }
        return result;
    }

    private List<String> summarizeToolNames(List<ToolCallback> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>(callbacks.size());
        for (ToolCallback callback : callbacks) {
            if (callback == null || callback.getToolDefinition() == null || callback.getToolDefinition().name() == null) {
                continue;
            }
            names.add(callback.getToolDefinition().name());
        }
        return names;
    }

    private Object summarizeChatResponse(Object value) {
        if (!(value instanceof ChatResponse response)) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        if (response.getResult() != null && response.getResult().getOutput() != null) {
            summary.put("assistantText", truncate(response.getResult().getOutput().getText()));
            summary.put("assistantMetadata", response.getResult().getOutput().getMetadata());
        }
        summary.put("metadata", summarizeObject(invokeNoArg(response, "getMetadata")));
        return summary;
    }

    private Object summarizeObject(Object source) {
        if (source == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(source, Object.class);
        } catch (Exception e) {
            return truncate(source.toString());
        }
    }

    private Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            logger.warn("Failed to serialize audit payload, fallback to error json: {}", e.getMessage());
            Map<String, Object> fallback = Map.of(
                    "serializationError", e.getMessage(),
                    "payloadType", payload == null ? "null" : payload.getClass().getName()
            );
            try {
                return objectMapper.writeValueAsString(fallback);
            } catch (Exception ignored) {
                return "{\"serializationError\":\"unknown\"}";
            }
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxBodySize) {
            return value;
        }
        return value.substring(0, maxBodySize);
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }

    private RuntimeException asRuntime(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new CompletionException(throwable);
    }

    private void save(LlmPromptAuditEntity entity) {
        try {
            auditService.save(entity);
        } catch (Exception e) {
            logger.warn("Failed to persist prompt audit record, traceId={}", entity.getTraceId(), e);
        }
    }
}
