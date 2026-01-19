package cn.ts.agent.node;

import cn.ts.graph.node.NodeAction;
import cn.ts.graph.state.State;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具节点：处理工具调用结果
 * <p>
 * 注意：在 Spring AI 1.0.0 中，ChatClient 会自动执行工具调用。
 * 此节点主要用于记录工具调用信息，实际工具执行由 Spring AI 内部处理。
 * </p>
 *
 * @author tianshuo
 */
public class ToolNode implements NodeAction {


    private List<ToolCallback> toolCallbacks;

    @Override
    public Map<String, Object> apply(State state) throws Exception {

        List<Message> messages = state.value("messages", new ArrayList<Message>());
        Message last = messages.get(messages.size() - 1);

        if (!(last instanceof AssistantMessage am)) {
            return Map.of("messages", last);  // 不是 AssistantMessage，直接返回
        }

        if (!am.hasToolCalls()) {
            return Map.of("messages", last);  // 没有工具调用
        }

        // 执行所有工具调用
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
            ToolCallback tool = findTool(tc.name());
            if (tool != null) {
                String result = tool.call(tc.arguments(), new ToolContext(Map.of()));
                responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));
            }
        }
        // 返回工具结果
        return Map.of("messages", new ToolResponseMessage(responses)
        );
    }

    private ToolCallback findTool(String name) {

        return toolCallbacks.stream()
                .filter(t -> t.getToolDefinition().name().equals(name))
                .findFirst()
                .orElse(null);

    }
}
