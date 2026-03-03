package cn.ts.web.channel.runtime;

import cn.ts.web.channel.entity.ChannelConfigEntity;
import cn.ts.web.channel.mapper.ChannelConfigMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChannelRuntimeManager {

    private static final Logger logger = LoggerFactory.getLogger(ChannelRuntimeManager.class);

    private final ChannelConfigMapper channelConfigMapper;
    private final ChannelRegistry channelRegistry;
    private final Map<Long, BaseChannel> runningChannels = new ConcurrentHashMap<>();

    public ChannelRuntimeManager(ChannelConfigMapper channelConfigMapper,
                                 ChannelRegistry channelRegistry) {
        this.channelConfigMapper = channelConfigMapper;
        this.channelRegistry = channelRegistry;
    }

    @PostConstruct
    public void bootstrap() {
        try {
            channelConfigMapper.selectEnabled().forEach(this::startChannel);
        } catch (RuntimeException e) {
            // Keep startup compatible with test contexts where channel schema is not initialized.
            logger.warn("Skip channel runtime bootstrap: {}", e.getMessage());
        }
    }

    public void refresh(Long channelConfigId) {
        try {
            stopChannel(channelConfigId);
            ChannelConfigEntity config = channelConfigMapper.selectById(channelConfigId);
            if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
                startChannel(config);
            }
        } catch (RuntimeException e) {
            logger.warn("Skip channel refresh for {}: {}", channelConfigId, e.getMessage());
        }
    }

    public boolean health(Long channelConfigId) {
        BaseChannel channel = runningChannels.get(channelConfigId);
        return channel != null && channel.healthy();
    }

    public BaseChannel getRunningChannel(Long channelConfigId) {
        return runningChannels.get(channelConfigId);
    }

    private void startChannel(ChannelConfigEntity config) {
        BaseChannel channel = channelRegistry.create(config);
        channel.start();
        runningChannels.put(config.getId(), channel);
    }

    private void stopChannel(Long channelConfigId) {
        BaseChannel channel = runningChannels.remove(channelConfigId);
        if (channel != null) {
            channel.stop();
        }
    }
}
