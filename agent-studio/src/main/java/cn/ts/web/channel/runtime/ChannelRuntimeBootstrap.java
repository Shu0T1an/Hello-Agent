package cn.ts.web.channel.runtime;

import cn.ts.web.channel.adapters.dingtalk.DingTalkChannelAdapter;
import org.springframework.stereotype.Component;

@Component
public class ChannelRuntimeBootstrap {

    public ChannelRuntimeBootstrap(ChannelRegistry registry, ChannelMessageDispatcher dispatcher) {
        registry.register("dingtalk", config -> new DingTalkChannelAdapter(config, dispatcher));
    }
}
