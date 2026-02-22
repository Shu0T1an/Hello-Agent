package cn.ts.web.session.controller;

import cn.ts.web.shared.response.Result;
import cn.ts.web.shared.response.ResultCode;
import cn.ts.web.session.dto.SessionDetailDTO;
import cn.ts.web.session.dto.SessionDTO;
import cn.ts.web.session.service.SessionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会话管理控制器
 * <p>
 * 提供会话的增删改查API
 * </p>
 *
 * @author tianshuo
 */
@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = "*")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * 获取所有会话列表
     *
     * @return 会话列表
     */
    @GetMapping
    public Result<List<SessionDTO>> getAllSessions() {
        return Result.success(sessionService.getAllSessions());
    }

    /**
     * 获取会话详情
     *
     * @param sessionId 会话ID
     * @return 会话详情
     */
    @GetMapping("/{sessionId}")
    public Result<SessionDetailDTO> getSession(@PathVariable String sessionId) {
        return sessionService.getSession(sessionId)
                .map(Result::success)
                .orElse(Result.error(ResultCode.NOT_FOUND));
    }

    /**
     * 创建新会话
     *
     * @param body 请求体 {"agentName": "xxx", "title": "xxx"}
     * @return 创建的会话
     */
    @PostMapping
    public Result<SessionDetailDTO> createSession(@RequestBody Map<String, String> body) {
        String agentName = body.getOrDefault("agentName", "TestAgent");
        String title = body.get("title");

        SessionDetailDTO session = sessionService.createSession(agentName, title);
        return Result.success("创建成功", session);
    }

    /**
     * 更新会话标题
     *
     * @param sessionId 会话ID
     * @param body      请求体 {"title": "xxx"}
     * @return 更新结果
     */
    @PutMapping("/{sessionId}")
    public Result<Void> updateSession(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        if (!sessionService.sessionExists(sessionId)) {
            return Result.error(ResultCode.NOT_FOUND);
        }

        String title = body.get("title");
        sessionService.updateSession(sessionId, title);
        return Result.success();
    }

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     * @return 删除结果
     */
    @DeleteMapping("/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        if (!sessionService.sessionExists(sessionId)) {
            return Result.error(ResultCode.NOT_FOUND);
        }

        sessionService.deleteSession(sessionId);
        return Result.success();
    }

    /**
     * 添加消息到会话
     *
     * @param sessionId 会话ID
     * @param body      请求体 {"role": "user|assistant", "content": "xxx"}
     * @return 添加结果
     */
    @PostMapping("/{sessionId}/messages")
    public Result<Void> addMessage(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        if (!sessionService.sessionExists(sessionId)) {
            return Result.error(ResultCode.NOT_FOUND);
        }

        String role = body.get("role");
        String content = body.get("content");

        if (role == null || content == null) {
            return Result.error(ResultCode.BAD_REQUEST);
        }

        sessionService.addMessage(sessionId, role, content);
        return Result.success();
    }

    /**
     * 切换会话的 Agent
     *
     * @param sessionId 会话ID
     * @param body      请求体 {"agentName": "xxx"}
     * @return 切换结果
     */
    @PutMapping("/{sessionId}/switch-agent")
    public Result<String> switchAgent(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        if (!sessionService.sessionExists(sessionId)) {
            return Result.error(ResultCode.NOT_FOUND);
        }

        String agentName = body.get("agentName");
        if (agentName == null || agentName.isEmpty()) {
            return Result.error(ResultCode.BAD_REQUEST, "agentName 不能为空");
        }

        try {
            sessionService.switchAgent(sessionId, agentName);
            return Result.success("Agent 切换成功");
        } catch (IllegalArgumentException e) {
            return Result.error(ResultCode.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 删除所有会话
     *
     * @return 删除结果（包含删除数量）
     */
    @DeleteMapping("/delete-all")
    public Result<Map<String, Integer>> deleteAllSessions() {
        int count = sessionService.deleteAllSessions();
        return Result.success(Map.of("count", count));
    }
}
