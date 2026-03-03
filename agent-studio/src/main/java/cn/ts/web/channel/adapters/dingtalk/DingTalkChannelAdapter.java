package cn.ts.web.channel.adapters.dingtalk;

import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;
import cn.ts.web.channel.dto.ChannelInboundMessage;
import cn.ts.web.channel.entity.ChannelConfigEntity;
import cn.ts.web.channel.runtime.BaseChannel;
import cn.ts.web.channel.runtime.ChannelMessageDispatcher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class DingTalkChannelAdapter implements BaseChannel {

    private static final Logger logger = LoggerFactory.getLogger(DingTalkChannelAdapter.class);

    private final ChannelConfigEntity config;
    private final ChannelMessageDispatcher dispatcher;
    private final DingTalkStreamClientFactory streamClientFactory;
    private final DingTalkBotReplyService botReplyService;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile OpenDingTalkClient streamClient;

    public DingTalkChannelAdapter(ChannelConfigEntity config,
                                  ChannelMessageDispatcher dispatcher,
                                  DingTalkStreamClientFactory streamClientFactory,
                                  DingTalkBotReplyService botReplyService,
                                  ObjectMapper objectMapper) {
        this.config = config;
        this.dispatcher = dispatcher;
        this.streamClientFactory = streamClientFactory;
        this.botReplyService = botReplyService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Long configId() {
        return config.getId();
    }

    @Override
    public String channelType() {
        return config.getChannelType();
    }

    @Override
    public String channelName() {
        return config.getChannelName();
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            Map<String, Object> configMap = parseConfig(config.getConfigJson());
            String clientId = firstNonBlank(
                    readConfigText(configMap, "clientId"),
                    readConfigText(configMap, "appKey")
            );
            String clientSecret = firstNonBlank(
                    readConfigText(configMap, "clientSecret"),
                    readConfigText(configMap, "appSecret")
            );
            if (isBlank(clientId) || isBlank(clientSecret)) {
                throw new IllegalArgumentException("DingTalk stream config requires clientId/clientSecret");
            }

            OpenDingTalkClient client = streamClientFactory.create(clientId, clientSecret, this::handleBotMessage);
            client.start();
            streamClient = client;
            logger.info("DingTalk stream started: channel={}, id={}", channelName(), configId());
        } catch (Exception e) {
            running.set(false);
            throw new IllegalStateException("Failed to start DingTalk stream channel: " + channelName(), e);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        OpenDingTalkClient client = streamClient;
        streamClient = null;
        if (client == null) {
            return;
        }
        try {
            client.stop();
            logger.info("DingTalk stream stopped: channel={}, id={}", channelName(), configId());
        } catch (Exception e) {
            logger.warn("Failed to stop DingTalk stream client: channel={}, id={}, message={}",
                    channelName(), configId(), e.getMessage());
        }
    }

    @Override
    public boolean healthy() {
        return running.get();
    }

    @Override
    public void onMessage(ChannelInboundMessage message) {
        dispatcher.dispatch(message);
    }

    Void handleBotMessage(ChatbotMessage chatbotMessage) {
        try {
            if (chatbotMessage == null) {
                return null;
            }
            ChannelInboundMessage message = toInboundMessage(chatbotMessage);
            String sessionWebhook = cleanText(chatbotMessage.getSessionWebhook());
            if (message.getText() == null || message.getText().isBlank()) {
                return null;
            }
            if (isBlank(sessionWebhook)) {
                onMessage(message);
            } else {
                dispatcher.dispatch(message, replyText -> replyByWebhook(sessionWebhook, replyText));
            }
        } catch (Exception e) {
            logger.error("Failed to process DingTalk stream event for channel {}({}): {}",
                    channelName(), configId(), e.getMessage(), e);
        }
        return null;
    }

    private void replyByWebhook(String sessionWebhook, String replyText) {
        try {
            botReplyService.replyText(sessionWebhook, replyText);
        } catch (IOException e) {
            logger.warn("Failed to send DingTalk reply by webhook: channel={}, id={}, message={}",
                    channelName(), configId(), e.getMessage());
        }
    }

    private ChannelInboundMessage toInboundMessage(ChatbotMessage payload) {
        ChannelInboundMessage message = new ChannelInboundMessage();
        message.setChannelType(channelType());
        message.setAgentName(null);
        message.setChannelUserId(firstNonBlank(
                cleanText(payload.getSenderStaffId()),
                cleanText(payload.getSenderId()),
                cleanText(payload.getSenderNick())
        ));
        message.setChannelSessionId(firstNonBlank(
                cleanText(payload.getConversationId()),
                cleanText(payload.getChatbotUserId())
        ));
        message.setText(resolveText(payload));
        return message;
    }

    private String resolveText(ChatbotMessage payload) {
        String text = extractContent(payload.getText());
        if (!isBlank(text)) {
            return text;
        }
        return extractContent(payload.getContent());
    }

    private String extractContent(MessageContent content) {
        if (content == null) {
            return null;
        }
        String plain = cleanText(content.getContent());
        if (!isBlank(plain)) {
            return plain;
        }
        return cleanText(content.getText());
    }

    private Map<String, Object> parseConfig(String configJson) {
        if (isBlank(configJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid DingTalk channel config json", e);
        }
    }

    private String readConfigText(Map<String, Object> configMap, String key) {
        Object value = configMap.get(key);
        if (value == null) {
            return null;
        }
        return cleanText(String.valueOf(value));
    }

    private String firstNonBlank(String first, String second) {
        String firstValue = cleanText(first);
        if (firstValue != null) {
            return firstValue;
        }
        return cleanText(second);
    }

    private String firstNonBlank(String first, String second, String third) {
        String value = firstNonBlank(first, second);
        if (value != null) {
            return value;
        }
        return cleanText(third);
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
