package cn.ts.web.channel.runtime;

import cn.ts.web.channel.entity.ChannelConfigEntity;
import cn.ts.web.channel.mapper.ChannelConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelRuntimeManagerTest {

    @Mock
    private ChannelConfigMapper channelConfigMapper;

    @Mock
    private ChannelRegistry channelRegistry;

    @Mock
    private BaseChannel oldChannel;

    @Mock
    private BaseChannel newChannel;

    private ChannelRuntimeManager channelRuntimeManager;

    @BeforeEach
    void setUp() {
        channelRuntimeManager = new ChannelRuntimeManager(channelConfigMapper, channelRegistry);
    }

    @Test
    void startEnabledChannels_ShouldRegisterAndStartAdapters() {
        ChannelConfigEntity config = config(1L, true);
        when(channelConfigMapper.selectEnabled()).thenReturn(List.of(config));
        when(channelRegistry.create(config)).thenReturn(oldChannel);

        channelRuntimeManager.bootstrap();

        verify(oldChannel).start();
        assertSame(oldChannel, channelRuntimeManager.getRunningChannel(1L));
    }

    @Test
    void refreshEnabledChannel_ShouldStopPreviousAndStartNewAdapter() {
        ChannelConfigEntity enabledConfig = config(2L, true);
        when(channelConfigMapper.selectEnabled()).thenReturn(List.of(enabledConfig));
        when(channelRegistry.create(enabledConfig)).thenReturn(oldChannel, newChannel);

        channelRuntimeManager.bootstrap();

        when(channelConfigMapper.selectById(2L)).thenReturn(enabledConfig);
        channelRuntimeManager.refresh(2L);

        verify(oldChannel).start();
        verify(oldChannel).stop();
        verify(newChannel).start();
        assertSame(newChannel, channelRuntimeManager.getRunningChannel(2L));
    }

    @Test
    void refreshDisabledChannel_ShouldStopAndRemoveAdapter() {
        ChannelConfigEntity enabledConfig = config(3L, true);
        ChannelConfigEntity disabledConfig = config(3L, false);
        when(channelConfigMapper.selectEnabled()).thenReturn(List.of(enabledConfig));
        when(channelRegistry.create(enabledConfig)).thenReturn(oldChannel);

        channelRuntimeManager.bootstrap();

        when(channelConfigMapper.selectById(3L)).thenReturn(disabledConfig);
        channelRuntimeManager.refresh(3L);

        verify(oldChannel, times(1)).stop();
        assertNull(channelRuntimeManager.getRunningChannel(3L));
    }

    private ChannelConfigEntity config(Long id, boolean enabled) {
        ChannelConfigEntity config = new ChannelConfigEntity();
        config.setId(id);
        config.setChannelName("channel-" + id);
        config.setChannelType("dingtalk");
        config.setEnabled(enabled);
        return config;
    }
}
