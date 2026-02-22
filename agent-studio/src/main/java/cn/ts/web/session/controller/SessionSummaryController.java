package cn.ts.web.session.controller;

import cn.ts.web.controller.response.Result;
import cn.ts.web.controller.response.ResultCode;
import cn.ts.web.session.dto.SessionSummaryDTO;
import cn.ts.web.session.service.SessionSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 会话摘要控制器
 * <p>
 * 提供会话统计信息的 REST API
 * </p>
 *
 * @author tianshuo
 */
@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = "*")
public class SessionSummaryController {

    private static final Logger log = LoggerFactory.getLogger(SessionSummaryController.class);

    private final SessionSummaryService sessionSummaryService;

    public SessionSummaryController(SessionSummaryService sessionSummaryService) {
        this.sessionSummaryService = sessionSummaryService;
    }

    /**
     * 获取会话摘要
     *
     * @param sessionId 会话ID
     * @return 会话摘要信息
     */
    @GetMapping("/{sessionId}/summary")
    public Result<SessionSummaryDTO> getSessionSummary(@PathVariable String sessionId) {
        try {
            log.debug("Getting session summary for sessionId: {}", sessionId);

            SessionSummaryDTO summary = sessionSummaryService.calculateSummary(sessionId);

            log.debug("Successfully calculated summary for session {}: {} tokens, {} tool calls, {} LLM calls",
                    sessionId,
                    summary.getBasicStats().getTotalTokens(),
                    summary.getBasicStats().getTotalToolCalls(),
                    summary.getBasicStats().getLlmCallCount());

            return Result.success(summary);

        } catch (IllegalArgumentException e) {
            log.warn("Session not found: {}", sessionId);
            return Result.error(ResultCode.NOT_FOUND);
        } catch (Exception e) {
            log.error("Failed to get session summary for sessionId: {}", sessionId, e);
            return Result.error(ResultCode.ERROR, "获取会话摘要失败: " + e.getMessage());
        }
    }
}
