package cn.ts.web.controller;

import cn.ts.web.dto.AgentResponse;
import cn.ts.web.service.AgentExecutionService;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SSE 流式控制器
 * <p>
 * 提供 SSE 端点用于向前端实时推送 Agent 执行事件
 * </p>
 *
 * @author tianshuo
 */
@RestController
@RequestMapping("/api/stream")
@CrossOrigin(origins = "*")
public class StreamController {

    private final AgentExecutionService agentExecutionService;

    public StreamController(AgentExecutionService agentExecutionService) {
        this.agentExecutionService = agentExecutionService;
    }

    /**
     * 流式执行 Agent
     * <p>
     * SSE 端点，实时推送执行事件
     * </p>
     *
     * @param agentName    Agent 名称
     * @param initialState 初始状态（可选）
     * @return SSE 事件流
     */
    @GetMapping(value = "/agent/{agentName}/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentResponse>> executeAgentStream(
            @PathVariable String agentName,
            @RequestParam(required = false) String input,
            @RequestParam(required = false) Map<String, Object> initialState) {

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
                        mergedState.isEmpty() ? null : mergedState)
                .map(response -> ServerSentEvent.<AgentResponse>builder()
                        .data(response)
                        .id(response.getExecutionId())
                        .build());
    }

    /**
     * 流式执行 Agent（带超时配置）
     *
     * @param agentName    Agent 名称
     * @param timeout      超时时间（秒）
     * @param initialState 初始状态（可选）
     * @return SSE 事件流
     */
    @GetMapping(value = "/agent/{agentName}/execute-with-timeout", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentResponse>> executeAgentStreamWithTimeout(
            @PathVariable String agentName,
            @RequestParam(defaultValue = "300") int timeout,
            @RequestParam(required = false) String input,
            @RequestParam(required = false) Map<String, Object> initialState) {

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
                Duration.ofSeconds(timeout)
        ).map(response -> ServerSentEvent.<AgentResponse>builder()
                .data(response)
                .build());
    }

    /**
     * 心跳端点
     * <p>
     * 用于保持 SSE 连接活跃
     * </p>
     *
     * @return 定时心跳事件
     */
    @GetMapping(value = "/heartbeat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentResponse>> heartbeat() {
        return Flux.interval(Duration.ofSeconds(30))
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
