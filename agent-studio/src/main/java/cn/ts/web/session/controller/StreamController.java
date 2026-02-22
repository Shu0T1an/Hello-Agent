package cn.ts.web.session.controller;

import cn.ts.web.shared.config.AgentExecutionConfig;
import cn.ts.web.agent.dto.AgentResponse;
import cn.ts.web.dto.CitationReference;
import cn.ts.web.session.dto.GraphStateVO;
import cn.ts.web.dto.TemporaryFileContent;
import cn.ts.web.agent.service.AgentExecutionService;
import cn.ts.web.service.CitationService;
import cn.ts.web.session.service.GraphStateService;
import cn.ts.web.session.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import jakarta.validation.Valid;

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

    private static final Logger logger = LoggerFactory.getLogger(StreamController.class);

    private final AgentExecutionService agentExecutionService;
    private final SessionService sessionService;
    private final AgentExecutionConfig config;
    private final GraphStateService graphStateService;
    private final CitationService citationService;

    public StreamController(
            AgentExecutionService agentExecutionService,
            SessionService sessionService,
            AgentExecutionConfig config,
            GraphStateService graphStateService,
            CitationService citationService) {
        this.agentExecutionService = agentExecutionService;
        this.sessionService = sessionService;
        this.config = config;
        this.graphStateService = graphStateService;
        this.citationService = citationService;
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
            @Valid @RequestBody cn.ts.web.agent.dto.AgentExecuteRequest request) {

        // 如果提供了 sessionId，检查是否需要自动切换 Agent
        String sessionId = request.getSessionId();
        if (sessionId != null && !sessionId.isEmpty()) {
            sessionService.getSession(sessionId).ifPresent(sessionDetail -> {
                // 如果会话当前 Agent 与请求的 Agent 不同，自动切换
                // 注意：getAgentName() 可能为 null（会话创建时未设置 Agent）
                String currentAgentName = sessionDetail.getAgentName();
                if (currentAgentName != null && !currentAgentName.equals(agentName)) {
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
//        List<Message> messages = new ArrayList<>();

        // 如果有 sessionId，从会话服务加载历史消息
//        if (sessionId != null && !sessionId.isEmpty()) {
//            sessionService.getSession(sessionId).ifPresent(sessionDetail -> {
//                if (sessionDetail.getMessages() != null) {
//                    for (SessionDetailDTO.SessionMessage msg : sessionDetail.getMessages()) {
//                        Message message = convertToSpringAIMessage(msg);
//                        if (message != null) {
//                            messages.add(message);
//                        }
//                    }
//                }
//            });
//        }

        // 添加当前用户输入

        // 创建 messages 副本，避免 Graph 执行修改原始列表


        // 合并初始状态 - 创建可变副本避免修改不可变集合
        GraphStateVO initialState = graphStateService.getGraphState(sessionId);
        Map<String, Object> stateData = initialState.getStateData();
        Map<String, Object> mergedState = new HashMap<>(stateData);
        List<Message> messages = (List<Message>)mergedState.get("messages");

        // 处理临时文件内容和引用
        List<TemporaryFileContent> fileContents = request.getFileContents();
        String enhancedInput = request.getInput();

        if (fileContents != null && !fileContents.isEmpty()) {
            // 构建带引用标记的上下文
            String annotatedContext = citationService.buildAnnotatedContext(fileContents);
            String citationInstruction = citationService.buildCitationInstruction(annotatedContext);

            // 添加引用系统消息
            messages.add(new SystemMessage(citationInstruction));

            logger.info("已添加临时文件引用上下文，文件数: {}", fileContents.size());
        }

        // 保存用户输入，用于 extractUserInput 提取
        if (enhancedInput != null && !enhancedInput.isEmpty()) {
            mergedState.put("input", enhancedInput);
            messages.add(new UserMessage(enhancedInput));
        }

        // 确定超时时间
        java.time.Duration actualTimeout = request.getTimeout() != null
                ? java.time.Duration.ofSeconds(request.getTimeout())
                : config.getTimeout();

        // 存储文件内容到状态中，用于后续引用提取
        if (fileContents != null && !fileContents.isEmpty()) {
            mergedState.put("fileContents", fileContents);
        }

        return agentExecutionService.executeAgentStreamWithSession(
                        agentName,
                        mergedState.isEmpty() ? null : mergedState,
                        sessionId,
                        actualTimeout)
                .map(response -> {
                    // 如果有文件内容且执行完成，提取引用信息
                    if ("completed".equals(response.getEventType())) {
//                        logger.info("=== completed 事件处理 ===");
//                        logger.info("fileContents: {}", fileContents != null ? "存在 (" + fileContents.size() + " 个文件)" : "null");
//                        logger.info("nodeId: {}", response.getNodeId());
//                        logger.info("message: {}", response.getMessage() != null ? "存在 (" + response.getMessage().substring(0, Math.min(100, response.getMessage().length())) + "...)" : "null");

                        if (fileContents != null && !fileContents.isEmpty() && response.getMessage() != null) {
                            List<CitationReference> citations = citationService.extractCitations(
                                    response.getMessage(),
                                    fileContents
                            );
//                            logger.info("提取到 {} 个引用", citations.size());

                            if (!citations.isEmpty()) {
                                Map<String, Object> metadata = response.getMetadata();
                                if (metadata == null) {
                                    metadata = new HashMap<>();
                                }
                                metadata.put("citations", citations);
                                response.setMetadata(metadata);

                                logger.info("已将 citations 添加到 metadata");
                            }
                        } else {
                            logger.warn("引用提取条件不满足: fileContents={}, message={}",
                                fileContents != null && !fileContents.isEmpty(),
                                response.getMessage() != null);
                        }
                    }
                    return response;
                })
                .map(response -> ServerSentEvent.<AgentResponse>builder()
                        .data(response)
                        .id(response.getExecutionId())
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

    /**
     * 恢复执行（从中断处继续）
     * <p>
     * 当用户提交反馈后，从检查点恢复执行
     * </p>
     *
     * @param agentName Agent 名称
     * @param request   恢复请求
     * @return SSE 事件流
     */
    @PostMapping(value = "/agent/{agentName}/resume", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentResponse>> resumeExecution(
            @PathVariable String agentName,
            @Valid @RequestBody cn.ts.web.agent.dto.AgentResumeRequest request) {

        // 确定超时时间
        java.time.Duration actualTimeout = request.getTimeout() != null
                ? java.time.Duration.ofSeconds(request.getTimeout())
                : config.getTimeout();

        return agentExecutionService.resumeAgentStream(
                        agentName,
                        request.getCheckpointId(),
                        request.getFeedbackData(),
                        request.getSessionId(),
                        actualTimeout)
                .map(response -> ServerSentEvent.<AgentResponse>builder()
                        .data(response)
                        .id(response.getExecutionId())
                        .build());
    }
}
