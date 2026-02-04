package cn.ts.web.service;

import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.checkpoint.StateSnapshot;
import cn.ts.web.dto.GraphStateVO;
import cn.ts.web.dto.SessionDetailVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 图状态服务
 * <p>
 * 提供图状态的查询功能，并将 StateSnapshot 转换为 VO 对象返回给前端
 * </p>
 *
 * @author tianshuo
 */
@Service
public class GraphStateService {

    private final CheckpointManager checkpointManager;

    public GraphStateService(CheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
    }

    /**
     * 获取图状态
     *
     * @param sessionId 会话ID（threadId）
     * @return GraphStateVO
     * @throws IllegalArgumentException 如果会话不存在
     */
    public GraphStateVO getGraphState(String sessionId) {
        return checkpointManager.getState(sessionId)
                .map(GraphStateVO::from)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }

    /**
     * 获取会话详情
     *
     * @param sessionId 会话ID（threadId）
     * @return SessionDetailVO
     * @throws IllegalArgumentException 如果会话不存在
     */
    public SessionDetailVO getSessionDetail(String sessionId) {
        return checkpointManager.getState(sessionId)
                .map(SessionDetailVO::from)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }

    /**
     * 获取会话状态历史
     *
     * @param sessionId 会话ID（threadId）
     * @return GraphStateVO 列表（按时间顺序）
     */
    public List<GraphStateVO> getStateHistory(String sessionId) {
        return checkpointManager.getStateHistory(sessionId).stream()
                .map(GraphStateVO::from)
                .toList();
    }

    /**
     * 检查会话是否存在
     *
     * @param sessionId 会话ID（threadId）
     * @return 是否存在
     */
    public boolean sessionExists(String sessionId) {
        return checkpointManager.getState(sessionId).isPresent();
    }
}
