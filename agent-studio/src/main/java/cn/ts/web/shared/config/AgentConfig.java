package cn.ts.web.shared.config;

import cn.ts.agent.tool.WriteToDoTool;
import cn.ts.agent.core.ReactAgent;
import cn.ts.agent.hook.ClarificationQaHook;
import cn.ts.agent.hook.HumanInTheLoopHook;
import cn.ts.agent.hook.LoggingHook;
import cn.ts.agent.mcp.McpManager;
import cn.ts.agent.mcp.model.McpStatistics;
import cn.ts.agent.rag.advisor.RagAdvisor;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.hook.Hook;
import cn.ts.graph.observation.GraphObservationLifecycleListener;
import cn.ts.web.agent.deepsearch.DeepSearchAgentBuilder;
import cn.ts.web.agent.interceptor.PromptAuditInterceptor;
import cn.ts.web.agent.deepsearch.DeepSearchProperties;
import cn.ts.web.agent.service.AgentExecutionService;
import cn.ts.web.tool.local.SimpleTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 配置类
 * 负责创建和注册 ReActAgent 实例
 *
 * @author tianshuo
 */
@Configuration
public class AgentConfig {

    private static final Logger logger = LoggerFactory.getLogger(AgentConfig.class);

    private final ChatModel chatModel;

    private final AgentExecutionService agentExecutionService;
    private final McpManager mcpManager;
    private final McpServerConfig mcpServerConfig;
    private final NodeJsConfig nodeJsConfig;
    private final ApiKeyConfig apiKeyConfig;
    private final VectorStore vectorStore;
    private final CheckpointManager checkpointManager;
    private final GraphObservationLifecycleListener observationListener;
    private final DeepSearchProperties deepSearchProperties;
    private final DeepSearchAgentBuilder deepSearchAgentBuilder;
    private final PromptAuditInterceptor promptAuditInterceptor;

    public AgentConfig(ChatModel chatModel,
                       AgentExecutionService agentExecutionService,
                       McpManager mcpManager,
                       McpServerConfig mcpServerConfig,
                       NodeJsConfig nodeJsConfig,
                       ApiKeyConfig apiKeyConfig,
                       @Qualifier("vectorStore") VectorStore vectorStore,
                       CheckpointManager checkpointManager,
                       GraphObservationLifecycleListener observationListener,
                       DeepSearchProperties deepSearchProperties,
                       DeepSearchAgentBuilder deepSearchAgentBuilder,
                       PromptAuditInterceptor promptAuditInterceptor) {
        this.chatModel = chatModel;
        this.agentExecutionService = agentExecutionService;
        this.mcpManager = mcpManager;
        this.mcpServerConfig = mcpServerConfig;
        this.nodeJsConfig = nodeJsConfig;
        this.apiKeyConfig = apiKeyConfig;
        this.vectorStore = vectorStore;
        this.checkpointManager = checkpointManager;
        this.observationListener = observationListener;
        this.deepSearchProperties = deepSearchProperties;
        this.deepSearchAgentBuilder = deepSearchAgentBuilder;
        this.promptAuditInterceptor = promptAuditInterceptor;
    }

