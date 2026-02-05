package cn.ts.web.service.strategy;

import org.springframework.ai.chat.messages.Message;

import java.util.Map;

/**
 * 消息反序列化策略接口
 * <p>
 * 定义从 Map 反序列化为不同类型 Message 的策略接口
 * </p>
 *
 * @author tianshuo
 */
public interface MessageDeserializationStrategy {

    /**
     * 判断此策略是否支持给定的消息类型
     *
     * @param messageType 消息类型字符串
     * @return 如果支持返回 true，否则返回 false
     */
    boolean supports(String messageType);

    /**
     * 从 Map 反序列化为 Message 对象
     *
     * @param map 包含消息数据的 Map
     * @return 反序列化后的 Message 对象
     * @throws IllegalArgumentException 如果 Map 数据不符合要求
     */
    Message deserialize(Map<String, Object> map);

    /**
     * 获取此策略支持的消息类型
     *
     * @return 消息类型字符串
     */
    String getSupportedMessageType();
}
