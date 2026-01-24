package cn.ts.web.config;

import cn.ts.agent.core.ReactAgent;
import cn.ts.agent.mcp.McpManager;
import cn.ts.agent.mcp.model.McpConnectionConfig;
import cn.ts.agent.mcp.model.McpConnectionType;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public AgentConfig(ChatModel chatModel,
                       AgentExecutionService agentExecutionService,
                       McpManager mcpManager) {
        this.chatModel = chatModel;
        this.agentExecutionService = agentExecutionService;
        this.mcpManager = mcpManager;
    }

    /**
     * 应用启动后创建并注册 Agent
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerAgents() {
//         先注册 MCP 服务（高德地图等）
        registerMcpServers();

        // 等待 MCP 连接建立
        waitForMcpConnections();

//         准备工具列表（包括普通工具和 MCP 客户端）
        List<Object> tools = new ArrayList<>();
        tools.add(new SimpleTools());

//         添加所有 MCP 客户端作为工具
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

        // 注册高德地图 MCP
        registerAmapMcp();

        // 可以在这里添加更多 MCP 服务器
        // registerWeatherMcp();
        // registerFileSystemMcp();

        logger.info("MCP 服务器注册完成");
    }

    /**
     * 注册高德地图 MCP 服务
     */
    private void registerAmapMcp() {
        logger.info("注册高德地图 MCP 服务...");

        // 构建环境变量
        Map<String, String> env = new HashMap<>();
        // 如果需要高德 API Key，在这里添加
         env.put("AMAP_MAPS_API_KEY", "7553ed7c2b3727253b52f522543f77ee");

        // 构建 STDIO 配置
        McpConnectionConfig.StdioConfig stdioConfig = McpConnectionConfig.StdioConfig.builder()
                .command("D:\\Java\\nodejs\\npx.cmd")
                .args(Arrays.asList("-y", "@amap/amap-maps-mcp-server"))
                .env(env)
                .build();

        // 构建连接配置
        McpConnectionConfig config = McpConnectionConfig.builder()
                .name("amap")
                .type(McpConnectionType.STDIO)
                .description("高德地图 MCP 服务")
                .stdioConfig(stdioConfig)
                .timeout(Duration.ofSeconds(30))
                .autoReconnect(true)
                .maxRetries(3)
                .retryInterval(Duration.ofSeconds(5))
                .build();

        // 注册连接
        boolean success = mcpManager.registerConnection(config);

        if (success) {
            logger.info("高德地图 MCP 服务注册成功");
        } else {
            logger.error("高德地图 MCP 服务注册失败");
        }
    }

    /**
     * 等待 MCP 连接建立
     */
    private void waitForMcpConnections() {
        logger.info("等待 MCP 连接建立...");

        // 等待 3 秒让 MCP 连接有时间建立
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            logger.warn("等待 MCP 连接被中断", e);
            Thread.currentThread().interrupt();
        }

        // 输出连接状态
        var statistics = mcpManager.getStatistics();
        logger.info("MCP 连接状态 - 总数: {}, 已连接: {}, 错误: {}",
                statistics.getTotalConnections(),
                statistics.getConnectedCount(),
                statistics.getErrorCount());

        // 输出工具数量
        logger.info("MCP 工具总数: {}", statistics.getTotalTools());
    }
}
