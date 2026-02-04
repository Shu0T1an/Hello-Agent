package cn.ts.web.service;

import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.checkpoint.CheckpointMetadata;
import cn.ts.graph.checkpoint.StateSnapshot;
import cn.ts.web.dto.SessionDetailDTO;
import cn.ts.web.dto.SessionDTO;
import cn.ts.web.entity.SessionEntity;
import cn.ts.web.mapper.CheckpointMapper;
import cn.ts.web.mapper.SessionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 会话管理服务（重构版）
 * <p>
 * 基于 Session 和 Checkpoint 分离设计：
 * - Session：管理会话级别的元数据（标题、Agent、状态等）
 * - Checkpoint：管理执行状态快照，用于状态恢复和历史追踪
 * </p>
 *
 * @author tianshuo
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final CheckpointManager checkpointManager;
    private final SessionMapper sessionMapper;
    private final CheckpointMapper checkpointMapper;
    private final ObjectMapper objectMapper;

    public SessionService(
            CheckpointManager checkpointManager,
            SessionMapper sessionMapper,
            CheckpointMapper checkpointMapper,
            ObjectMapper objectMapper) {
        this.checkpointManager = checkpointManager;
        this.sessionMapper = sessionMapper;
        this.checkpointMapper = checkpointMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取所有会话列表
     *
     * @return 会话列表（按更新时间倒序）
     */
    public List<SessionDTO> getAllSessions() {
        return sessionMapper.selectActiveSessions().stream()
                .map(this::toSessionDTO)
                .toList();
    }

    /**
     * 获取会话详情
     *
     * @param sessionId 会话ID
     * @return 会话详情
     */
    public Optional<SessionDetailDTO> getSession(String sessionId) {
        // 1. 获取 Session 记录
        SessionEntity session = sessionMapper.selectBySessionId(sessionId);
        if (session == null) {
            return Optional.empty();
        }

        // 2. 获取最新的 Checkpoint（用于消息）
        StateSnapshot latestSnapshot = checkpointManager.getState(sessionId)
                .orElse(null);

        return Optional.of(buildSessionDetailDTO(session, latestSnapshot));
    }

    /**
     * 创建新会话
     *
     * @param agentName Agent名称
     * @param title     会话标题（可选）
     * @return 创建的会话详情
     */
    @Transactional
    public SessionDetailDTO createSession(String agentName, String title) {
        String sessionId = UUID.randomUUID().toString();

        // 1. 创建 Session 记录
        SessionEntity session = new SessionEntity()
                .setSessionId(sessionId)
                .setTitle(title != null && !title.isEmpty() ? title : "新对话")
                .setCurrentAgent(agentName)
                .setStatus("active")
                .setAgentSwitchHistory("[]")
                .setCreatedAt(Instant.now())
                .setUpdatedAt(Instant.now());
        sessionMapper.insert(session);

        // 2. 创建初始 Checkpoint
        Map<String, Object> state = new HashMap<>();
        state.put("messages", new ArrayList<>());
        state.put("current_agent", agentName);
        state.put("agent_history", new ArrayList<>());
        state.put("iteration", 0);

        CheckpointMetadata metadata = CheckpointMetadata.builder()
                .source("manual")
                .stepInfo(Map.of("title", title != null && !title.isEmpty() ? title : "新对话"))
                .build();

        StateSnapshot snapshot = StateSnapshot.builder()
                .checkpointId(UUID.randomUUID().toString())
                .threadId(sessionId)
                .nodeId("INIT")
                .state(state)
                .metadata(metadata)
                .iteration(0)
                .build();

        checkpointManager.getStorage().saveCheckpoint(sessionId, snapshot);

        log.info("Created new session {} with agent {}", sessionId, agentName);
        return buildSessionDetailDTO(session, snapshot);
    }

    /**
     * 更新会话标题
     *
     * @param sessionId 会话ID
     * @param title     新标题
     */
    @Transactional
    public void updateSession(String sessionId, String title) {
        sessionMapper.updateTitle(sessionId, title);
        log.debug("Updated session {} title to {}", sessionId, title);
    }

    /**
     * 切换会话的 Agent
     *
     * @param sessionId   会话ID
     * @param newAgentName 新 Agent 名称
     */
    @Transactional
    public void switchAgent(String sessionId, String newAgentName) {
        SessionEntity session = sessionMapper.selectBySessionId(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        // 获取当前的 Agent 切换历史
        List<String> history = parseAgentHistory(session.getAgentSwitchHistory());
        history.add(session.getCurrentAgent());

        // 更新 Session
        sessionMapper.updateAgent(sessionId, newAgentName, serializeAgentHistory(history));

        // 同时更新 Checkpoint 中的状态
        Optional<StateSnapshot> latestOpt = checkpointManager.getState(sessionId);
        if (latestOpt.isPresent()) {
            StateSnapshot latest = latestOpt.get();
            Map<String, Object> state = new HashMap<>(latest.getState());
            state.put("current_agent", newAgentName);

            CheckpointMetadata metadata = CheckpointMetadata.builder()
                    .source("manual")
                    .parentId(latest.getCheckpointId())
                    .stepInfo(Map.of("agent_switch", true))
                    .build();

            StateSnapshot newSnapshot = StateSnapshot.builder()
                    .checkpointId(UUID.randomUUID().toString())
                    .threadId(sessionId)
                    .nodeId(latest.getNodeId())
                    .state(state)
                    .metadata(metadata)
                    .iteration(latest.getIteration())
                    .build();

            checkpointManager.getStorage().saveCheckpoint(sessionId, newSnapshot);
        }

        log.info("Switched agent for session {} from {} to {}", sessionId, session.getCurrentAgent(), newAgentName);
    }

    /**
     * 添加消息到会话
     *
     * @param sessionId 会话ID
     * @param role      角色（user/assistant）
     * @param content   消息内容
     */
    @Transactional
    public void addMessage(String sessionId, String role, String content) {
        Optional<StateSnapshot> latestOpt = checkpointManager.getState(sessionId);
        if (latestOpt.isEmpty()) {
            log.warn("Session {} not found for adding message", sessionId);
            return;
        }

        StateSnapshot latest = latestOpt.get();
        Map<String, Object> state = new HashMap<>(latest.getState());

        // 添加消息
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) state.getOrDefault("messages", new ArrayList<>());
        Map<String, Object> message = new HashMap<>();
        message.put("id", UUID.randomUUID().toString());
        message.put("role", role);
        message.put("content", content);
        message.put("timestamp", Instant.now().toString());
        messages.add(message);
        state.put("messages", messages);

        // 创建新 Checkpoint
        CheckpointMetadata metadata = CheckpointMetadata.builder()
                .source("manual")
                .parentId(latest.getCheckpointId())
                .build();

        StateSnapshot newSnapshot = StateSnapshot.builder()
                .checkpointId(UUID.randomUUID().toString())
                .threadId(sessionId)
                .nodeId(latest.getNodeId())
                .state(state)
                .metadata(metadata)
                .iteration(latest.getIteration())
                .build();

        checkpointManager.getStorage().saveCheckpoint(sessionId, newSnapshot);

        // 更新 Session 的时间戳
        sessionMapper.updateTimestamp(sessionId);
    }

    /**
     * 添加消息到会话（如果不存在）
     * <p>
     * 检查最后一条消息是否与待添加消息相同，如果相同则跳过添加，防止重复
     * </p>
     * <p>
     * <b>已废弃：</b>由于状态保存已移至每个节点执行完成后，此方法不再需要。
     * 消息会通过 NodeExecutor 中的检查点机制自动保存。
     * </p>
     *
     * @param sessionId 会话ID
     * @param role      角色（user/assistant）
     * @param content   消息内容
     * @deprecated 不再需要手动添加消息，消息通过节点检查点自动保存
     */
    @Deprecated(forRemoval = true)
    @Transactional
    public void addMessageIfNotExists(String sessionId, String role, String content) {

        @SuppressWarnings("unchecked")
        Optional<StateSnapshot> latestOpt = checkpointManager.getState(sessionId);
        if (latestOpt.isEmpty()) {
            log.warn("Session {} not found for adding message", sessionId);
            return;
        }

        StateSnapshot latest = latestOpt.get();
        Map<String, Object> state = latest.getState();

        // 检查消息是否已存在
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) state.getOrDefault("messages", new ArrayList<>());

        // 检查最后一条消息是否相同
        if (!messages.isEmpty()) {
            Map<String, Object> lastMessage = messages.get(messages.size() - 1);
            String lastRole = (String) lastMessage.get("role");
            String lastContent = (String) lastMessage.get("content");

            if (role.equals(lastRole) && content.equals(lastContent)) {
                log.debug("Message already exists, skipping: role={}, content={}", role, content);
                return;  // 消息已存在，跳过
            }
        }

        // 消息不存在，添加
        addMessage(sessionId, role, content);
    }

    /**
     * 删除会话（软删除）
     *
     * @param sessionId 会话ID
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteSession(String sessionId) {
        // 软删除 Session
        sessionMapper.softDelete(sessionId);

        // 删除关联的 Checkpoint
        checkpointManager.deleteThread(sessionId);

        log.info("Deleted session {}", sessionId);
        return true;
    }

    /**
     * 检查会话是否存在
     *
     * @param sessionId 会话ID
     * @return 是否存在
     */
    public boolean sessionExists(String sessionId) {
        SessionEntity session = sessionMapper.selectBySessionId(sessionId);
        return session != null && "active".equals(session.getStatus());
    }

    /**
     * 获取会话数量
     *
     * @return 会话总数
     */
    public int getSessionCount() {
        return sessionMapper.selectActiveSessions().size();
    }

    // ==================== 辅助方法 ====================

    /**
     * 将 SessionEntity 转换为 SessionDTO
     */
    private SessionDTO toSessionDTO(SessionEntity entity) {
        // 从最新的 Checkpoint 获取消息数量
        int messageCount = 0;
        Optional<StateSnapshot> latestOpt = checkpointManager.getState(entity.getSessionId());
        if (latestOpt.isPresent()) {
            Map<String, Object> state = latestOpt.get().getState();
            @SuppressWarnings("unchecked")
            List<?> messages = (List<?>) state.getOrDefault("messages", new ArrayList<>());
            messageCount = messages.size();
        }

        return new SessionDTO(
                entity.getSessionId(),
                entity.getTitle(),
                entity.getCurrentAgent(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                messageCount
        );
    }

    /**
     * 构建 SessionDetailDTO
     */
    private SessionDetailDTO buildSessionDetailDTO(SessionEntity session, StateSnapshot snapshot) {
        SessionDetailDTO dto = new SessionDetailDTO();
        dto.setId(session.getSessionId());
        dto.setTitle(session.getTitle());
        dto.setAgentName(session.getCurrentAgent());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setUpdatedAt(session.getUpdatedAt());

        // 提取消息
        List<SessionDetailDTO.SessionMessage> messages = new ArrayList<>();
        if (snapshot != null) {
            Map<String, Object> state = snapshot.getState();
            Object messagesObj = state.get("messages");
            if (messagesObj instanceof List<?> messageList) {
                messages = convertMessagesToSessionMessages(messageList);
            }
        }
        dto.setMessages(messages);
        dto.setMessageCount(messages.size());

        return dto;
    }

    /**
     * 将 messages 列表转换为 SessionMessage 列表
     * <p>
     * 处理两种情况：
     * 1. Message 对象（类型化反序列化后）
     * 2. Map 对象（旧格式）
     * </p>
     */
    @SuppressWarnings("unchecked")
    private List<SessionDetailDTO.SessionMessage> convertMessagesToSessionMessages(List<?> messageList) {
        List<SessionDetailDTO.SessionMessage> result = new ArrayList<>();

        for (Object item : messageList) {
            if (item == null) continue;

            // 如果是 Message 对象，提取信息
            if (item instanceof Message message) {
                SessionDetailDTO.SessionMessage sessionMessage = new SessionDetailDTO.SessionMessage();
                sessionMessage.setId(UUID.randomUUID().toString()); // Message 对象没有 id，生成一个
                sessionMessage.setContent(extractContent(message));
                sessionMessage.setRole(extractRole(message));
                sessionMessage.setTimestamp(Instant.now());
                sessionMessage.setMetadata(extractMetadata(message));
                result.add(sessionMessage);
                continue;
            }else{
                throw new IllegalArgumentException("Unknown message type: " + item.getClass());
            }
        }

        return result;
    }

    /**
     * 从 Message 对象提取元数据
     * <p>
     * 为前端提供结构化的工具调用和响应数据
     * </p>
     */
    private Map<String, Object> extractMetadata(Message message) {
        HashMap<String, Object> metadata = new HashMap<>();

        if (message instanceof UserMessage || message instanceof SystemMessage) {
            return metadata;
        }

        // AssistantMessage - 提取工具调用信息
        if (message instanceof AssistantMessage assistantMessage) {
            if (assistantMessage.hasToolCalls()) {
                List<Map<String, Object>> toolCallsList = new ArrayList<>();
                for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                    Map<String, Object> toolCallInfo = new HashMap<>();
                    toolCallInfo.put("id", toolCall.id());
                    toolCallInfo.put("name", toolCall.name());
                    toolCallInfo.put("type", toolCall.type());
                    toolCallInfo.put("arguments", toolCall.arguments());
                    toolCallsList.add(toolCallInfo);
                }
                metadata.put("tool_calls", toolCallsList);
            }
            return metadata;
        }

        // ToolResponseMessage - 提取工具响应信息
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            List<Map<String, Object>> toolResponsesList = new ArrayList<>();
            for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                Map<String, Object> responseInfo = new HashMap<>();
                responseInfo.put("id", response.id());
                responseInfo.put("name", response.name());
                responseInfo.put("response", response.responseData());
                toolResponsesList.add(responseInfo);
            }
            metadata.put("tool_responses", toolResponsesList);
            return metadata;
        }

        throw new RuntimeException("Unknown message type: " + message.getClass());
    }

    /**
     * 从 Message 对象提取角色
     */
    private String extractRole(Message message) {
        if (message instanceof UserMessage) return "user";
        if(message instanceof AssistantMessage && ((AssistantMessage) message).hasToolCalls()) return "tool_call";
        if (message instanceof AssistantMessage) return "assistant";
        if(message instanceof ToolResponseMessage) return "tool_response";
        return "user"; // 默认
    }

    /**
     * 从 Message 对象提取内容
     * <p>
     * 配合前端设计，格式化工具调用和响应的内容
     * </p>
     */
    private String extractContent(Message message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.getText();
        }

        // AssistantMessage 处理
        if (message instanceof AssistantMessage assistantMessage) {
            // 有工具调用时，格式化显示工具调用信息
            if (assistantMessage.hasToolCalls()) {
                List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
                StringBuilder sb = new StringBuilder();
                for (AssistantMessage.ToolCall toolCall : toolCalls) {
                    String id = toolCall.id();
                    String name = toolCall.name();
                    String arguments = toolCall.arguments();
                    // 格式化：工具调用名称和参数
                    sb.append("**调用工具**: `").append(name).append("`\n\n");
                    sb.append("**参数**: ```json\n").append(arguments).append("\n```\n");
                }
                return sb.toString();
            }
            // 普通的 assistant 消息，直接返回文本
            return assistantMessage.getText();
        }

        // ToolResponseMessage 处理
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            List<ToolResponseMessage.ToolResponse> responses = toolResponseMessage.getResponses();
            StringBuilder sb = new StringBuilder();
            for (ToolResponseMessage.ToolResponse response : responses) {
                String name = response.name();
                String result = response.responseData();
                // 格式化：工具响应结果
                sb.append("**工具结果**: `").append(name).append("` \n\n");
                sb.append("**返回值**: ```\n").append(result).append("\n```\n");
            }
            return sb.toString();
        }

        return "这是一条空消息";
    }

    /**
     * 解析 Agent 切换历史
     */
    private List<String> parseAgentHistory(String historyJson) {
        if (historyJson == null || historyJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(historyJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse agent history: {}", historyJson, e);
            return new ArrayList<>();
        }
    }

    /**
     * 序列化 Agent 切换历史
     */
    private String serializeAgentHistory(List<String> history) {
        try {
            return objectMapper.writeValueAsString(history);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize agent history: {}", history, e);
            return "[]";
        }
    }
}
