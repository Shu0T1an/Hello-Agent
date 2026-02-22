package cn.ts.agent.core;

import cn.ts.agent.api.AgentResult;
import cn.ts.agent.constant.StateKeys;
import cn.ts.graph.GraphResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.ArrayList;
import java.util.List;

class AgentResultMapper {

    AgentResult map(GraphResult graphResult) {
        if (graphResult.isFailure()) {
            return AgentResult.failure(graphResult.error());
        }

        List<Message> messages = graphResult.finalState()
                .<List<Message>>value(StateKeys.MESSAGES)
                .orElse(new ArrayList<>());

        String output = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof AssistantMessage am) {
                output = am.getText();
                break;
            }
        }

        if (output.isEmpty()) {
            ChatResponse response = graphResult.finalState()
                    .<ChatResponse>value(StateKeys.CHAT_RESPONSE)
                    .orElse(null);
            if (response != null && !response.getResults().isEmpty()) {
                output = response.getResults().get(0).getOutput().getText();
            }
        }

        return AgentResult.success(output, graphResult);
    }
}
