package cn.ts.web.channel.service;

import cn.ts.web.channel.dto.ChannelConfigDTO;
import cn.ts.web.channel.entity.ChannelConfigEntity;
import cn.ts.web.channel.mapper.ChannelConfigMapper;
import cn.ts.web.channel.runtime.ChannelRuntimeManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelConfigServiceTest {

    @Mock
    private ChannelConfigMapper channelConfigMapper;

    @Mock
    private ChannelRuntimeManager channelRuntimeManager;

    private ChannelConfigService channelConfigService;

    @BeforeEach
    void setUp() {
        channelConfigService = new ChannelConfigService(
                channelConfigMapper,
                channelRuntimeManager,
                new ObjectMapper()
        );
    }

    @Test
    void createDingTalkChannel_MissingClientId_ShouldThrow() {
        ChannelConfigDTO dto = buildDingtalkDTO(Map.of("clientSecret", "secret-value"));

        assertThrows(IllegalArgumentException.class, () -> channelConfigService.create(dto));

        verify(channelConfigMapper, never()).insert(ArgumentMatchers.any(ChannelConfigEntity.class));
        verify(channelRuntimeManager, never()).refresh(ArgumentMatchers.anyLong());
    }

    @Test
    void updateDingTalkChannel_MissingClientSecret_ShouldThrow() {
        ChannelConfigDTO dto = buildDingtalkDTO(Map.of("clientId", "client-id"));
        ChannelConfigEntity existing = new ChannelConfigEntity();
        existing.setId(1L);
        existing.setChannelName("existing-channel");
        existing.setChannelType("dingtalk");
        when(channelConfigMapper.selectById(1L)).thenReturn(existing);

        assertThrows(IllegalArgumentException.class, () -> channelConfigService.update(1L, dto));

        verify(channelConfigMapper, never()).updateById(ArgumentMatchers.any(ChannelConfigEntity.class));
        verify(channelRuntimeManager, never()).refresh(1L);
    }

    private ChannelConfigDTO buildDingtalkDTO(Map<String, Object> config) {
        ChannelConfigDTO dto = new ChannelConfigDTO();
        dto.setChannelName("ding-talk-main");
        dto.setChannelType("dingtalk");
        dto.setConfig(config);
        dto.setEnabled(true);
        dto.setStatus("stopped");
        return dto;
    }
}
