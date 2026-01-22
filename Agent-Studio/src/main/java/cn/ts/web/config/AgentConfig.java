package cn.ts.web.config;

import cn.ts.agent.core.ReactAgent;
import cn.ts.web.service.AgentExecutionService;
import cn.ts.web.tools.SimpleTools;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Agent 配置类
 * 负责创建和注册 ReActAgent 实例
 *
 * @author tianshuo
 */
@Configuration
public class AgentConfig {

    private final ChatModel chatModel;
    private final AgentExecutionService agentExecutionService;
    private final SimpleTools simpleTools;

    public AgentConfig(ChatModel chatModel,
                       AgentExecutionService agentExecutionService,
                       SimpleTools simpleTools) {
        this.chatModel = chatModel;
        this.agentExecutionService = agentExecutionService;
        this.simpleTools = simpleTools;
    }

    /**
     * 应用启动后创建并注册 Agent
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerAgents() {
        // 创建一个简单的测试 Agent（非流式，不带工具）
        ReactAgent testAgent = new ReactAgent(
                "TestAgent",
                "一个简单的测试助手，可以回答问题",
                chatModel,
                false,
                new Object[] {simpleTools}
        );

        // 注册到 AgentExecutionService，使其可通过 SSE 端点访问
        agentExecutionService.registerGraph(testAgent.getName(), testAgent.getGraph());

        System.out.println("Agent '" + testAgent.getName() + "' 已注册");

        // 创建流式测试 Agent（带工具）
        ReactAgent streamingAgent =new ReactAgent(
                "StreamingTestAgent",
                "流式测试助手，可以实时输出响应和使用工具",
                chatModel,
                true,
                new Object[] {simpleTools}
        );

        // 注册流式 Agent
        agentExecutionService.registerGraph(streamingAgent.getName(), streamingAgent.getGraph());

        System.out.println("流式 Agent '" + streamingAgent.getName() + "' 已注册");
    }
}
