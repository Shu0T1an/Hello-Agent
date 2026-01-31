package cn.ts.web.service;

import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.checkpoint.CheckpointMetadata;
import cn.ts.graph.checkpoint.StateSnapshot;
import cn.ts.web.dto.SessionDetailDTO;
import cn.ts.web.dto.SessionDTO;
import cn.ts.web.entity.CheckpointEntity;
import cn.ts.web.mapper.CheckpointMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 会话管理服务
 * <p>
 * 基于 Checkpoint 系统实现统一会话和状态管理
 * 会话直接对应 Checkpoint 中的 threadId
 * </p>
 *
 * @author tianshuo
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final CheckpointManager checkpointManager;
    private final CheckpointMapper checkpointMapper;

    public SessionService(CheckpointManager checkpointManager, CheckpointMapper checkpointMapper) {
        this.checkpointManager = checkpointManager;
        this.checkpointMapper = checkpointMapper;
    }

    /**
     * 获取所有会话列表
     *
     * @return 会话列表（按更新时间倒序）
     */
    public List<SessionDTO> getAllSessions() {
        List<String> threadIds = checkpointMapper.selectAllThreadIds();
        return threadIds.stream()
                .map(this::buildSessionDTOFromThreadId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .collect(Collectors.toList());
    }

    /**
     * 获取会话详情
     *
     * @param sessionId 会话ID（threadId）
     * @return 会话详情
     */
    public Optional<SessionDetailDTO> getSession(String sessionId) {
        return checkpointManager.getState(sessionId)
                .map(this::buildSessionDetailFromSnapshot);
    }

    /**
     * 创建新会话
     *
     * @param agentName  Agent名称
     * @param title      会话标题（可选）
     * @return 创建的会话
     */
    @Transactional
    public SessionDetailDTO createSession(String agentName, String title) {
        String threadId = "thread-" + UUID.randomUUID();

        // 初始化 State
        Map<String, Object> state = new HashMap<>();
        state.put("messages", new ArrayList<>());
        state.put("current_agent", agentName);
        state.put("agent_history", new ArrayList<>());
        state.put("iteration", 0);

        // 创建初始 Checkpoint
        CheckpointMetadata metadata = CheckpointMetadata.builder()
                .source("manual")
                .stepInfo(Map.of("title", title != null && !title.isEmpty() ? title : "新对话"))
                .build();

        StateSnapshot snapshot = StateSnapshot.builder()
                .threadId(threadId)
                .nodeId("INIT")
                .state(state)
                .metadata(metadata)
                .iteration(0)
                .build();

        checkpointManager.getStorage().saveCheckpoint(threadId, snapshot);

        log.info("Created new session {} with agent {}", threadId, agentName);
        return buildSessionDetailFromSnapshot(snapshot);
    }

    /**
     * 更新会话标题
     *
     * @param sessionId 会话ID
     * @param title     新标题
     */
    @Transactional
    public void updateSession(String sessionId, String title) {
        Optional<StateSnapshot> latestOpt = checkpointManager.getState(sessionId);
        if (latestOpt.isEmpty()) {
            log.warn("Session {} not found for update", sessionId);
            return;
        }

        StateSnapshot latest = latestOpt.get();

        // 创建新的 Checkpoint 更新标题（通过 stepInfo 传递）
        Map<String, Object> newState = new HashMap<>(latest.getState());

        CheckpointMetadata metadata = CheckpointMetadata.builder()
                .source("manual")
                .parentId(latest.getCheckpointId())
                .stepInfo(Map.of("title", title))
                .build();

        StateSnapshot newSnapshot = StateSnapshot.builder()
                .threadId(sessionId)
                .checkpointId(UUID.randomUUID().toString())
                .nodeId(latest.getNodeId())
                .state(newState)
                .metadata(metadata)
                .iteration(latest.getIteration())
                .build();

        checkpointManager.getStorage().saveCheckpoint(sessionId, newSnapshot);
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
        Optional<StateSnapshot> latestOpt = checkpointManager.getState(sessionId);
        if (latestOpt.isEmpty()) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        StateSnapshot latest = latestOpt.get();
        Map<String, Object> state = new HashMap<>(latest.getState());

        // 更新 current_agent
        String oldAgent = (String) state.get("current_agent");
        state.put("current_agent", newAgentName);

        // 记录切换历史
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> agentHistory = (List<Map<String, Object>>) state.getOrDefault("agent_history", new ArrayList<>());
        Map<String, Object> switchRecord = new HashMap<>();
        switchRecord.put("agent", newAgentName);
        switchRecord.put("switched_at", Instant.now().toString());
        switchRecord.put("reason", "user_initiated");
        switchRecord.put("from_agent", oldAgent);
        agentHistory.add(switchRecord);
        state.put("agent_history", agentHistory);

        // 创建新 Checkpoint
        CheckpointMetadata metadata = CheckpointMetadata.builder()
                .source("manual")
                .parentId(latest.getCheckpointId())
                .stepInfo(Map.of("agent_switch", true))
                .build();

        StateSnapshot newSnapshot = StateSnapshot.builder()
                .threadId(sessionId)
                .checkpointId(UUID.randomUUID().toString())
                .nodeId(latest.getNodeId())
                .state(state)
                .metadata(metadata)
                .iteration(latest.getIteration())
                .build();

        checkpointManager.getStorage().saveCheckpoint(sessionId, newSnapshot);
        log.info("Switched agent for session {} from {} to {}", sessionId, oldAgent, newAgentName);
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
                .threadId(sessionId)
                .checkpointId(UUID.randomUUID().toString())
                .nodeId(latest.getNodeId())
                .state(state)
                .metadata(metadata)
                .iteration(latest.getIteration())
                .build();

        checkpointManager.getStorage().saveCheckpoint(sessionId, newSnapshot);
    }

    /**
     * 添加消息到会话（如果不存在）
     * <p>
     * 检查最后一条消息是否与待添加消息相同，如果相同则跳过添加，防止重复
     * </p>
     *
     * @param sessionId 会话ID
     * @param role      角色（user/assistant）
     * @param content   消息内容
     */
    @Transactional
    public void addMessageIfNotExists(String sessionId, String role, String content) {
        Optional<StateSnapshot> latestOpt = checkpointManager.getState(sessionId);
        if (latestOpt.isEmpty()) {
            log.warn("Session {} not found for adding message", sessionId);
            return;
        }

        StateSnapshot latest = latestOpt.get();
        Map<String, Object> state = new HashMap<>(latest.getState());

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
     * 删除会话
     *
     * @param sessionId 会话ID
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteSession(String sessionId) {
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
        return checkpointManager.getState(sessionId).isPresent();
    }

    /**
     * 获取会话数量
     *
     * @return 会话总数
     */
    public int getSessionCount() {
        return checkpointMapper.selectAllThreadIds().size();
    }

    /**
     * 从 threadId 构建 SessionDTO
     */
    private Optional<SessionDTO> buildSessionDTOFromThreadId(String threadId) {
        return checkpointMapper.selectLatestByThreadId(threadId)
                .map(this::toSessionDTO);
    }

    /**
     * 从 StateSnapshot 构建 SessionDetailDTO
     */
    private SessionDetailDTO buildSessionDetailFromSnapshot(StateSnapshot snapshot) {
        Map<String, Object> state = snapshot.getState();
        Map<String, Object> stepInfo = snapshot.getMetadata().getStepInfo();

        SessionDetailDTO dto = new SessionDetailDTO();
        dto.setId(snapshot.getThreadId());
        dto.setAgentName((String) state.get("current_agent"));
        dto.setTitle(stepInfo != null && stepInfo.containsKey("title")
                ? (String) stepInfo.get("title")
                : "新对话");
        dto.setCreatedAt(snapshot.getTimestamp());
        dto.setUpdatedAt(snapshot.getTimestamp());

        // 提取消息
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) state.getOrDefault("messages", new ArrayList<>());
        List<SessionDetailDTO.SessionMessage> sessionMessages = messages.stream()
                .map(msg -> {
                    SessionDetailDTO.SessionMessage message = new SessionDetailDTO.SessionMessage();
                    message.setId((String) msg.get("id"));
                    message.setRole((String) msg.get("role"));
                    message.setContent((String) msg.get("content"));
                    String timestampStr = (String) msg.get("timestamp");
                    if (timestampStr != null) {
                        message.setTimestamp(Instant.parse(timestampStr));
                    }
                    return message;
                })
                .collect(Collectors.toList());
        dto.setMessages(sessionMessages);
        dto.setMessageCount(sessionMessages.size());

        return dto;
    }

    /**
     * 将 CheckpointEntity 转换为 SessionDTO
     */
    private SessionDTO toSessionDTO(CheckpointEntity entity) {
        Map<String, Object> state = entity.getStateMap();
        Map<String, Object> metadata = entity.getMetadataMap();

        SessionDTO dto = new SessionDTO();
        dto.setId(entity.getThreadId());
        dto.setAgentName((String) state.get("current_agent"));
        dto.setTitle(metadata != null && metadata.containsKey("title")
                ? (String) metadata.get("title")
                : "新对话");
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getCreatedAt());

        // 计算消息数量
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) state.getOrDefault("messages", new ArrayList<>());
        dto.setMessageCount(messages.size());

        return dto;
    }
}
