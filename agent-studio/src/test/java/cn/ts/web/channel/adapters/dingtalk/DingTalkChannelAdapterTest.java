package cn.ts.web.channel.adapters.dingtalk;

import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;
import cn.ts.web.channel.dto.ChannelInboundMessage;
import cn.ts.web.channel.entity.ChannelConfigEntity;
import cn.ts.web.channel.runtime.ChannelMessageDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DingTalkChannelAdapterTest {

    @Test
    void lifecycleAndMetadata_ShouldWorkAsExpected() throws Exception {
        ChannelMessageDispatcher dispatcher = mock(ChannelMessageDispatcher.class);
        DingTalkStreamClientFactory streamClientFactory = mock(DingTalkStreamClientFactory.class);
        DingTalkBotReplyService botReplyService = mock(DingTalkBotReplyService.class);
        OpenDingTalkClient streamClient = mock(OpenDingTalkClient.class);
        ChannelConfigEntity config = new ChannelConfigEntity();
        config.setId(10L);
        config.setChannelType("dingtalk");
        config.setChannelName("ding-main");
        config.setConfigJson("{\"clientId\":\"cid\",\"clientSecret\":\"sec\"}");
        when(streamClientFactory.create(eq("cid"), eq("sec"), any(OpenDingTalkCallbackListener.class)))
                .thenReturn(streamClient);
        DingTalkChannelAdapter adapter = new DingTalkChannelAdapter(
                config, dispatcher, streamClientFactory, botReplyService, new ObjectMapper());

        assertEquals(10L, adapter.configId());
        assertEquals("dingtalk", adapter.channelType());
        assertEquals("ding-main", adapter.channelName());
        assertFalse(adapter.healthy());

        adapter.start();
        assertTrue(adapter.healthy());

        adapter.stop();
        verify(streamClient).start();
        verify(streamClient).stop();
        assertFalse(adapter.healthy());
    }

    @Test
    void onMessage_ShouldDispatchToRuntime() {
        ChannelMessageDispatcher dispatcher = mock(ChannelMessageDispatcher.class);
        DingTalkStreamClientFactory streamClientFactory = mock(DingTalkStreamClientFactory.class);
        DingTalkBotReplyService botReplyService = mock(DingTalkBotReplyService.class);
        ChannelConfigEntity config = new ChannelConfigEntity();
        config.setId(11L);
        config.setChannelType("dingtalk");
        config.setChannelName("ding-main");
        DingTalkChannelAdapter adapter = new DingTalkChannelAdapter(
                config, dispatcher, streamClientFactory, botReplyService, new ObjectMapper());

        ChannelInboundMessage message = new ChannelInboundMessage();
        message.setText("hello");
        adapter.onMessage(message);

        verify(dispatcher).dispatch(message);
    }

    @Test
    void handleStreamEvent_ShouldDispatchMessageAndAckSuccess() throws Exception {
        ChannelMessageDispatcher dispatcher = mock(ChannelMessageDispatcher.class);
        DingTalkStreamClientFactory streamClientFactory = mock(DingTalkStreamClientFactory.class);
        DingTalkBotReplyService botReplyService = mock(DingTalkBotReplyService.class);
        OpenDingTalkClient streamClient = mock(OpenDingTalkClient.class);
        ChannelConfigEntity config = new ChannelConfigEntity();
        config.setId(12L);
        config.setChannelType("dingtalk");
        config.setChannelName("ding-main");
        config.setConfigJson("{\"clientId\":\"cid-2\",\"clientSecret\":\"sec-2\"}");

        ArgumentCaptor<OpenDingTalkCallbackListener> listenerCaptor = ArgumentCaptor.forClass(OpenDingTalkCallbackListener.class);
        when(streamClientFactory.create(eq("cid-2"), eq("sec-2"), listenerCaptor.capture()))
                .thenReturn(streamClient);

        DingTalkChannelAdapter adapter = new DingTalkChannelAdapter(
                config, dispatcher, streamClientFactory, botReplyService, new ObjectMapper());
        adapter.start();

        ChatbotMessage chatbotMessage = new ChatbotMessage();
        chatbotMessage.setSenderStaffId("staff-1");
        chatbotMessage.setConversationId("conversation-1");
        chatbotMessage.setSessionWebhook("https://example.test/webhook");
        MessageContent text = new MessageContent();
        text.setContent("hello stream");
        chatbotMessage.setText(text);

        Object callbackResponse = listenerCaptor.getValue().execute(chatbotMessage);
        assertNull(callbackResponse);

        ArgumentCaptor<Consumer<String>> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(dispatcher).dispatch(argThat(msg ->
                msg != null
                        && "hello stream".equals(msg.getText())
                        && "staff-1".equals(msg.getChannelUserId())
                        && "conversation-1".equals(msg.getChannelSessionId())
        ), callbackCaptor.capture());

        callbackCaptor.getValue().accept("bot reply");
        verify(botReplyService).replyText("https://example.test/webhook", "bot reply");
    }
}
