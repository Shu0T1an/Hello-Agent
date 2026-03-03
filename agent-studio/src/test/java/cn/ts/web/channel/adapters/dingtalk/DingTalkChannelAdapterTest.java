package cn.ts.web.channel.adapters.dingtalk;

import cn.ts.web.channel.dto.ChannelInboundMessage;
import cn.ts.web.channel.entity.ChannelConfigEntity;
import cn.ts.web.channel.runtime.ChannelMessageDispatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DingTalkChannelAdapterTest {

    @Test
    void lifecycleAndMetadata_ShouldWorkAsExpected() {
        ChannelMessageDispatcher dispatcher = mock(ChannelMessageDispatcher.class);
        ChannelConfigEntity config = new ChannelConfigEntity();
        config.setId(10L);
        config.setChannelType("dingtalk");
        config.setChannelName("ding-main");
        DingTalkChannelAdapter adapter = new DingTalkChannelAdapter(config, dispatcher);

        assertEquals(10L, adapter.configId());
        assertEquals("dingtalk", adapter.channelType());
        assertEquals("ding-main", adapter.channelName());
        assertFalse(adapter.healthy());

        adapter.start();
        assertTrue(adapter.healthy());

        adapter.stop();
        assertFalse(adapter.healthy());
    }

    @Test
    void onMessage_ShouldDispatchToRuntime() {
        ChannelMessageDispatcher dispatcher = mock(ChannelMessageDispatcher.class);
        ChannelConfigEntity config = new ChannelConfigEntity();
        config.setId(11L);
        config.setChannelType("dingtalk");
        config.setChannelName("ding-main");
        DingTalkChannelAdapter adapter = new DingTalkChannelAdapter(config, dispatcher);

        ChannelInboundMessage message = new ChannelInboundMessage();
        message.setText("hello");
        adapter.onMessage(message);

        verify(dispatcher).dispatch(message);
    }
}
