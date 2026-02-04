package cn.ts.graph.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型化状态序列化测试
 * <p>
 * 验证类型化序列化和反序列化能正确保持泛型类型信息。
 * </p>
 *
 * @author tianshuo
 */
class TypedStateSerializationTest {

    private ObjectMapper objectMapper;
    private TypedStateSerializer serializer;
    private TypedStateDeserializer deserializer;

    @BeforeEach
    void setUp() {
        // 配置 ObjectMapper，注册 MessageJsonModule
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new MessageJsonModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 创建序列化器和反序列化器
        serializer = new TypedStateSerializer(objectMapper);
        deserializer = new TypedStateDeserializer(objectMapper);

        // 注册默认类型
        StateTypeRegistry.getInstance().clear();
        StateTypeRegistry.getInstance().register("messages", new com.fasterxml.jackson.core.type.TypeReference<List<Message>>() {});
    }

    @Test
    void testMessageListSerialization() throws JsonProcessingException {
        // 创建包含不同类型 Message 的列表
        List<Message> messages = List.of(
                new UserMessage("Hello, how are you?"),
                new AssistantMessage("I'm doing well, thank you!"),
                new SystemMessage("You are a helpful assistant."),
                new UserMessage("What's the weather today?"),
                new AssistantMessage("I don't have access to real-time weather data.")
        );

        // 创建状态
        Map<String, Object> state = new HashMap<>();
        state.put("messages", messages);
        state.put("iteration", 2);
        state.put("sessionId", "test-session-123");

        // 序列化
        String json = serializer.serializeWithTypeMetadata(state);

        // 验证 JSON 包含类型元数据
        assertTrue(json.contains(StateTypeRegistry.TYPE_METADATA_KEY));
        assertTrue(json.contains("messages"));

        // 反序列化
        Map<String, Object> restored = deserializer.deserializeWithTypeMetadata(json);

        // 验证基本字段
        assertEquals(3, restored.size()); // messages, iteration, sessionId
        assertEquals(2, restored.get("iteration"));
        assertEquals("test-session-123", restored.get("sessionId"));

        // 验证 Message 列表类型正确
        Object messagesObj = restored.get("messages");
        assertTrue(messagesObj instanceof List);

        @SuppressWarnings("unchecked")
        List<Message> restoredMessages = (List<Message>) messagesObj;
        assertEquals(5, restoredMessages.size());

        // 验证每个 Message 的具体类型
        assertTrue(restoredMessages.get(0) instanceof UserMessage);
        assertTrue(restoredMessages.get(1) instanceof AssistantMessage);
        assertTrue(restoredMessages.get(2) instanceof SystemMessage);
        assertTrue(restoredMessages.get(3) instanceof UserMessage);
        assertTrue(restoredMessages.get(4) instanceof AssistantMessage);

        // 验证 Message 内容
        assertEquals("Hello, how are you?", ((UserMessage) restoredMessages.get(0)).getText());
        assertEquals("I'm doing well, thank you!", ((AssistantMessage) restoredMessages.get(1)).getText());
        assertEquals("You are a helpful assistant.", ((SystemMessage) restoredMessages.get(2)).getText());
        assertEquals("What's the weather today?", ((UserMessage) restoredMessages.get(3)).getText());
        assertEquals("I don't have access to real-time weather data.", ((AssistantMessage) restoredMessages.get(4)).getText());
    }

    @Test
    void testEmptyState() throws JsonProcessingException {
        // 测试空状态
        Map<String, Object> state = new HashMap<>();

        String json = serializer.serializeWithTypeMetadata(state);
        Map<String, Object> restored = deserializer.deserializeWithTypeMetadata(json);

        assertTrue(restored.isEmpty());
    }

    @Test
    void testStateWithMultipleMessageTypes() throws JsonProcessingException {
        // 测试包含多种消息类型的复杂场景
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage("System prompt"));
        messages.add(new UserMessage("User input"));
        messages.add(new AssistantMessage("Assistant response with tool calls",
                Map.of("key", "value"),
                List.of(new AssistantMessage.ToolCall("call-123", "function", "search", "{\"query\":\"test\"}"))));

        Map<String, Object> state = new HashMap<>();
        state.put("messages", messages);
        state.put("counter", 42);
        state.put("active", true);

        String json = serializer.serializeWithTypeMetadata(state);
        Map<String, Object> restored = deserializer.deserializeWithTypeMetadata(json);

        @SuppressWarnings("unchecked")
        List<Message> restoredMessages = (List<Message>) restored.get("messages");

        assertEquals(3, restoredMessages.size());
        assertTrue(restoredMessages.get(0) instanceof SystemMessage);
        assertTrue(restoredMessages.get(1) instanceof UserMessage);
        assertTrue(restoredMessages.get(2) instanceof AssistantMessage);

        // 验证 AssistantMessage 的 toolCalls
        AssistantMessage assistantMsg = (AssistantMessage) restoredMessages.get(2);
        assertTrue(assistantMsg.hasToolCalls());
        assertEquals(1, assistantMsg.getToolCalls().size());
        assertEquals("call-123", assistantMsg.getToolCalls().get(0).id());
        assertEquals("search", assistantMsg.getToolCalls().get(0).name());
    }

    @Test
    void testStateWithoutRegisteredType() throws JsonProcessingException {
        // 测试未注册类型的行为（应该使用原始值）
        Map<String, Object> state = new HashMap<>();
        state.put("unregisteredKey", "some value");
        state.put("number", 123);

        String json = serializer.serializeWithTypeMetadata(state);
        Map<String, Object> restored = deserializer.deserializeWithTypeMetadata(json);

        // 未注册的类型应该保持原始值
        assertEquals("some value", restored.get("unregisteredKey"));
        assertEquals(123, restored.get("number"));
    }

    @Test
    void testTypeMetadataStructure() throws JsonProcessingException {
        // 测试类型元数据的结构
        List<Message> messages = List.of(new UserMessage("Test"));

        Map<String, Object> state = new HashMap<>();
        state.put("messages", messages);

        String json = serializer.serializeWithTypeMetadata(state);

        // 验证 JSON 包含类型元数据键
        assertTrue(json.contains("\"__type_metadata__\""));
        assertTrue(json.contains("\"messages\""));
    }

    @Test
    void testNullState() throws JsonProcessingException {
        // 测试 null 状态
        String json = serializer.serializeWithTypeMetadata(null);
        assertEquals("{}", json);

        Map<String, Object> restored = deserializer.deserializeWithTypeMetadata(json);
        assertTrue(restored.isEmpty());
    }

    @Test
    void testEmptyJson() throws JsonProcessingException {
        // 测试空 JSON
        Map<String, Object> restored = deserializer.deserializeWithTypeMetadata("{}");
        assertTrue(restored.isEmpty());

        restored = deserializer.deserializeWithTypeMetadata("");
        assertTrue(restored.isEmpty());

        restored = deserializer.deserializeWithTypeMetadata(null);
        assertTrue(restored.isEmpty());
    }
}
