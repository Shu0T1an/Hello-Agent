package cn.ts.web.controller;

import cn.ts.web.config.AgentExecutionConfig;
import cn.ts.web.dto.AgentResponse;
import cn.ts.web.dto.SessionDetailDTO;
import cn.ts.web.service.AgentExecutionService;
import cn.ts.web.service.SessionService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SSE 流式控制器（重构版）
 * <p>
 * 提供 SSE 端点用于向前端实时推送 Agent 执行事件
 * 使用配置外部化，支持通过配置文件调整超时和心跳间隔
 * </p>
 *
 * @author tianshuo
 */
@RestController
@RequestMapping("/api/stream")
@CrossOrigin(origins = "*")
public class StreamController {

    private final AgentExecutionService agentExecutionService;
    private final SessionService sessionService;
    private final AgentExecutionConfig config;

    public StreamController(
            AgentExecutionService agentExecutionService,
            SessionService sessionService,
            AgentExecutionConfig config) {
        this.agentExecutionService = agentExecutionService;
        this.sessionService = sessionService;
        this.config = config;
    }

    /**
     * 流式执行 Agent
     * <p>
     * SSE 端点，实时推送执行事件
     * 使用 POST + JSON 请求体，统一处理所有参数
     * </p>
     *
     * @param agentName Agent 名称
     * @param request   执行请求
     * @return SSE 事件流
     */
    @PostMapping(value = "/agent/{agentName}/execute", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentResponse>> executeAgent(
            @PathVariable String agentName,
            @Valid @RequestBody cn.ts.web.dto.AgentExecuteRequest request) {

        // 如果提供了 sessionId，检查是否需要自动切换 Agent
        String sessionId = request.getSessionId();
        if (sessionId != null && !sessionId.isEmpty()) {
            sessionService.getSession(sessionId).ifPresent(sessionDetail -> {
                // 如果会话当前 Agent 与请求的 Agent 不同，自动切换
                if (!sessionDetail.getAgentName().equals(agentName)) {
                    try {
                        sessionService.switchAgent(sessionId, agentName);
                    } catch (Exception e) {
                        // Agent 切换失败，记录日志但不阻断执行
                        // 执行时会因为 Agent 不存在而失败
                    }
                }
            });
        }

        // 构建消息列表（包含历史消息）
        List<Message> messages = new ArrayList<>();

        // 如果有 sessionId，从会话服务加载历史消息
        if (sessionId != null && !sessionId.isEmpty()) {
            sessionService.getSession(sessionId).ifPresent(sessionDetail -> {
                if (sessionDetail.getMessages() != null) {
                    for (SessionDetailDTO.SessionMessage msg : sessionDetail.getMessages()) {
                        Message message = convertToSpringAIMessage(msg);
                        if (message != null) {
                            messages.add(message);
                        }
                    }
                }
            });
        }

        // 添加当前用户输入
        if (request.getInput() != null && !request.getInput().isEmpty()) {
            messages.add(new UserMessage(request.getInput()));
        }

        // 创建 messages 副本，避免 Graph 执行修改原始列表
        List<Message> messagesCopy = new ArrayList<>(messages);

        // 合并初始状态
        Map<String, Object> mergedState = new HashMap<>();
        if (request.getInitialState() != null) {
            mergedState.putAll(request.getInitialState());
        }
        if (!messagesCopy.isEmpty()) {
            mergedState.put("messages", messagesCopy);
        }
        // 保存用户输入，用于 extractUserInput 提取
        if (request.getInput() != null && !request.getInput().isEmpty()) {
            mergedState.put("input", request.getInput());
        }

        // 确定超时时间
        java.time.Duration actualTimeout = request.getTimeout() != null
                ? java.time.Duration.ofSeconds(request.getTimeout())
                : config.getTimeout();

        return agentExecutionService.executeAgentStreamWithSession(
                        agentName,
                        mergedState.isEmpty() ? null : mergedState,
                        sessionId,
                        actualTimeout)
                .map(response -> ServerSentEvent.<AgentResponse>builder()
                        .data(response)
                        .id(response.getExecutionId())
                        .build());
    }

    /**
     * 将后端会话消息转换为 Spring AI Message
     */
    private Message convertToSpringAIMessage(SessionDetailDTO.SessionMessage msg) {
        return switch (msg.getRole()) {
            case "user" -> new UserMessage(msg.getContent());
            case "assistant" -> new AssistantMessage(msg.getContent());
            default -> null;
        };
    }

    /**
     * 心跳端点
     * <p>
     * 用于保持 SSE 连接活跃，使用配置文件中的心跳间隔
     * </p>
     *
     * @return 定时心跳事件
     */
    @GetMapping(value = "/heartbeat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentResponse>> heartbeat() {
        return Flux.interval(config.getHeartbeatInterval())
                .map(sequence -> ServerSentEvent.<AgentResponse>builder()
                        .id(String.valueOf(sequence))
                        .data(AgentResponse.heartbeat(sequence))
                        .build());
    }

    /**
     * 检查 Agent 是否已注册
     *
     * @param agentName Agent 名称
     * @return true 如果已注册
     */
    @GetMapping("/agent/{agentName}/exists")
    public boolean checkAgentExists(@PathVariable String agentName) {
        return agentExecutionService.isAgentRegistered(agentName);
    }

    /**
     * 获取所有已注册的 Agent
     *
     * @return Agent 名称列表
     */
    @GetMapping("/agents")
    public java.util.Set<String> getRegisteredAgents() {
        return agentExecutionService.getRegisteredAgents();
    }
}
