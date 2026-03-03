package cn.ts.web.channel.runtime;

import cn.ts.agent.constant.StateKeys;
import cn.ts.web.agent.service.AgentExecutionService;
import cn.ts.web.channel.dto.ChannelInboundMessage;
import cn.ts.web.agent.dto.AgentResponse;
import cn.ts.web.channel.entity.ChannelSessionMappingEntity;
import cn.ts.web.channel.mapper.ChannelSessionMappingMapper;
import cn.ts.web.session.dto.GraphStateVO;
import cn.ts.web.session.service.GraphStateService;
import cn.ts.web.session.service.MessageConversionService;
import cn.ts.web.session.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component
public class ChannelMessageDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(ChannelMessageDispatcher.class);

    private final AgentExecutionService agentExecutionService;
    private final SessionService sessionService;
    private final ChannelSessionMappingMapper channelSessionMappingMapper;
    private final GraphStateService graphStateService;
    private final MessageConversionService messageConversionService;

    @Value("${agent.channel.default-agent:general-purpose}")
    private String defaultAgent;

    public ChannelMessageDispatcher(AgentExecutionService agentExecutionService,
                                    SessionService sessionService,
                                    ChannelSessionMappingMapper channelSessionMappingMapper,
                                    GraphStateService graphStateService,
                                    MessageConversionService messageConversionService) {
        this.agentExecutionService = agentExecutionService;
        this.sessionService = sessionService;
        this.channelSessionMappingMapper = channelSessionMappingMapper;
        this.graphStateService = graphStateService;
        this.messageConversionService = messageConversionService;
    }

    public void dispatch(ChannelInboundMessage message) {
        dispatch(message, null);
    }

    public void dispatch(ChannelInboundMessage message, Consumer<String> onReply) {
        String agentName = (message.getAgentName() == null || message.getAgentName().isBlank())
                ? defaultAgent
                : message.getAgentName().trim();

        String sessionId = resolveSessionId(message, agentName);
        String resolvedSessionId = sessionId;

        Map<String, Object> state = buildInitialState(resolvedSessionId, message.getText());
        StringBuilder replyBuilder = new StringBuilder();
        AtomicReference<String> fallbackMessage = new AtomicReference<>();

        agentExecutionService.executeAgentStreamWithSession(agentName, state, resolvedSessionId, Duration.ofSeconds(120))
                .subscribe(response -> collectReply(response, replyBuilder, fallbackMessage),
                        error -> {
                            logExecutionError(agentName, resolvedSessionId, error);
                            publishReply(onReply, "执行失败，请稍后重试");
                        },
                        () -> {
                            String reply = replyBuilder.toString().trim();
                            if (reply.isEmpty()) {
                                reply = fallbackMessage.get();
                            }
                            publishReply(onReply, reply);
                        });
    }

    private void collectReply(AgentResponse response,
                              StringBuilder replyBuilder,
                              AtomicReference<String> fallbackMessage) {
        if (response == null || response.getMessage() == null || response.getMessage().isBlank()) {
            return;
        }
        if (response.getMetadata() != null && response.getMetadata().get("chunk") instanceof String) {
            replyBuilder.append(response.getMessage());
            return;
        }
        if (fallbackMessage.get() == null) {
            fallbackMessage.set(response.getMessage());
        }
    }

    private void publishReply(Consumer<String> onReply, String reply) {
        if (onReply == null || reply == null || reply.isBlank()) {
            return;
        }
        try {
            onReply.accept(reply);
        } catch (RuntimeException ignored) {
            // reply callback should never interrupt channel dispatch pipeline
        }
    }

    private void logExecutionError(String agentName, String sessionId, Throwable error) {
        if (error instanceof WebClientResponseException webEx) {
            logger.error(
                    "Channel agent execution failed: agent={}, sessionId={}, status={}, body={}",
                    agentName,
                    sessionId,
                    webEx.getRawStatusCode(),
                    webEx.getResponseBodyAsString(),
                    webEx
            );
            return;
        }
        logger.error("Channel agent execution failed: agent={}, sessionId={}, message={}",
                agentName, sessionId, error.getMessage(), error);
    }

    private String resolveSessionId(ChannelInboundMessage message, String agentName) {
        String externalSessionId = cleanText(message.getChannelSessionId());
        String channelType = cleanText(message.getChannelType());
        if (isBlank(externalSessionId) || isBlank(channelType)) {
            return createInternalSession(agentName);
        }

        ChannelSessionMappingEntity mapping = channelSessionMappingMapper.selectByExternal(channelType, externalSessionId);
        if (mapping != null) {
            String internalSessionId = cleanText(mapping.getInternalSessionId());
            if (!isBlank(internalSessionId) && sessionService.sessionExists(internalSessionId)) {
                return internalSessionId;
            }
            String refreshedSessionId = createInternalSession(agentName);
            channelSessionMappingMapper.updateInternalSessionId(channelType, externalSessionId, refreshedSessionId);
            return refreshedSessionId;
        }

        if (sessionService.sessionExists(externalSessionId)) {
            return externalSessionId;
        }

        String newSessionId = createInternalSession(agentName);
        try {
            ChannelSessionMappingEntity entity = new ChannelSessionMappingEntity();
            entity.setChannelType(channelType);
            entity.setExternalSessionId(externalSessionId);
            entity.setInternalSessionId(newSessionId);
            channelSessionMappingMapper.insert(entity);
            return newSessionId;
        } catch (DuplicateKeyException ex) {
            ChannelSessionMappingEntity existing = channelSessionMappingMapper.selectByExternal(channelType, externalSessionId);
            if (existing != null) {
                String existingSessionId = cleanText(existing.getInternalSessionId());
                if (!isBlank(existingSessionId)) {
                    return existingSessionId;
                }
            }
            return newSessionId;
        }
    }

    private String createInternalSession(String agentName) {
        return sessionService.createSession(agentName, "Channel Session").getId();
    }

    private Map<String, Object> buildInitialState(String sessionId, String userInput) {
        Map<String, Object> state = new HashMap<>();
        try {
            if (graphStateService.sessionExists(sessionId)) {
                GraphStateVO graphState = graphStateService.getGraphState(sessionId);
                if (graphState.getStateData() != null) {
                    state.putAll(graphState.getStateData());
                }
            }
        } catch (RuntimeException e) {
            logger.warn("Failed to load graph state for session {}: {}", sessionId, e.getMessage());
        }

        List<Message> messages = extractMessages(state.get(StateKeys.MESSAGES));
        messages.add(new UserMessage(userInput));
        state.put(StateKeys.MESSAGES, messages);
        state.put(StateKeys.INPUT, userInput);
        return state;
    }

    @SuppressWarnings("unchecked")
    private List<Message> extractMessages(Object messagesObj) {
        if (!(messagesObj instanceof List<?> rawMessages) || rawMessages.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(messageConversionService.convertStateToMessages(rawMessages));
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
