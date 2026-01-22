package cn.ts.web.controller;

import cn.ts.web.dto.SessionDetailDTO;
import cn.ts.web.dto.SessionDTO;
import cn.ts.web.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<SessionDTO>> getAllSessions() {
        return ResponseEntity.ok(sessionService.getAllSessions());
    }

    /**
     * 获取会话详情
     *
     * @param sessionId 会话ID
     * @return 会话详情
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionDetailDTO> getSession(@PathVariable String sessionId) {
        return sessionService.getSession(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建新会话
     *
     * @param body 请求体 {"agentName": "xxx", "title": "xxx"}
     * @return 创建的会话
     */
    @PostMapping
    public ResponseEntity<SessionDetailDTO> createSession(@RequestBody Map<String, String> body) {
        String agentName = body.getOrDefault("agentName", "TestAgent");
        String title = body.get("title");

        SessionDetailDTO session = sessionService.createSession(agentName, title);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    /**
     * 更新会话标题
     *
     * @param sessionId 会话ID
     * @param body      请求体 {"title": "xxx"}
     * @return 更新结果
     */
    @PutMapping("/{sessionId}")
    public ResponseEntity<Void> updateSession(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        if (!sessionService.sessionExists(sessionId)) {
            return ResponseEntity.notFound().build();
        }

        String title = body.get("title");
        sessionService.updateSession(sessionId, title);
        return ResponseEntity.ok().build();
    }

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     * @return 删除结果
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        if (!sessionService.sessionExists(sessionId)) {
            return ResponseEntity.notFound().build();
        }

        sessionService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 添加消息到会话
     *
     * @param sessionId 会话ID
     * @param body      请求体 {"role": "user|assistant", "content": "xxx"}
     * @return 添加结果
     */
    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<Void> addMessage(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        if (!sessionService.sessionExists(sessionId)) {
            return ResponseEntity.notFound().build();
        }

        String role = body.get("role");
        String content = body.get("content");

        if (role == null || content == null) {
            return ResponseEntity.badRequest().build();
        }

        sessionService.addMessage(sessionId, role, content);
        return ResponseEntity.ok().build();
    }
}
