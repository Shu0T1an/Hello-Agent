package cn.ts.web.config;

import cn.ts.agent.core.ReactAgent;
import cn.ts.agent.mcp.McpManager;
import cn.ts.agent.mcp.model.McpStatistics;
import cn.ts.web.service.AgentExecutionService;
import cn.ts.web.tools.SimpleTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
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

    public AgentConfig(ChatModel chatModel,
                       AgentExecutionService agentExecutionService,
                       McpManager mcpManager,
                       McpServerConfig mcpServerConfig,
                       NodeJsConfig nodeJsConfig,
                       ApiKeyConfig apiKeyConfig) {
        this.chatModel = chatModel;
        this.agentExecutionService = agentExecutionService;
        this.mcpManager = mcpManager;
        this.mcpServerConfig = mcpServerConfig;
        this.nodeJsConfig = nodeJsConfig;
        this.apiKeyConfig = apiKeyConfig;
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

        // 添加所有 MCP 客户端作为工具
        tools.addAll(mcpManager.getAllMcpClients());

        logger.info("注册 Agent，工具数量: {}", tools.size());

        // 创建一个简单的测试 Agent（非流式，带工具）
        ReactAgent testAgent = new ReactAgent(
                "TestAgent",
                "一个简单的测试助手，可以回答问题和使用工具",
                chatModel,
                false,
                tools.toArray()
        );

        // 注册到 AgentExecutionService，使其可通过 SSE 端点访问
        agentExecutionService.registerGraph(testAgent.getName(), testAgent.getGraph());

        logger.info("Agent '{}' 已注册", testAgent.getName());

        // 创建流式测试 Agent（带工具）
        ReactAgent streamingAgent = new ReactAgent(
                "StreamingTestAgent",
                "流式测试助手，可以实时输出响应和使用工具",
                chatModel,
                true,
                tools.toArray()
        );

        // 注册流式 Agent
        agentExecutionService.registerGraph(streamingAgent.getName(), streamingAgent.getGraph());

        logger.info("流式 Agent '{}' 已注册", streamingAgent.getName());

        logger.info("所有 Agent 注册完成");
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
