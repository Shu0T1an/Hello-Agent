package cn.ts.web.channel.adapters.dingtalk;

import com.dingtalk.open.app.api.chatbot.BotReplier;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class DingTalkBotReplyService {

    private static final int MAX_TEXT_LENGTH = 4000;

    public void replyText(String sessionWebhook, String text) throws IOException {
        if (isBlank(sessionWebhook) || isBlank(text)) {
            return;
        }
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return;
        }
        BotReplier.fromWebhook(sessionWebhook).replyText(normalized);
    }

    private String normalize(String text) {
        String normalized = text.trim();
        if (normalized.length() <= MAX_TEXT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_TEXT_LENGTH);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
