package cn.ts.web.agent.controller;

import cn.ts.graph.GraphResult;
import cn.ts.web.agent.dto.AgentResponse;
import cn.ts.web.agent.service.AgentExecutionService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Agent 测试控制器
 *
 * @author tianshuo
 */
@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
public class AgentTestController {

    private final AgentExecutionService agentExecutionService;

    public AgentTestController(AgentExecutionService agentExecutionService) {
        this.agentExecutionService = agentExecutionService;
    }

    /**
     * 同步调用 Agent
     * GET /api/agent/invoke?agentName=TestAgent&message=你好
     */
    @GetMapping("/invoke")
    public String invokeAgent(
            @RequestParam String agentName,
            @RequestParam String message) {

        if (!agentExecutionService.isAgentRegistered(agentName)) {
            return "错误: Agent '" + agentName + "' 未注册";
        }

        // 使用现有的 AgentExecutionService 执行
        return agentExecutionService.executeAgentAsync(
                agentName,
                Map.of("input", message)
            )
            .map(result -> {
                if (result.isFailure()) {
                    return "错误: " + result.error().getMessage();
                }

                // 从 finalState 中获取 messages
                return result.finalState()
                        .<java.util.List<org.springframework.ai.chat.messages.Message>>value("messages")
                        .map(messages -> {
                            // 获取最后的 assistant 消息
                            for (int i = messages.size() - 1; i >= 0; i--) {
                                Message msg = messages.get(i);
                                if (msg instanceof AssistantMessage am) {
                                    return am.getText();
                                }
                            }
                            return "无法获取响应";
                        })
                        .orElse("无法获取响应");
            })
            .block(); // 阻塞等待结果
    }

    /**
     * 流式调用 Agent（SSE）
     * GET /api/agent/stream?agentName=StreamingTestAgent&message=你好
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentResponse>> streamAgent(
            @RequestParam String agentName,
            @RequestParam String message) {

        if (!agentExecutionService.isAgentRegistered(agentName)) {
            AgentResponse errorResp = AgentResponse.error(null, "Agent '" + agentName + "' 未注册");
            return Flux.just(ServerSentEvent.<AgentResponse>builder().data(errorResp).build());
        }

        return agentExecutionService.executeAgentStream(
                agentName,
                Map.of("input", message)
        ).map(response -> ServerSentEvent.<AgentResponse>builder()
                .data(response)
                .event(response.getEventType())
                .id(response.getExecutionId())
                .build());
    }
}
