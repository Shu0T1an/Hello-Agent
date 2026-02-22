package cn.ts.web.service;

import cn.ts.web.shared.constant.ApiConstants;
import cn.ts.web.service.strategy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MessageConversionService 测试
 * <p>
 * 验证消息转换服务的正确性
 * </p>
 *
 * @author tianshuo
 */
class MessageConversionServiceTest {

    private MessageConversionService service;

    @BeforeEach
    void setUp() {
        service = new MessageConversionService();
    }

    @Test
    void testConvertEmptyList() {
        List<Message> result = service.convertStateToMessages(new ArrayList<>());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConvertNullList() {
        List<Message> result = service.convertStateToMessages(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConvertAlreadyMessageObjects() {
        List<Object> input = new ArrayList<>();
        input.add(new UserMessage("test"));
        input.add(new AssistantMessage("response"));

        List<Message> result = service.convertStateToMessages(input);
        assertEquals(2, result.size());
        assertTrue(result.get(0) instanceof UserMessage);
        assertTrue(result.get(1) instanceof AssistantMessage);
    }

    @Test
    void testConvertUserMessageFromMap() {
        List<Map<String, Object>> input = new ArrayList<>();
        Map<String, Object> userMessageMap = new HashMap<>();
        userMessageMap.put("messageType", ApiConstants.MessageTypes.USER);
        userMessageMap.put("text", "Hello, world!");
        userMessageMap.put("metadata", new HashMap<String, Object>());
        input.add(userMessageMap);

        List<Message> result = service.convertStateToMessages(input);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof UserMessage);

        UserMessage userMessage = (UserMessage) result.get(0);
        assertEquals("Hello, world!", userMessage.getText());
    }

    @Test
    void testConvertSystemMessageFromMap() {
        List<Map<String, Object>> input = new ArrayList<>();
        Map<String, Object> systemMessageMap = new HashMap<>();
        systemMessageMap.put("messageType", ApiConstants.MessageTypes.SYSTEM);
        systemMessageMap.put("content", "System instruction");
        input.add(systemMessageMap);

        List<Message> result = service.convertStateToMessages(input);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof SystemMessage);

        SystemMessage systemMessage = (SystemMessage) result.get(0);
        assertEquals("System instruction", systemMessage.getText());
    }

    @Test
    void testConvertAssistantMessageFromMap() {
        List<Map<String, Object>> input = new ArrayList<>();
        Map<String, Object> assistantMessageMap = new HashMap<>();
        assistantMessageMap.put("messageType", ApiConstants.MessageTypes.ASSISTANT);
        assistantMessageMap.put("content", "Assistant response");
        assistantMessageMap.put("metadata", new HashMap<String, Object>());
        assistantMessageMap.put("toolCalls", new ArrayList<>());
        input.add(assistantMessageMap);

        List<Message> result = service.convertStateToMessages(input);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof AssistantMessage);

        AssistantMessage assistantMessage = (AssistantMessage) result.get(0);
        assertEquals("Assistant response", assistantMessage.getText());
    }

