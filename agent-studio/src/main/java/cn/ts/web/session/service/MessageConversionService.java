package cn.ts.web.session.service;

import cn.ts.web.shared.constant.ApiConstants;
import cn.ts.web.session.service.strategy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 消息转换服务
 * <p>
 * 统一处理消息类型的反序列化和转换逻辑，使用策略模式支持不同消息类型
 * </p>
 *
 * @author tianshuo
 */
@Service
public class MessageConversionService {

    private static final Logger log = LoggerFactory.getLogger(MessageConversionService.class);

    private final List<MessageDeserializationStrategy> strategies;

    /**
     * 构造函数，注册所有可用的反序列化策略
     */
    public MessageConversionService() {
        this.strategies = new ArrayList<>();
        registerDefaultStrategies();
    }

    /**
     * 注册默认的消息反序列化策略
     */
    private void registerDefaultStrategies() {
        strategies.add(new UserMessageStrategy());
        strategies.add(new AssistantMessageStrategy());
        strategies.add(new SystemMessageStrategy());
        strategies.add(new ToolResponseMessageStrategy());
        log.debug("Registered {} message deserialization strategies", strategies.size());
    }

    /**
     * 将从检查点恢复的 messages 列表中的 Map 转换为 Message 对象
     * <p>
     * 检查点恢复时，Message 对象被反序列化为 LinkedHashMap，
     * 需要根据 messageType 字段重新转换为正确的 Message 类型
     * </p>
     *
     * @param messagesData 可能包含 Map 或 Message 的列表
     * @return 包含正确 Message 类型对象的列表
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Message> convertStateToMessages(List<?> messagesData) {
        if (messagesData == null || messagesData.isEmpty()) {
            return new ArrayList<>();
        }

        List<Message> result = new ArrayList<>();

        for (Object item : messagesData) {
            if (item == null) {
                continue;
            }

            // 如果已经是 Message 对象，直接添加
            if (item instanceof Message message) {
                result.add(message);
                continue;
            }

            // 如果是 Map，需要转换
            if (item instanceof Map) {
                Message message = convertMapToMessage((Map<String, Object>) item);
                if (message != null) {
                    result.add(message);
                }
            }
        }

        log.debug("Converted {} items to messages", result.size());
        return result;
    }

    /**
     * 将 Map 转换为 Message 对象
     *
     * @param map 包含消息数据的 Map
     * @return Message 对象，如果转换失败返回 null
     */
    private Message convertMapToMessage(Map<String, Object> map) {
        String messageType = extractMessageType(map);

        if (messageType == null) {
            log.debug("No messageType found, defaulting to USER");
            messageType = ApiConstants.MessageTypes.USER;
        }

        MessageDeserializationStrategy strategy = findStrategy(messageType);
        if (strategy == null) {
            log.warn("No strategy found for message type: {}, using default", messageType);
            strategy = findStrategy(ApiConstants.MessageTypes.USER);
        }

        if (strategy != null) {
            try {
                return strategy.deserialize(map);
            } catch (Exception e) {
                log.error("Failed to deserialize message with type: {}, error: {}",
                        messageType, e.getMessage(), e);
                return null;
            }
        }

        log.error("Unable to find suitable strategy for message type: {}", messageType);
        return null;
    }

    /**
     * 从 Map 中提取消息类型
     *
     * @param map 消息数据 Map
     * @return 消息类型字符串，如果不存在返回 null
     */
    private String extractMessageType(Map<String, Object> map) {
        Object typeObj = map.get("messageType");
        return typeObj != null ? typeObj.toString() : null;
    }

    /**
     * 查找支持指定消息类型的策略
     *
     * @param messageType 消息类型
     * @return 支持的策略，如果不存在返回 null
     */
    private MessageDeserializationStrategy findStrategy(String messageType) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(messageType))
                .findFirst()
                .orElse(null);
    }

    /**
     * 注册自定义的消息反序列化策略
     * <p>
     * 允许在运行时添加新的消息类型支持
     * </p>
     *
     * @param strategy 要注册的策略
     */
    public void registerStrategy(MessageDeserializationStrategy strategy) {
        if (strategy != null) {
            strategies.add(strategy);
            log.info("Registered custom strategy for message type: {}",
                    strategy.getSupportedMessageType());
        }
    }

    /**
     * 获取所有已注册的策略数量
     *
     * @return 策略数量
     */
    public int getRegisteredStrategyCount() {
        return strategies.size();
    }
}
