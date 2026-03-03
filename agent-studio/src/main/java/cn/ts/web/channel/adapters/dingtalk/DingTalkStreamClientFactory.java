package cn.ts.web.channel.adapters.dingtalk;

import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import org.springframework.stereotype.Component;

@Component
public class DingTalkStreamClientFactory {

    public OpenDingTalkClient create(String clientId,
                                     String clientSecret,
                                     OpenDingTalkCallbackListener<ChatbotMessage, Void> listener) {
        return OpenDingTalkStreamClientBuilder.custom()
                .credential(new AuthClientCredential(clientId, clientSecret))
                .registerCallbackListener(DingTalkStreamTopics.BOT_MESSAGE_TOPIC, listener)
                .build();
    }
}
