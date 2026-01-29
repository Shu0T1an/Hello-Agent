package cn.ts.web.factory;

import cn.ts.agent.core.ReactAgent;
import cn.ts.web.dto.agent.AgentConfigDTO;
import cn.ts.web.dto.agent.ToolDefinitionDTO;
import cn.ts.web.service.ModelConfigService;
import cn.ts.web.service.ToolDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * Agent 工厂
 * <p>
 * 根据配置动态创建 ReactAgent 实例
 * </p>
 */
@Component
public class AgentFactory {

    private static final Logger logger = LoggerFactory.getLogger(AgentFactory.class);

    private final ModelConfigService modelConfigService;
    private final ToolDefinitionService toolDefinitionService;

    public AgentFactory(ModelConfigService modelConfigService,
                       ToolDefinitionService toolDefinitionService) {
        this.modelConfigService = modelConfigService;
        this.toolDefinitionService = toolDefinitionService;
    }

    /**
     * 根据配置创建 ReactAgent
     *
     * @param config Agent 配置
     * @return ReactAgent 实例
     */
    public ReactAgent createAgent(AgentConfigDTO config) {
        logger.info("Creating agent: {}", config.getAgentName());

        // 1. 创建 ChatModel
        ChatModel chatModel = createChatModel(config);

        // 2. 准备工具列表
        Object[] tools = instantiateTools(config);

        // 3. 使用 Builder 创建 ReactAgent
        ReactAgent.Builder builder = ReactAgent.builder()
                .name(config.getAgentName())
                .description(config.getDescription() != null ? config.getDescription() : config.getDisplayName())
                .chatModel(chatModel);

        // 4. 设置流式选项
        if (config.getEnableStreaming() != null) {
            builder.streaming(config.getEnableStreaming());
        }

        // 5. 设置工具
        builder.tools(tools);

        // 6. 构建 Agent
        ReactAgent agent = builder.build();

        logger.info("Agent created successfully: {}", config.getAgentName());
        return agent;
    }

    /**
     * 创建 ChatModel
     */
    private ChatModel createChatModel(AgentConfigDTO config) {
        // 从配置获取模型配置
        var modelConfig = config.getModelConfig();
        if (modelConfig == null) {
            // 如果没有直接提供，则从数据库加载
            modelConfig = modelConfigService.getModelById(config.getModelId());
        }

        if (modelConfig == null) {
            throw new IllegalArgumentException("Model configuration not found for agent: " + config.getAgentName());
        }

        // 使用 ModelConfigService 创建 ChatModel
        return modelConfigService.createChatModel(modelConfig);
    }

    /**
     * 实例化工具列表
     */
    private Object[] instantiateTools(AgentConfigDTO config) {
        var toolDefs = config.getToolDefinitions();
        if (toolDefs == null || toolDefs.isEmpty()) {
            return new Object[0];
        }

        return toolDefinitionService.instantiateTools(toolDefs);
    }
}
