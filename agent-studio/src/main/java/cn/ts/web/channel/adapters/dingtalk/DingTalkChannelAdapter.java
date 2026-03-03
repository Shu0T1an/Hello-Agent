package cn.ts.web.channel.adapters.dingtalk;

import cn.ts.web.channel.dto.ChannelInboundMessage;
import cn.ts.web.channel.entity.ChannelConfigEntity;
import cn.ts.web.channel.runtime.BaseChannel;
import cn.ts.web.channel.runtime.ChannelMessageDispatcher;

import java.util.concurrent.atomic.AtomicBoolean;

public class DingTalkChannelAdapter implements BaseChannel {

    private final ChannelConfigEntity config;
    private final ChannelMessageDispatcher dispatcher;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DingTalkChannelAdapter(ChannelConfigEntity config, ChannelMessageDispatcher dispatcher) {
        this.config = config;
        this.dispatcher = dispatcher;
    }

    @Override
    public Long configId() {
        return config.getId();
    }

    @Override
    public String channelType() {
        return config.getChannelType();
    }

    @Override
    public String channelName() {
        return config.getChannelName();
    }

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        running.set(false);
    }

    @Override
    public boolean healthy() {
        return running.get();
    }

    @Override
    public void onMessage(ChannelInboundMessage message) {
        dispatcher.dispatch(message);
    }
}
