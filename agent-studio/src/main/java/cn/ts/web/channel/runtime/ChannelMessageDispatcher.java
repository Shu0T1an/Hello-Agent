package cn.ts.web.channel.runtime;

import cn.ts.agent.constant.StateKeys;
import cn.ts.web.agent.service.AgentExecutionService;
import cn.ts.web.channel.dto.ChannelInboundMessage;
import cn.ts.web.session.service.SessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class ChannelMessageDispatcher {

    private final AgentExecutionService agentExecutionService;
    private final SessionService sessionService;

    @Value("${agent.channel.default-agent:general-purpose}")
    private String defaultAgent;

    public ChannelMessageDispatcher(AgentExecutionService agentExecutionService,
                                    SessionService sessionService) {
        this.agentExecutionService = agentExecutionService;
        this.sessionService = sessionService;
    }

    public void dispatch(ChannelInboundMessage message) {
        String agentName = (message.getAgentName() == null || message.getAgentName().isBlank())
                ? defaultAgent
                : message.getAgentName().trim();

        String sessionId = message.getChannelSessionId();
        if (sessionId == null || sessionId.isBlank() || !sessionService.sessionExists(sessionId)) {
            sessionId = sessionService.createSession(agentName, "Channel Session").getId();
        }

        Map<String, Object> state = new HashMap<>();
        state.put(StateKeys.INPUT, message.getText());

        agentExecutionService.executeAgentStreamWithSession(agentName, state, sessionId, Duration.ofSeconds(120))
                .subscribe();
    }
}
