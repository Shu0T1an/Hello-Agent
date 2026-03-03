package cn.ts.web.channel.runtime;

import cn.ts.web.channel.dto.ChannelInboundMessage;

public interface BaseChannel {

    Long configId();

    String channelType();

    String channelName();

    void start();

    void stop();

    boolean healthy();

    default void onMessage(ChannelInboundMessage message) {
        // no-op by default
    }
}