    /**
     * 应用启动后创建并注册 Agent
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerAgents() {
        // 注册 MCP 服务
        registerMcpServers();
        // 等待 MCP 连接建立
        waitForMcpConnections();

        // 准备工具列表（包括普通工具和 MCP 客户端）
        List<Object> tools = new ArrayList<>();
        tools.add(new SimpleTools());
        tools.add(new WriteToDoTool());

        // 添加所有 MCP 客户端作为工具
        tools.addAll(mcpManager.getAllMcpClients());
        // ========== 创建非流式 TestAgent ==========
        List<Hook> testAgentHooks = new ArrayList<>();

        // 添加日志 Hook
        testAgentHooks.add(LoggingHook.builder()
                .prefix("[TestAgent]")
                .logMessages(true)
                .logState(true)
                .build());

        // 添加人工审批 Hook - 为敏感操作添加审批
        testAgentHooks.add(HumanInTheLoopHook.builder()
                .approvalOn("delete_todo", "删除待办事项，不可逆操作")
                .approvalOn("clear_todos", "清空所有待办事项")
                .approvalMessage("⚠️ 需要人工审批：以下操作可能影响数据，请确认是否继续")
                .build());
        testAgentHooks.add(ClarificationQaHook.builder().build());

        // 创建一个简单的测试 Agent（非流式，带工具和 Hook）
        ReactAgent testAgent = ReactAgent.builder()
                .name("TestAgent")
                .description("一个简单的测试助手，可以回答问题和使用工具，支持人工审批")
                .chatModel(chatModel)
                .streaming(false)
                .tools(tools.toArray())
                .hooks(testAgentHooks)
                .modelInterceptors(List.of(promptAuditInterceptor))
                .checkpointManager(checkpointManager)
                .addLifecycleListener(observationListener)
                .build();

        // 注册到 AgentExecutionService，使其可通过 SSE 端点访问
        agentExecutionService.registerGraph(testAgent.getName(), testAgent.getGraph());

        logger.info("Agent '{}' 已注册 (包含 {} 个 Hook)", testAgent.getName(), testAgentHooks.size());


        // ========== 创建流式 StreamingTestAgent ==========
        List<Hook> streamingAgentHooks = new ArrayList<>();

        // 添加日志 Hook
//        streamingAgentHooks.add(LoggingHook.builder()
//                .prefix("[StreamingAgent]")
//                .logMessages(true)
//                .logState(false)  // 流式模式下减少状态日志
//                .build());

        // 添加人工审批 Hook - 流式模式下也支持审批
        streamingAgentHooks.add(HumanInTheLoopHook.builder()
                .approvalOn("add", "两数相加")
                .requireApprovalForAll(false)  // 只对指定工具审批
                .approvalMessage("🤖 请审批：Agent 请求执行以下工具调用")
                .build());
        streamingAgentHooks.add(ClarificationQaHook.builder().build());

        // 创建流式测试 Agent（带工具和 Hook）
        ReactAgent streamingAgent = ReactAgent.builder()
                .name("StreamingTestAgent")
                .description("流式测试助手，可以实时输出响应和使用工具，支持人工审批")
                .chatModel(chatModel)
                .advisors(List.of(new RagAdvisor(vectorStore)))
                .streaming(true)
                .tools(tools.toArray())
                .hooks(streamingAgentHooks)
                .modelInterceptors(List.of(promptAuditInterceptor))
                .checkpointManager(checkpointManager)
                .addLifecycleListener(observationListener)
                .build();

        // 注册流式 Agent
        agentExecutionService.registerGraph(streamingAgent.getName(), streamingAgent.getGraph());

        logger.info("流式 Agent '{}' 已注册 (包含 {} 个 Hook)", streamingAgent.getName(), streamingAgentHooks.size());

        registerBuiltInDeepSearchAgent(tools.toArray());

        logger.info("所有 Agent 注册完成");
    }

    private void registerBuiltInDeepSearchAgent(Object[] tools) {
        if (!deepSearchProperties.isEnabled()) {
            logger.info("Built-in DeepSearch agent registration disabled by config.");
            return;
        }

        String agentName = deepSearchProperties.getAgentName();
        if (agentName == null || agentName.isBlank()) {
            logger.error("Skip built-in DeepSearch registration: agent.deep-search.agent-name must not be blank.");
            return;
        }

        if (agentExecutionService.isAgentRegistered(agentName)) {
            logger.warn("Skip built-in DeepSearch registration because '{}' is already registered (db/runtime priority).", agentName);
            return;
        }

        try {
            ReactAgent deepSearchAgent = deepSearchAgentBuilder.build(chatModel, tools);
            agentExecutionService.registerGraph(agentName, deepSearchAgent.getGraph());
            logger.info("Built-in DeepSearch agent '{}' registered successfully.", agentName);
        } catch (Exception e) {
            logger.error("Failed to register built-in DeepSearch agent '{}': {}", agentName, e.getMessage(), e);
        }
    }

    /**
     * 注册 MCP 服务器
     */
    private void registerMcpServers() {
        logger.info("开始注册 MCP 服务器...");

        if (mcpServerConfig.getServers() == null || mcpServerConfig.getServers().isEmpty()) {
            logger.warn("未配置任何 MCP 服务器");
            return;
        }

        // 从配置文件读取并注册所有 MCP 服务器
        for (McpServerConfig.ServerConfig serverConfig : mcpServerConfig.getServers()) {
            registerMcpServer(serverConfig);
        }

        logger.info("MCP 服务器注册完成，共 {} 个", mcpServerConfig.getServers().size());
    }

