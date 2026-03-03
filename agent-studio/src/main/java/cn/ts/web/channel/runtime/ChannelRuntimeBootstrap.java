package cn.ts.web.channel.runtime;

import cn.ts.web.channel.adapters.dingtalk.DingTalkChannelAdapter;
import cn.ts.web.channel.adapters.dingtalk.DingTalkBotReplyService;
import cn.ts.web.channel.adapters.dingtalk.DingTalkStreamClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ChannelRuntimeBootstrap {

    public ChannelRuntimeBootstrap(ChannelRegistry registry,
                                   ChannelMessageDispatcher dispatcher,
                                   DingTalkStreamClientFactory streamClientFactory,
                                   DingTalkBotReplyService botReplyService,
                                   ObjectMapper objectMapper) {
        registry.register("dingtalk", config -> new DingTalkChannelAdapter(
                config,
                dispatcher,
                streamClientFactory,
                botReplyService,
                objectMapper
        ));
    }
}
