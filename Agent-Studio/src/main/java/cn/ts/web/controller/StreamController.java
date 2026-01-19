package cn.ts.web.controller;

import cn.ts.web.service.AgentExecutionService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
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
    public Flux<String> executeAgentStream(
            @PathVariable String agentName,
            @RequestParam(required = false) Map<String, Object> initialState) {

        return agentExecutionService.executeAgentStream(agentName, initialState)
                .doOnSubscribe(subscription -> {
                    // 发送连接成功事件
                })
                .doOnComplete(() -> {
                    // 流完成
                })
                .doOnError(error -> {
                    // 流错误
                });
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
    public Flux<String> executeAgentStreamWithTimeout(
            @PathVariable String agentName,
            @RequestParam(defaultValue = "300") int timeout,
            @RequestParam(required = false) Map<String, Object> initialState) {

        return agentExecutionService.executeAgentStream(
                agentName,
                initialState != null ? initialState : Map.of(),
                Duration.ofSeconds(timeout)
        );
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
    public Flux<ServerSentEvent<String>> heartbeat() {
        return Flux.interval(Duration.ofSeconds(30))
                .map(sequence -> ServerSentEvent.<String>builder()
                        .id(String.valueOf(sequence))
                        .event("heartbeat")
                        .data("ping")
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