    /**
     * 注册单个 MCP 服务器
     *
     * @param serverConfig MCP 服务器配置
     */
    private void registerMcpServer(McpServerConfig.ServerConfig serverConfig) {
        logger.info("注册 MCP 服务器: {} ({})", serverConfig.getName(), serverConfig.getDescription());

        // 解析环境变量占位符
        McpServerConfig.ServerConfig resolvedConfig = resolveConfigPlaceholders(serverConfig);

        // 转换为 McpConnectionConfig 并注册
        boolean success = mcpManager.registerConnection(resolvedConfig.toMcpConnectionConfig());

        if (success) {
            logger.info("MCP 服务器 '{}' 注册成功", serverConfig.getName());
        } else {
            logger.error("MCP 服务器 '{}' 注册失败", serverConfig.getName());
        }
    }

    /**
     * 解析配置中的占位符（如 ${nodejs.npx-path}）
     *
     * @param serverConfig 原始配置
     * @return 解析后的配置
     */
    private McpServerConfig.ServerConfig resolveConfigPlaceholders(McpServerConfig.ServerConfig serverConfig) {
        // 解析 STDIO 命令路径
        if (serverConfig.getStdio() != null && serverConfig.getStdio().getCommand() != null) {
            String command = serverConfig.getStdio().getCommand();
            if ("${nodejs.npx-path}".equals(command)) {
                serverConfig.getStdio().setCommand(nodeJsConfig.getNpxPathOrDefault());
            }
        }

        // 解析环境变量中的 API 密钥
        if (serverConfig.getStdio() != null && serverConfig.getStdio().getEnv() != null) {
            serverConfig.getStdio().getEnv().forEach((key, value) -> {
                if (value != null && value.startsWith("${api.keys.")) {
                    String serviceName = extractServiceNameFromPlaceholder(value);
                    String apiKey = apiKeyConfig.getKey(serviceName);
                    if (apiKey != null) {
                        serverConfig.getStdio().getEnv().put(key, apiKey);
                    }
                }
            });
        }

        return serverConfig;
    }

    /**
     * 从占位符中提取服务名称
     * 例如: "${api.keys.amap}" -> "amap"
     *
     * @param placeholder 占位符字符串
     * @return 服务名称
     */
    private String extractServiceNameFromPlaceholder(String placeholder) {
        if (placeholder != null && placeholder.startsWith("${api.keys.") && placeholder.endsWith("}")) {
            return placeholder.substring("${api.keys.".length(), placeholder.length() - 1);
        }
        return placeholder;
    }

    /**
     * 等待 MCP 连接建立
     */
    private void waitForMcpConnections() {
        logger.info("等待 MCP 连接建立...");

        boolean connected = waitForConnections(Duration.ofSeconds(30));

        var statistics = mcpManager.getStatistics();
        logConnectionStatistics(statistics);

        if (!connected) {
            logger.warn("MCP 连接等待超时");
        }
    }

    /**
     * 等待所有连接完成
     *
     * @param timeout 超时时间
     * @return 是否所有连接都已建立
     */
    private boolean waitForConnections(Duration timeout) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeout.toMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (areAllConnectionsSettled()) {
                logger.info("MCP 连接建立完成");
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.warn("等待 MCP 连接被中断", e);
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    /**
     * 检查是否所有连接都已稳定（成功或失败）
     */
    private boolean areAllConnectionsSettled() {
        var statistics = mcpManager.getStatistics();
        int total = statistics.getTotalConnections();
        int settled = statistics.getConnectedCount() + statistics.getErrorCount();
        return total > 0 && settled >= total;
    }

    /**
     * 记录连接统计信息
     */
    private void logConnectionStatistics(McpStatistics statistics) {
        logger.info("MCP 连接状态 - 总数: {}, 已连接: {}, 错误: {}, 连接中: {}",
                statistics.getTotalConnections(),
                statistics.getConnectedCount(),
                statistics.getErrorCount(),
                statistics.getConnectingCount());
        logger.info("MCP 工具总数: {}", statistics.getTotalTools());
    }
}
