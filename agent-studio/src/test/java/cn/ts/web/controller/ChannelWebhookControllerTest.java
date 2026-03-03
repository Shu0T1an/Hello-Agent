package cn.ts.web.controller;

import cn.ts.web.channel.adapters.dingtalk.DingTalkCallbackCrypto;
import cn.ts.web.channel.adapters.dingtalk.DingTalkBotReplyService;
import cn.ts.web.channel.controller.ChannelWebhookController;
import cn.ts.web.channel.dto.ChannelConfigDTO;
import cn.ts.web.channel.runtime.BaseChannel;
import cn.ts.web.channel.runtime.ChannelMessageDispatcher;
import cn.ts.web.channel.runtime.ChannelRuntimeManager;
import cn.ts.web.channel.service.ChannelConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

@WebMvcTest(ChannelWebhookController.class)
class ChannelWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChannelRuntimeManager channelRuntimeManager;

    @MockBean
    private BaseChannel baseChannel;

    @MockBean
    private ChannelConfigService channelConfigService;

    @MockBean
    private ChannelMessageDispatcher channelMessageDispatcher;

    @MockBean
    private DingTalkBotReplyService dingTalkBotReplyService;

    @MockBean
    private DingTalkCallbackCrypto dingTalkCallbackCrypto;

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

    @Test
    void dingtalkWebhook_ShouldDecryptDispatchAndReturnEncryptedAck() throws Exception {
        when(channelRuntimeManager.getRunningChannel(3L)).thenReturn(baseChannel);
        when(baseChannel.channelType()).thenReturn("dingtalk");
        ChannelConfigDTO config = new ChannelConfigDTO();
        config.setChannelType("dingtalk");
        config.setConfig(Map.of(
                "callbackToken", "token-1",
                "callbackAesKey", "aes-key-1",
                "clientId", "app-key-1"
        ));
        when(channelConfigService.getById(3L)).thenReturn(config);
        when(dingTalkCallbackCrypto.decryptAndVerify(
                eq("sig-1"),
                eq("1700000000"),
                eq("nonce-1"),
                eq("encrypted-payload"),
                any(DingTalkCallbackCrypto.DingTalkSecurityContext.class)
        )).thenReturn("""
                {
                  "senderStaffId": "staff-2",
                  "conversationId": "conversation-2",
                  "text": {
                    "content": "测试消息"
                  }
                }
                """);
        when(dingTalkCallbackCrypto.buildSuccessResponse(any(DingTalkCallbackCrypto.DingTalkSecurityContext.class)))
                .thenReturn(Map.of(
                        "msg_signature", "resp-signature",
                        "encrypt", "resp-encrypt",
                        "timeStamp", "1700000001",
                        "nonce", "nonce-2"
                ));

        mockMvc.perform(post("/api/channels/webhook/dingtalk/3")
                        .param("signature", "sig-1")
                        .param("timestamp", "1700000000")
                        .param("nonce", "nonce-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "encrypt": "encrypted-payload"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encrypt").value("resp-encrypt"))
                .andExpect(jsonPath("$.msg_signature").value("resp-signature"));

        verify(baseChannel).onMessage(argThat(matchesMessage("测试消息", "staff-2", "conversation-2")));
    }

    @Test
    void dingtalkWebhook_ShouldAcceptMsgSignatureAndAckWithoutDispatchForNonTextEvent() throws Exception {
        when(channelRuntimeManager.getRunningChannel(4L)).thenReturn(baseChannel);
        when(baseChannel.channelType()).thenReturn("dingtalk");
        ChannelConfigDTO config = new ChannelConfigDTO();
        config.setChannelType("dingtalk");
        config.setConfig(Map.of(
                "callbackToken", "token-2",
                "callbackAesKey", "aes-key-2",
                "clientId", "app-key-2"
        ));
        when(channelConfigService.getById(4L)).thenReturn(config);
        when(dingTalkCallbackCrypto.decryptAndVerify(
                eq("sig-2"),
                eq("1700000002"),
                eq("nonce-3"),
                eq("encrypted-event"),
                any(DingTalkCallbackCrypto.DingTalkSecurityContext.class)
        )).thenReturn("""
                {
                  "EventType": "check_url"
                }
                """);
        when(dingTalkCallbackCrypto.buildSuccessResponse(any(DingTalkCallbackCrypto.DingTalkSecurityContext.class)))
                .thenReturn(Map.of(
                        "msg_signature", "resp-signature-2",
                        "encrypt", "resp-encrypt-2",
                        "timeStamp", "1700000003",
                        "nonce", "nonce-4"
                ));

        mockMvc.perform(post("/api/channels/webhook/dingtalk/4")
                        .param("msg_signature", "sig-2")
                        .param("timeStamp", "1700000002")
                        .param("nonce", "nonce-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "encrypt": "encrypted-event"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encrypt").value("resp-encrypt-2"))
                .andExpect(jsonPath("$.msg_signature").value("resp-signature-2"));

        verify(baseChannel, never()).onMessage(any());
    }

    @Test
    void dingtalkWebhook_ShouldDispatchWithReplyCallback_WhenSessionWebhookPresent() throws Exception {
        when(channelRuntimeManager.getRunningChannel(5L)).thenReturn(baseChannel);
        when(baseChannel.channelType()).thenReturn("dingtalk");
        ChannelConfigDTO config = new ChannelConfigDTO();
        config.setChannelType("dingtalk");
        config.setConfig(Map.of(
                "callbackToken", "token-3",
                "callbackAesKey", "aes-key-3",
                "clientId", "app-key-3"
        ));
        when(channelConfigService.getById(5L)).thenReturn(config);
        when(dingTalkCallbackCrypto.decryptAndVerify(
                eq("sig-3"),
                eq("1700000004"),
                eq("nonce-5"),
                eq("encrypted-message"),
                any(DingTalkCallbackCrypto.DingTalkSecurityContext.class)
        )).thenReturn("""
                {
                  "data": {
                    "senderStaffId": "staff-3",
                    "conversationId": "conversation-3",
                    "sessionWebhook": "https://example.com/webhook",
                    "text": {
                      "content": "流式回复测试"
                    }
                  }
                }
                """);
        when(dingTalkCallbackCrypto.buildSuccessResponse(any(DingTalkCallbackCrypto.DingTalkSecurityContext.class)))
                .thenReturn(Map.of(
                        "msg_signature", "resp-signature-3",
                        "encrypt", "resp-encrypt-3",
                        "timeStamp", "1700000005",
                        "nonce", "nonce-6"
                ));

        mockMvc.perform(post("/api/channels/webhook/dingtalk/5")
                        .param("signature", "sig-3")
                        .param("timestamp", "1700000004")
                        .param("nonce", "nonce-5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "encrypt": "encrypted-message"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encrypt").value("resp-encrypt-3"))
                .andExpect(jsonPath("$.msg_signature").value("resp-signature-3"));

        verify(baseChannel, never()).onMessage(any());
        verify(channelMessageDispatcher).dispatch(argThat(matchesMessage("流式回复测试", "staff-3", "conversation-3")), any());
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
