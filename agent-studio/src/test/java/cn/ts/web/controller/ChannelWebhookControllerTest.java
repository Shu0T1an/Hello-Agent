package cn.ts.web.controller;

import cn.ts.web.channel.controller.ChannelWebhookController;
import cn.ts.web.channel.runtime.BaseChannel;
import cn.ts.web.channel.runtime.ChannelRuntimeManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChannelWebhookController.class)
class ChannelWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChannelRuntimeManager channelRuntimeManager;

    @MockBean
    private BaseChannel baseChannel;

    @Test
    void webhook_ShouldAcceptGenericPayload() throws Exception {
        when(channelRuntimeManager.getRunningChannel(1L)).thenReturn(baseChannel);
        when(baseChannel.channelType()).thenReturn("dingtalk");

        String payload = """
                {
                  "channelUserId": "u-1",
                  "channelSessionId": "s-1",
                  "text": "hello"
                }
                """;

        mockMvc.perform(post("/api/channels/webhook/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true));

        verify(baseChannel).onMessage(argThat(matchesMessage("hello", "u-1", "s-1")));
    }

    @Test
    void webhook_ShouldParseDingTalkRawPayload() throws Exception {
        when(channelRuntimeManager.getRunningChannel(2L)).thenReturn(baseChannel);
        when(baseChannel.channelType()).thenReturn("dingtalk");

        String payload = """
                {
                  "senderStaffId": "staff-1",
                  "conversationId": "conversation-1",
                  "text": {
                    "content": "你好"
                  }
                }
                """;

        mockMvc.perform(post("/api/channels/webhook/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true));

        verify(baseChannel).onMessage(argThat(matchesMessage("你好", "staff-1", "conversation-1")));
    }

    private ArgumentMatcher<cn.ts.web.channel.dto.ChannelInboundMessage> matchesMessage(
            String text,
            String userId,
            String sessionId
    ) {
        return message -> message != null
                && text.equals(message.getText())
                && userId.equals(message.getChannelUserId())
                && sessionId.equals(message.getChannelSessionId());
    }
}
