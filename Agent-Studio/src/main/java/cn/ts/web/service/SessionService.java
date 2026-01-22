package cn.ts.web.service;

import cn.ts.web.dto.SessionDetailDTO;
import cn.ts.web.dto.SessionDTO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 会话管理服务
 * <p>
 * 提供会话的创建、查询、删除等功能
 * 使用内存存储，适合单用户场景
 * </p>
 *
 * @author tianshuo
 */
@Service
public class SessionService {

    /**
     * 会话存储：sessionId -> SessionDetailDTO
     */
    private final Map<String, SessionDetailDTO> sessions = new ConcurrentHashMap<>();

    /**
     * 获取所有会话列表
     *
     * @return 会话列表（按更新时间倒序）
     */
    public List<SessionDTO> getAllSessions() {
        return sessions.values().stream()
                .map(this::toDTO)
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .collect(Collectors.toList());
    }

    /**
     * 获取会话详情
     *
     * @param sessionId 会话ID
     * @return 会话详情
     */
    public Optional<SessionDetailDTO> getSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * 创建新会话
     *
     * @param agentName  Agent名称
     * @param title      会话标题（可选）
     * @return 创建的会话
     */
    public SessionDetailDTO createSession(String agentName, String title) {
        String sessionId = "session-" + UUID.randomUUID();
        Instant now = Instant.now();

        SessionDetailDTO session = new SessionDetailDTO();
        session.setId(sessionId);
        session.setAgentName(agentName);
        session.setTitle(title != null && !title.isEmpty() ? title : "新对话");
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setMessageCount(0);
        session.setMessages(new ArrayList<>());

        sessions.put(sessionId, session);
        return session;
    }

    /**
     * 更新会话
     *
     * @param sessionId 会话ID
     * @param title     新标题
     */
    public void updateSession(String sessionId, String title) {
        SessionDetailDTO session = sessions.get(sessionId);
        if (session != null) {
            session.setTitle(title);
            session.setUpdatedAt(Instant.now());
        }
    }

    /**
     * 添加消息到会话
     *
     * @param sessionId 会话ID
     * @param role      角色（user/assistant）
     * @param content   消息内容
     */
    public void addMessage(String sessionId, String role, String content) {
        SessionDetailDTO session = sessions.get(sessionId);
        if (session != null) {
            SessionDetailDTO.SessionMessage message = new SessionDetailDTO.SessionMessage(
                    UUID.randomUUID().toString(),
                    role,
                    content,
                    Instant.now()
            );
            session.getMessages().add(message);
            session.setMessageCount(session.getMessages().size());
            session.setUpdatedAt(Instant.now());
        }
    }

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     * @return 是否删除成功
     */
    public boolean deleteSession(String sessionId) {
        return sessions.remove(sessionId) != null;
    }

    /**
     * 检查会话是否存在
     *
     * @param sessionId 会话ID
     * @return 是否存在
     */
    public boolean sessionExists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    /**
     * 获取会话数量
     *
     * @return 会话总数
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * 转换为 SessionDTO（不含消息列表）
     */
    private SessionDTO toDTO(SessionDetailDTO detail) {
        SessionDTO dto = new SessionDTO();
        dto.setId(detail.getId());
        dto.setTitle(detail.getTitle());
        dto.setAgentName(detail.getAgentName());
        dto.setCreatedAt(detail.getCreatedAt());
        dto.setUpdatedAt(detail.getUpdatedAt());
        dto.setMessageCount(detail.getMessageCount());
        return dto;
    }
}
