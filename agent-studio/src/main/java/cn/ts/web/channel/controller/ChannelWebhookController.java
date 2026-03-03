package cn.ts.web.channel.controller;

import cn.ts.web.channel.dto.ChannelInboundMessage;
import cn.ts.web.channel.runtime.BaseChannel;
import cn.ts.web.channel.runtime.ChannelRuntimeManager;
import cn.ts.web.shared.response.Result;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/channels/webhook")
public class ChannelWebhookController {

    private final ChannelRuntimeManager channelRuntimeManager;

    public ChannelWebhookController(ChannelRuntimeManager channelRuntimeManager) {
        this.channelRuntimeManager = channelRuntimeManager;
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
