package cn.ts.web.service;

import cn.ts.agent.util.MessageUtils;
import cn.ts.graph.checkpoint.StateSnapshot;
import cn.ts.web.dto.SessionDetailDTO;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class SessionMessageAssembler {

    List<SessionDetailDTO.SessionMessage> fromSnapshot(StateSnapshot snapshot) {
        if (snapshot == null) {
            return new ArrayList<>();
        }
        Object messagesObj = snapshot.getState().get("messages");
        if (!(messagesObj instanceof List<?> messageList)) {
            return new ArrayList<>();
        }
        return convertMessagesToSessionMessages(messageList);
    }

    private List<SessionDetailDTO.SessionMessage> convertMessagesToSessionMessages(List<?> messageList) {
        List<SessionDetailDTO.SessionMessage> result = new ArrayList<>();
        for (Object item : messageList) {
            if (item == null) {
                continue;
            }
            if (item instanceof Message message) {
                SessionDetailDTO.SessionMessage sessionMessage = new SessionDetailDTO.SessionMessage();
                sessionMessage.setId(UUID.randomUUID().toString());
                sessionMessage.setContent(MessageUtils.MessageExtractor.extractContent(message));
                sessionMessage.setRole(MessageUtils.MessageExtractor.extractRole(message));
                sessionMessage.setTimestamp(Instant.now());
                sessionMessage.setMetadata(MessageUtils.MessageExtractor.extractMetadata(message));
                result.add(sessionMessage);
                continue;
            }
            throw new IllegalArgumentException("Unknown message type: " + item.getClass());
        }
        return result;
    }
}
