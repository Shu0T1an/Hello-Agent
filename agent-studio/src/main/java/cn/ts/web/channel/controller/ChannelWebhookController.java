package cn.ts.web.channel.controller;

import cn.ts.web.channel.adapters.dingtalk.DingTalkCallbackCrypto;
import cn.ts.web.channel.adapters.dingtalk.DingTalkBotReplyService;
import cn.ts.web.channel.dto.ChannelConfigDTO;
import cn.ts.web.channel.dto.ChannelInboundMessage;
import cn.ts.web.channel.runtime.BaseChannel;
import cn.ts.web.channel.runtime.ChannelMessageDispatcher;
import cn.ts.web.channel.runtime.ChannelRuntimeManager;
import cn.ts.web.channel.service.ChannelConfigService;
import cn.ts.web.shared.response.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/channels/webhook")
public class ChannelWebhookController {

    private final ChannelRuntimeManager channelRuntimeManager;
    private final ChannelConfigService channelConfigService;
    private final ChannelMessageDispatcher channelMessageDispatcher;
    private final DingTalkBotReplyService dingTalkBotReplyService;
    private final DingTalkCallbackCrypto dingTalkCallbackCrypto;
    private final ObjectMapper objectMapper;

    public ChannelWebhookController(ChannelRuntimeManager channelRuntimeManager,
                                    ChannelConfigService channelConfigService,
                                    ChannelMessageDispatcher channelMessageDispatcher,
                                    DingTalkBotReplyService dingTalkBotReplyService,
                                    DingTalkCallbackCrypto dingTalkCallbackCrypto,
                                    ObjectMapper objectMapper) {
        this.channelRuntimeManager = channelRuntimeManager;
        this.channelConfigService = channelConfigService;
        this.channelMessageDispatcher = channelMessageDispatcher;
        this.dingTalkBotReplyService = dingTalkBotReplyService;
        this.dingTalkCallbackCrypto = dingTalkCallbackCrypto;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{channelConfigId}")
    public Result<Map<String, Object>> webhook(@PathVariable Long channelConfigId,
                                               @RequestBody JsonNode payload) {
        BaseChannel channel = channelRuntimeManager.getRunningChannel(channelConfigId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel is not running: " + channelConfigId);
        }
        ChannelInboundMessage message = toInboundMessage(payload, channel.channelType());
        if (message.getText() == null || message.getText().isBlank()) {
            throw new IllegalArgumentException("Channel message text is required");
        }
        channel.onMessage(message);
        return Result.success(Map.of("accepted", true));
    }

    @PostMapping("/dingtalk/{channelConfigId}")
    public Map<String, String> dingtalkWebhook(@PathVariable Long channelConfigId,
                                               @RequestParam(name = "signature", required = false) String signature,
                                               @RequestParam(name = "msg_signature", required = false) String msgSignature,
                                               @RequestParam(name = "timestamp", required = false) String timestamp,
                                               @RequestParam(name = "timeStamp", required = false) String timeStamp,
                                               @RequestParam(name = "nonce") String nonce,
                                               @RequestBody JsonNode payload) {
        BaseChannel channel = channelRuntimeManager.getRunningChannel(channelConfigId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel is not running: " + channelConfigId);
        }
        if (!"dingtalk".equalsIgnoreCase(channel.channelType())) {
            throw new IllegalArgumentException("Unsupported webhook channel type: " + channel.channelType());
        }

        ChannelConfigDTO config = channelConfigService.getById(channelConfigId);
        DingTalkCallbackCrypto.DingTalkSecurityContext context = buildDingTalkSecurityContext(config.getConfig());

        String resolvedSignature = firstNonBlank(signature, msgSignature);
        String resolvedTimestamp = firstNonBlank(timestamp, timeStamp);
        String encryptedPayload = readText(payload, "encrypt");
        String plainText = dingTalkCallbackCrypto.decryptAndVerify(
                resolvedSignature,
                resolvedTimestamp,
                nonce,
                encryptedPayload,
                context
        );

        JsonNode decryptedPayload = parseJson(plainText);
        ChannelInboundMessage message = toDingTalkInboundMessage(decryptedPayload, channel.channelType());
        String sessionWebhook = resolveDingTalkSessionWebhook(decryptedPayload);
        if (message.getText() != null && !message.getText().isBlank()) {
            if (sessionWebhook == null) {
                channel.onMessage(message);
            } else {
                channelMessageDispatcher.dispatch(message, replyText -> replyByWebhook(sessionWebhook, replyText));
            }
        }
        return dingTalkCallbackCrypto.buildSuccessResponse(context);
    }

    private void replyByWebhook(String sessionWebhook, String replyText) {
        try {
            dingTalkBotReplyService.replyText(sessionWebhook, replyText);
        } catch (IOException ignored) {
            // keep webhook ack path robust even if outbound reply fails
        }
    }

    private String resolveDingTalkSessionWebhook(JsonNode payload) {
        JsonNode dataNode = payload == null ? null : payload.get("data");
        String webhook = null;
        if (dataNode != null && dataNode.isObject()) {
            webhook = readText(dataNode, "sessionWebhook");
        }
        return webhook != null ? webhook : readText(payload, "sessionWebhook");
    }

    private ChannelInboundMessage toInboundMessage(JsonNode payload, String channelType) {
        ChannelInboundMessage message = new ChannelInboundMessage();
        message.setChannelType(channelType);
        message.setAgentName(readText(payload, "agentName"));
        message.setChannelUserId(firstNonBlank(
                readText(payload, "channelUserId"),
                readText(payload, "senderStaffId"),
                readText(payload, "senderId")
        ));
        message.setChannelSessionId(firstNonBlank(
                readText(payload, "channelSessionId"),
                readText(payload, "conversationId")
        ));
        message.setText(resolveText(payload));
        return message;
    }

    private ChannelInboundMessage toDingTalkInboundMessage(JsonNode payload, String channelType) {
        JsonNode dataNode = payload.get("data");
        ChannelInboundMessage message = (dataNode != null && dataNode.isObject())
                ? toInboundMessage(dataNode, channelType)
                : toInboundMessage(payload, channelType);
        if (message.getText() == null && dataNode != null && dataNode.isObject()) {
            ChannelInboundMessage fallback = toInboundMessage(payload, channelType);
            fillIfBlank(message, fallback);
        }
        return message;
    }

    private void fillIfBlank(ChannelInboundMessage target, ChannelInboundMessage fallback) {
        if (target.getText() == null) {
            target.setText(fallback.getText());
        }
        if (target.getChannelUserId() == null) {
            target.setChannelUserId(fallback.getChannelUserId());
        }
        if (target.getChannelSessionId() == null) {
            target.setChannelSessionId(fallback.getChannelSessionId());
        }
        if (target.getAgentName() == null) {
            target.setAgentName(fallback.getAgentName());
        }
    }

    private DingTalkCallbackCrypto.DingTalkSecurityContext buildDingTalkSecurityContext(Map<String, Object> config) {
        String token = firstNonBlank(
                readConfigText(config, "callbackToken"),
                readConfigText(config, "token")
        );
        String aesKey = firstNonBlank(
                readConfigText(config, "callbackAesKey"),
                readConfigText(config, "aesKey"),
                readConfigText(config, "encodingAesKey")
        );
        String ownerKey = firstNonBlank(
                readConfigText(config, "ownerKey"),
                readConfigText(config, "clientId"),
                readConfigText(config, "appKey")
        );
        return new DingTalkCallbackCrypto.DingTalkSecurityContext(token, aesKey, ownerKey);
    }

    private JsonNode parseJson(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid DingTalk callback body", e);
        }
    }

    private String readConfigText(Map<String, Object> config, String key) {
        if (config == null || key == null) {
            return null;
        }
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        return cleanText(String.valueOf(value));
    }

    private String resolveText(JsonNode payload) {
        JsonNode textNode = payload.get("text");
        if (textNode != null) {
            if (textNode.isTextual()) {
                return cleanText(textNode.asText());
            }
            if (textNode.isObject()) {
                String nested = readText(textNode, "content");
                if (nested != null) {
                    return nested;
                }
            }
        }
        JsonNode contentNode = payload.get("content");
        if (contentNode != null && contentNode.isObject()) {
            String contentText = readText(contentNode, "text");
            if (contentText != null) {
                return contentText;
            }
        }
        return null;
    }

    private String readText(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            return null;
        }
        return cleanText(value.asText());
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String first, String second, String third) {
        String candidate = firstNonBlank(first, second);
        return candidate != null ? candidate : cleanText(third);
    }

    private String firstNonBlank(String first, String second) {
        String cleanFirst = cleanText(first);
        if (cleanFirst != null) {
            return cleanFirst;
        }
        return cleanText(second);
    }
}
