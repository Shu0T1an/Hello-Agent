package cn.ts.web.session.controller;

import cn.ts.web.infra.audit.service.LlmPromptAuditService;
import cn.ts.web.session.dto.SessionAuditDTO;
import cn.ts.web.shared.response.Result;
import cn.ts.web.shared.response.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session audit query controller.
 */
@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = "*")
public class SessionAuditController {

    private static final Logger log = LoggerFactory.getLogger(SessionAuditController.class);

    private final LlmPromptAuditService auditService;

    public SessionAuditController(LlmPromptAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/{sessionId}/audits")
    public Result<SessionAuditDTO> getSessionAudits(
            @PathVariable String sessionId,
            @RequestParam(required = false) Integer limit) {
        try {
            SessionAuditDTO result = auditService.listBySessionId(sessionId, limit);
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.error(ResultCode.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to query session audits for sessionId={}", sessionId, e);
            return Result.error(ResultCode.ERROR, "获取会话审计数据失败: " + e.getMessage());
        }
    }
}
