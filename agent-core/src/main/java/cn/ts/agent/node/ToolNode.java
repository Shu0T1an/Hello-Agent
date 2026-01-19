package cn.ts.agent.node;

import cn.ts.graph.node.NodeAction;
import cn.ts.graph.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具节点：处理工具调用
 * <p>
 * 功能：
 * 1. 从 State 中获取 AssistantMessage 的 toolCalls
 * 2. 执行工具调用
 * 3. 将 ToolResponseMessage 追加到 messages 列表
 * 4. 递增 iteration 计数
 * </p>
 *
 * @author tianshuo
 */
public class ToolNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(ToolNode.class);

    private final List<ToolCallback> toolCallbacks;

    /**
     * 创建工具节点（无工具）
     */
    public ToolNode() {
        this.toolCallbacks = new ArrayList<>();
    }

    /**
     * 创建工具节点（使用工具对象数组）
     * <p>
     * 注意：需要手动将工具对象转换为 ToolCallback
     * </p>
     *
     * @param tools 工具对象数组
     */
    public ToolNode(Object... tools) {
        this.toolCallbacks = new ArrayList<>();
        // TODO: 实现从工具对象提取 ToolCallback 的逻辑
        logger.warn("ToolNode(Object... tools) constructor not fully implemented yet");
    }

    /**
     * 创建工具节点（使用 ToolCallback 列表）
     *
     * @param toolCallbacks 工具回调列表
     */
    public ToolNode(List<ToolCallback> toolCallbacks) {
        this.toolCallbacks = toolCallbacks != null
                ? new ArrayList<>(toolCallbacks)
                : new ArrayList<>();
    }

    @Override
    public Map<String, Object> apply(State state) throws Exception {
        logger.debug("ToolNode processing state: {}", state);

        // 1. 获取当前消息列表
        List<Message> messages = new ArrayList<>(
                state.value("messages", new ArrayList<Message>())
        );

        // 2. 检查是否有消息
        if (messages.isEmpty()) {
            logger.debug("No messages in state, returning empty result");
            return Map.of();
        }

        // 3. 获取最后一条消息
        Message last = messages.get(messages.size() - 1);

        // 4. 检查是否为 AssistantMessage
        if (!(last instanceof AssistantMessage am)) {
            logger.debug("Last message is not AssistantMessage, type: {}", last.getClass());
            return Map.of();
        }

        // 5. 检查是否有工具调用
        if (!am.hasToolCalls()) {
            logger.debug("AssistantMessage has no tool calls");
            return Map.of();
        }

        // 6. 执行所有工具调用
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
            logger.debug("Executing tool call: {}", tc.name());
            ToolCallback tool = findTool(tc.name());
            if (tool != null) {
                try {
                    String result = tool.call(tc.arguments(), new ToolContext(Map.of()));
                    responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));
                    logger.debug("Tool {} executed successfully", tc.name());
                } catch (Exception e) {
                    logger.error("Tool {} execution failed: {}", tc.name(), e.getMessage());
                    responses.add(new ToolResponseMessage.ToolResponse(
                            tc.id(), tc.name(), "Error: " + e.getMessage()
                    ));
                }
            } else {
                logger.warn("Tool not found: {}", tc.name());
                responses.add(new ToolResponseMessage.ToolResponse(
                        tc.id(), tc.name(), "Error: Tool not found: " + tc.name()
                ));
            }
        }

        // 7. 将 ToolResponseMessage 添加到 messages 列表
        messages.add(new ToolResponseMessage(responses));

        // 8. 递增 iteration
        int iteration = state.<Integer>value("iteration").orElse(0);
        int nextIteration = iteration + 1;
        logger.debug("ToolNode completed, iteration: {} -> {}", iteration, nextIteration);

        return Map.of(
                "messages", messages,
                "iteration", nextIteration
        );
    }

    /**
     * 查找工具
     *
     * @param name 工具名称
     * @return ToolCallback 或 null
     */
    private ToolCallback findTool(String name) {
        return toolCallbacks.stream()
                .filter(t -> t.getToolDefinition().name().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * 添加工具回调
     *
     * @param toolCallback 工具回调
     */
    public void addToolCallback(ToolCallback toolCallback) {
        this.toolCallbacks.add(toolCallback);
    }

    /**
     * 获取工具回调列表
     *
     * @return 工具回调列表的不可修改副本
     */
    public List<ToolCallback> getToolCallbacks() {
        return List.copyOf(toolCallbacks);
    }
}
