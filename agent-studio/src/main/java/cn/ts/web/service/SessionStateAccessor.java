package cn.ts.web.service;

import cn.ts.graph.checkpoint.StateSnapshot;
import cn.ts.graph.util.TypeSafeStateUtils;
import cn.ts.web.shared.constant.SessionConstants;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class SessionStateAccessor {

    Map<String, Object> createInitialState(String agentName) {
        Map<String, Object> state = new HashMap<>();
        state.put(SessionConstants.StateKeys.MESSAGES, new ArrayList<>());
        state.put(SessionConstants.StateKeys.CURRENT_AGENT, agentName);
        state.put(SessionConstants.StateKeys.AGENT_HISTORY, new ArrayList<>());
        state.put(SessionConstants.StateKeys.ITERATION, SessionConstants.Defaults.DEFAULT_ITERATION);
        return state;
    }

    Map<String, Object> stateWithSwitchedAgent(StateSnapshot latestSnapshot, String newAgentName) {
        Map<String, Object> state = new HashMap<>(latestSnapshot.getState());
        state.put(SessionConstants.StateKeys.CURRENT_AGENT, newAgentName);
        return state;
    }

    Map<String, Object> stateWithAppendedMessage(StateSnapshot latestSnapshot, String role, String content) {
        Map<String, Object> state = new HashMap<>(latestSnapshot.getState());
        List<Map<String, Object>> messages = TypeSafeStateUtils.getListFromMapOrEmpty(state, SessionConstants.StateKeys.MESSAGES);
        Map<String, Object> message = new HashMap<>();
        message.put("id", UUID.randomUUID().toString());
        message.put("role", role);
        message.put("content", content);
        message.put("timestamp", Instant.now().toString());
        messages.add(message);
        state.put(SessionConstants.StateKeys.MESSAGES, messages);
        return state;
    }

    int messageCount(StateSnapshot snapshot) {
        if (snapshot == null) {
            return 0;
        }
        List<?> messages = TypeSafeStateUtils.getListFromMapOrEmpty(snapshot.getState(), SessionConstants.StateKeys.MESSAGES);
        return messages.size();
    }
}