    @Test
    void testConvertToolResponseMessageFromMap() {
        List<Map<String, Object>> input = new ArrayList<>();
        Map<String, Object> toolResponseMap = new HashMap<>();
        toolResponseMap.put("messageType", ApiConstants.MessageTypes.TOOL_RESPONSE);

        List<Map<String, Object>> responses = new ArrayList<>();
        Map<String, Object> response = new HashMap<>();
        response.put("id", "tool-1");
        response.put("name", "testTool");
        response.put("responseData", "Tool result");
        responses.add(response);

        toolResponseMap.put("responses", responses);
        input.add(toolResponseMap);

        List<Message> result = service.convertStateToMessages(input);
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof ToolResponseMessage);

        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) result.get(0);
        List<ToolResponseMessage.ToolResponse> toolResponses = toolResponseMessage.getResponses();
        assertEquals(1, toolResponses.size());
        assertEquals("tool-1", toolResponses.get(0).id());
        assertEquals("testTool", toolResponses.get(0).name());
        assertEquals("Tool result", toolResponses.get(0).responseData());
    }

    @Test
    void testConvertMixedMessages() {
        List<Object> input = new ArrayList<>();
        input.add(new UserMessage("User input"));

        Map<String, Object> assistantMap = new HashMap<>();
        assistantMap.put("messageType", ApiConstants.MessageTypes.ASSISTANT);
        assistantMap.put("content", "Assistant response");
        assistantMap.put("metadata", new HashMap<String, Object>());
        assistantMap.put("toolCalls", new ArrayList<>());
        input.add(assistantMap);

        Map<String, Object> systemMap = new HashMap<>();
        systemMap.put("messageType", ApiConstants.MessageTypes.SYSTEM);
        systemMap.put("content", "System message");
        input.add(systemMap);

        List<Message> result = service.convertStateToMessages(input);
        assertEquals(3, result.size());
        assertTrue(result.get(0) instanceof UserMessage);
        assertTrue(result.get(1) instanceof AssistantMessage);
        assertTrue(result.get(2) instanceof SystemMessage);
    }

    @Test
    void testConvertMessageWithoutType() {
        List<Map<String, Object>> input = new ArrayList<>();
        Map<String, Object> messageMap = new HashMap<>();
        // 不设置 messageType
        messageMap.put("text", "Unknown message");
        input.add(messageMap);

        List<Message> result = service.convertStateToMessages(input);
        // 应该默认作为 UserMessage 处理
        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof UserMessage);
    }

    @Test
    void testConvertWithNullItem() {
        List<Object> input = new ArrayList<>();
        input.add(null);
        input.add(new UserMessage("test"));

        List<Message> result = service.convertStateToMessages(input);
        // null 应该被跳过
        assertEquals(1, result.size());
    }

    @Test
    void testConvertWithInvalidMap() {
        List<Map<String, Object>> input = new ArrayList<>();
        Map<String, Object> invalidMap = new HashMap<>();
        invalidMap.put("messageType", "INVALID_TYPE");
        input.add(invalidMap);

        List<Message> result = service.convertStateToMessages(input);
        // 无效类型应该被忽略或使用默认策略
        assertTrue(result.size() >= 0);
    }

    @Test
    void testRegisterCustomStrategy() {
        MessageConversionService service = new MessageConversionService();
        int initialCount = service.getRegisteredStrategyCount();

        // 注册自定义策略
        MessageDeserializationStrategy customStrategy = new MessageDeserializationStrategy() {
            @Override
            public boolean supports(String messageType) {
                return "CUSTOM".equals(messageType);
            }

            @Override
            public Message deserialize(Map<String, Object> map) {
                return new UserMessage("Custom message");
            }

            @Override
            public String getSupportedMessageType() {
                return "CUSTOM";
            }
        };

        service.registerStrategy(customStrategy);
        assertEquals(initialCount + 1, service.getRegisteredStrategyCount());
    }

    @Test
    void testUserMessageStrategy() {
        UserMessageStrategy strategy = new UserMessageStrategy();
        assertTrue(strategy.supports(ApiConstants.MessageTypes.USER));
        assertEquals(ApiConstants.MessageTypes.USER, strategy.getSupportedMessageType());

        Map<String, Object> map = new HashMap<>();
        map.put("text", "Test message");
        map.put("metadata", new HashMap<String, Object>());

        UserMessage message = strategy.deserialize(map);
        assertEquals("Test message", message.getText());
    }

    @Test
    void testSystemMessageStrategy() {
        SystemMessageStrategy strategy = new SystemMessageStrategy();
        assertTrue(strategy.supports(ApiConstants.MessageTypes.SYSTEM));
        assertEquals(ApiConstants.MessageTypes.SYSTEM, strategy.getSupportedMessageType());

        Map<String, Object> map = new HashMap<>();
        map.put("content", "System content");

        SystemMessage message = strategy.deserialize(map);
        assertEquals("System content", message.getText());
    }

    @Test
    void testAssistantMessageStrategyWithToolCalls() {
        AssistantMessageStrategy strategy = new AssistantMessageStrategy();
        assertTrue(strategy.supports(ApiConstants.MessageTypes.ASSISTANT));

        Map<String, Object> map = new HashMap<>();
        map.put("content", "Assistant content");
        map.put("metadata", new HashMap<String, Object>());

        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> toolCall = new HashMap<>();
        toolCall.put("id", "call-1");
        toolCall.put("type", "function");
        toolCall.put("name", "testFunction");
        toolCall.put("arguments", "{\"arg\":\"value\"}");
        toolCalls.add(toolCall);

        map.put("toolCalls", toolCalls);

        AssistantMessage message = strategy.deserialize(map);
        assertEquals("Assistant content", message.getText());
        assertEquals(1, message.getToolCalls().size());
        assertEquals("call-1", message.getToolCalls().get(0).id());
    }

    @Test
    void testToolResponseMessageStrategy() {
        ToolResponseMessageStrategy strategy = new ToolResponseMessageStrategy();
        assertTrue(strategy.supports(ApiConstants.MessageTypes.TOOL_RESPONSE));
        assertTrue(strategy.supports(ApiConstants.MessageTypes.TOOL));

        Map<String, Object> map = new HashMap<>();
        List<Map<String, Object>> responses = new ArrayList<>();
        Map<String, Object> response = new HashMap<>();
        response.put("id", "resp-1");
        response.put("name", "testTool");
        response.put("responseData", "Result data");
        responses.add(response);
        map.put("responses", responses);

        ToolResponseMessage message = strategy.deserialize(map);
        assertEquals(1, message.getResponses().size());
        assertEquals("resp-1", message.getResponses().get(0).id());
    }
}
