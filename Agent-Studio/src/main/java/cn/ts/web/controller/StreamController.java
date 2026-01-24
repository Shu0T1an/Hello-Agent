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
     * </p>
     *
     * @param agentName    Agent 名称
     * @param input        用户输入
     * @param sessionId    会话ID（可选，用于保持连续对话）
     * @param initialState 初始状态（可选）
     * @return SSE 事件流
     */
    @GetMapping(value = "/agent/{agentName}/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentResponse>> executeAgentStream(
            @PathVariable String agentName,
            @RequestParam(required = false) String input,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Map<String, Object> initialState) {

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
        if (input != null && !input.isEmpty()) {
            messages.add(new UserMessage(input));
        }

        // 合并参数：支持 input 参数或 initialState
        Map<String, Object> mergedState = new HashMap<>();
        if (initialState != null) {
            mergedState.putAll(initialState);
        }
        if (!messages.isEmpty()) {
            mergedState.put("messages", messages);
        }

        return agentExecutionService.executeAgentStreamWithSession(
                        agentName,
                        mergedState.isEmpty() ? null : mergedState,
                        sessionId)
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
     * 流式执行 Agent（带超时配置）
     * <p>
     * 使用配置文件中的默认超时时间，可通过参数覆盖
     * </p>
     *
     * @param agentName    Agent 名称
     * @param timeout      超时时间（秒），默认使用配置值
     * @param initialState 初始状态（可选）
     * @return SSE 事件流
     */
    @GetMapping(value = "/agent/{agentName}/execute-with-timeout", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentResponse>> executeAgentStreamWithTimeout(
            @PathVariable String agentName,
            @RequestParam(required = false) Integer timeout,
            @RequestParam(required = false) String input,
            @RequestParam(required = false) Map<String, Object> initialState) {

        // 使用配置的超时时间，或参数覆盖
        java.time.Duration actualTimeout = timeout != null
                ? java.time.Duration.ofSeconds(timeout)
                : config.getTimeout();

        // 合并参数：支持 input 参数或 initialState
        Map<String, Object> mergedState = new HashMap<>();
        if (initialState != null) {
            mergedState.putAll(initialState);
        }
        if (input != null && !input.isEmpty()) {
            mergedState.put("messages", List.of(new UserMessage(input)));
        }

        return agentExecutionService.executeAgentStream(
                agentName,
                mergedState.isEmpty() ? Map.of() : mergedState,
                actualTimeout
        ).map(response -> ServerSentEvent.<AgentResponse>builder()
                .data(response)
                .build());
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
