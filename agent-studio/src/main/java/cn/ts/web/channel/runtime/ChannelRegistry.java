package cn.ts.web.channel.runtime;

import cn.ts.web.channel.entity.ChannelConfigEntity;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component
public class ChannelRegistry {

    private final Map<String, Function<ChannelConfigEntity, BaseChannel>> factories = new ConcurrentHashMap<>();

    public void register(String channelType, Function<ChannelConfigEntity, BaseChannel> factory) {
        factories.put(channelType.toLowerCase(), factory);
    }

    public BaseChannel create(ChannelConfigEntity config) {
        Function<ChannelConfigEntity, BaseChannel> factory = factories.get(config.getChannelType().toLowerCase());
        if (factory == null) {
            throw new IllegalArgumentException("Unsupported channel type: " + config.getChannelType());
        }
        return factory.apply(config);
    }
}
