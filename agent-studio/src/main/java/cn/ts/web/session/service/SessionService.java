package cn.ts.web.session.service;

import cn.ts.graph.checkpoint.StateSnapshot;
import cn.ts.web.shared.constant.SessionConstants;
import cn.ts.web.session.dto.SessionDTO;
import cn.ts.web.session.dto.SessionDetailDTO;
import cn.ts.web.session.entity.SessionEntity;
import cn.ts.web.mapper.CheckpointMapper;
import cn.ts.web.session.mapper.SessionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 会话管理服务（Session + Checkpoint）。
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionMapper sessionMapper;
    @SuppressWarnings("unused")
    private final CheckpointMapper checkpointMapper;
    private final ObjectMapper objectMapper;
    private final SessionCheckpointFacade checkpointFacade;
    private final SessionMessageAssembler messageAssembler;
    private final SessionStateAccessor stateAccessor;

    public SessionService(
            SessionMapper sessionMapper,
            CheckpointMapper checkpointMapper,
            ObjectMapper objectMapper,
            SessionCheckpointFacade checkpointFacade,
            SessionMessageAssembler messageAssembler,
            SessionStateAccessor stateAccessor) {
        this.sessionMapper = sessionMapper;
        this.checkpointMapper = checkpointMapper;
        this.objectMapper = objectMapper;
        this.checkpointFacade = checkpointFacade;
        this.messageAssembler = messageAssembler;
        this.stateAccessor = stateAccessor;
    }

    public List<SessionDTO> getAllSessions() {
        return sessionMapper.selectActiveSessions().stream()
                .map(this::toSessionDTO)
                .toList();
    }

    public Optional<SessionDetailDTO> getSession(String sessionId) {
        SessionEntity session = sessionMapper.selectBySessionId(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        StateSnapshot latestSnapshot = checkpointFacade.latest(sessionId).orElse(null);
        return Optional.of(buildSessionDetailDTO(session, latestSnapshot));
    }

    @Transactional
    public SessionDetailDTO createSession(String agentName, String title) {
        String sessionId = UUID.randomUUID().toString();
        String normalizedTitle = (title != null && !title.isEmpty()) ? title : SessionConstants.DEFAULT_SESSION_TITLE;

        SessionEntity session = new SessionEntity()
                .setSessionId(sessionId)
                .setTitle(normalizedTitle)
                .setCurrentAgent(agentName)
                .setStatus(SessionConstants.STATUS_ACTIVE)
                .setAgentSwitchHistory("[]")
                .setCreatedAt(Instant.now())
                .setUpdatedAt(Instant.now());
        sessionMapper.insert(session);

        checkpointFacade.saveInitial(sessionId, normalizedTitle, agentName);

        log.info("Created new session {} with agent {}", sessionId, agentName);
        StateSnapshot latest = checkpointFacade.latest(sessionId).orElse(null);
        return buildSessionDetailDTO(session, latest);
    }

    @Transactional
    public void updateSession(String sessionId, String title) {
        sessionMapper.updateTitle(sessionId, title);
        log.debug("Updated session {} title to {}", sessionId, title);
    }

    @Transactional
    public void switchAgent(String sessionId, String newAgentName) {
        SessionEntity session = sessionMapper.selectBySessionId(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        List<String> history = parseAgentHistory(session.getAgentSwitchHistory());
        history.add(session.getCurrentAgent());
        sessionMapper.updateAgent(sessionId, newAgentName, serializeAgentHistory(history));

        checkpointFacade.latest(sessionId)
                .ifPresent(latest -> checkpointFacade.saveAfterAgentSwitch(sessionId, newAgentName, latest));

        log.info("Switched agent for session {} from {} to {}", sessionId, session.getCurrentAgent(), newAgentName);
    }

    @Transactional
    public void addMessage(String sessionId, String role, String content) {
        Optional<StateSnapshot> latestOpt = checkpointFacade.latest(sessionId);
        if (latestOpt.isEmpty()) {
            log.warn("Session {} not found for adding message", sessionId);
            return;
        }
        checkpointFacade.saveAfterMessageAppend(sessionId, role, content, latestOpt.get());
        sessionMapper.updateTimestamp(sessionId);
    }

    /**
     * @deprecated 由节点检查点自动保存消息，优先使用自动机制。
     */
    @Deprecated(forRemoval = true)
    @Transactional
    public void addMessageIfNotExists(String sessionId, String role, String content) {
        Optional<StateSnapshot> latestOpt = checkpointFacade.latest(sessionId);
        if (latestOpt.isEmpty()) {
            log.warn("Session {} not found for adding message", sessionId);
            return;
        }
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> messages = (List<java.util.Map<String, Object>>) latestOpt.get()
                .getState()
                .getOrDefault("messages", new ArrayList<>());
        if (!messages.isEmpty()) {
            java.util.Map<String, Object> lastMessage = messages.get(messages.size() - 1);
            String lastRole = (String) lastMessage.get("role");
            String lastContent = (String) lastMessage.get("content");
            if (role.equals(lastRole) && content.equals(lastContent)) {
                log.debug("Message already exists, skipping: role={}, content={}", role, content);
                return;
            }
        }
        addMessage(sessionId, role, content);
    }

    @Transactional
    public boolean deleteSession(String sessionId) {
        sessionMapper.softDelete(sessionId);
        checkpointFacade.deleteThread(sessionId);
        log.info("Deleted session {}", sessionId);
        return true;
    }

    public boolean sessionExists(String sessionId) {
        SessionEntity session = sessionMapper.selectBySessionId(sessionId);
        return session != null && SessionConstants.STATUS_ACTIVE.equals(session.getStatus());
    }

    public int getSessionCount() {
        return sessionMapper.selectActiveSessions().size();
    }

    @Transactional
    public int deleteAllSessions() {
        List<SessionEntity> sessions = sessionMapper.selectActiveSessions();
        int count = 0;
        for (SessionEntity session : sessions) {
            sessionMapper.softDelete(session.getSessionId());
            checkpointFacade.deleteThread(session.getSessionId());
            count++;
        }
        log.info("Deleted {} sessions", count);
        return count;
    }

    private SessionDTO toSessionDTO(SessionEntity entity) {
        int messageCount = checkpointFacade.latest(entity.getSessionId())
                .map(stateAccessor::messageCount)
                .orElse(0);
        return new SessionDTO(
                entity.getSessionId(),
                entity.getTitle(),
                entity.getCurrentAgent(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                messageCount
        );
    }

    private SessionDetailDTO buildSessionDetailDTO(SessionEntity session, StateSnapshot snapshot) {
        SessionDetailDTO dto = new SessionDetailDTO();
        dto.setId(session.getSessionId());
        dto.setTitle(session.getTitle());
        dto.setAgentName(session.getCurrentAgent());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setUpdatedAt(session.getUpdatedAt());

        List<SessionDetailDTO.SessionMessage> messages = messageAssembler.fromSnapshot(snapshot);
        dto.setMessages(messages);
        dto.setMessageCount(messages.size());
        return dto;
    }

    private List<String> parseAgentHistory(String historyJson) {
        if (historyJson == null || historyJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(historyJson, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse agent history: {}", historyJson, e);
            return new ArrayList<>();
        }
    }

    private String serializeAgentHistory(List<String> history) {
        try {
            return objectMapper.writeValueAsString(history);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize agent history: {}", history, e);
            return "[]";
        }
    }
}
