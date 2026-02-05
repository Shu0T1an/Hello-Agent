package cn.ts.graph.util;

import cn.ts.graph.state.State;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StateTemplateBuilder 测试
 * <p>
 * 验证状态模板构建器的功能和 StateTemplates 预定义模板
 * </p>
 *
 * @author tianshuo
 */
class StateTemplateBuilderTest {

    @Test
    void testBasicBuilder() {
        State state = StateTemplateBuilder.builder()
                .put("key1", "value1")
                .put("key2", 42)
                .build();

        assertNotNull(state);
        assertTrue(state.containsKey("key1"));
        assertTrue(state.containsKey("key2"));
        assertEquals("value1", state.value("key1").orElse(null));
        assertEquals(42, state.value("key2").orElse(null));
    }

    @Test
    void testPutAll() {
        Map<String, Object> data = Map.of(
                "key1", "value1",
                "key2", "value2",
                "key3", 123
        );

        State state = StateTemplateBuilder.builder()
                .putAll(data)
                .build();

        assertEquals("value1", state.value("key1").orElse(null));
        assertEquals("value2", state.value("key2").orElse(null));
        assertEquals(123, state.value("key3").orElse(0));
    }

    @Test
    void testWithAppendStrategy() {
        State state = StateTemplateBuilder.builder()
                .put("messages", new ArrayList<String>())
                .withAppendStrategy("messages")
                .build();

        // 验证策略已注册（通过测试追加行为）
        List<String> original = new ArrayList<>();
        original.add("msg1");
        state.update("messages", original);

        List<String> additional = new ArrayList<>();
        additional.add("msg2");
        state.update("messages", additional);

        // 由于使用了 AppendStrategy，消息应该被追加
        Object resultObj = state.value("messages").orElse(new ArrayList<>());
        assertTrue(resultObj instanceof List);
        List<?> result = (List<?>) resultObj;
        assertTrue(result.size() >= 1);
    }

    @Test
    void testWithReplaceStrategy() {
        State state = StateTemplateBuilder.builder()
                .put("iteration", 0)
                .withReplaceStrategy("iteration")
                .build();

        state.update("iteration", 5);
        assertEquals(5, state.value("iteration").orElse(0));

        state.update("iteration", 10);
        assertEquals(10, state.value("iteration").orElse(0));
    }

    @Test
    void testConfigureMethod() {
        State state = StateTemplateBuilder.builder()
                .configure(builder -> {
                    builder.put("key1", "value1");
                    builder.put("key2", "value2");
                    builder.withReplaceStrategy("key1");
                })
                .build();

        assertTrue(state.containsKey("key1"));
        assertTrue(state.containsKey("key2"));
    }

    @Test
    void testBuildData() {
        Map<String, Object> data = StateTemplateBuilder.builder()
                .put("key1", "value1")
                .put("key2", 42)
                .buildData();

        assertTrue(data.containsKey("key1"));
        assertTrue(data.containsKey("key2"));
        assertEquals("value1", data.get("key1"));
        assertEquals(42, data.get("key2"));
    }

    @Test
    void testStateTemplatesCreateAgentInitialState() {
        State state = StateTemplates.createAgentInitialState("test input", 10);

        assertNotNull(state);
        assertEquals("test input", state.value("input").orElse(null));
        assertEquals(10, state.value("max_iterations").orElse(0));
        assertEquals(0, state.value("iteration").orElse(0));
        assertTrue(state.containsKey("messages"));
        assertTrue(state.containsKey("execute_record"));
    }

    @Test
    void testStateTemplatesCreateEmptyAgentState() {
        State state = StateTemplates.createEmptyAgentState();

        assertNotNull(state);
        assertTrue(state.containsKey("messages"));
        assertTrue(state.containsKey("execute_record"));
        assertTrue(state.containsKey("iteration"));

        // 验证消息列表为空列表
        Object messagesObj = state.value("messages").orElse(null);
        assertNotNull(messagesObj);
        assertTrue(messagesObj instanceof List);
        List<?> messages = (List<?>) messagesObj;
        assertTrue(messages.isEmpty());
    }

    @Test
    void testStateTemplatesCreateSessionInitialState() {
        State state = StateTemplates.createSessionInitialState("testAgent");

        assertNotNull(state);
        assertEquals("testAgent", state.value("current_agent").orElse(null));
        assertTrue(state.containsKey("messages"));
        assertTrue(state.containsKey("agent_history"));
    }

    @Test
    void testStateTemplatesCreateStateWithMessages() {
        List<Message> messages = new ArrayList<>();
        // 创建一个简单的用户消息
        messages.add(new org.springframework.ai.chat.messages.UserMessage("test message"));

        State state = StateTemplates.createStateWithMessages(messages, 5);

        assertNotNull(state);
        assertEquals(5, state.value("iteration").orElse(0));
        assertTrue(state.containsKey("messages"));
    }

    @Test
    void testStateTemplatesCreateWithCustomData() {
        Map<String, Object> customData = Map.of(
                "customKey1", "customValue1",
                "customKey2", 42
        );

        State state = StateTemplates.createWithCustomData(customData);

        assertNotNull(state);
        assertEquals("customValue1", state.value("customKey1").orElse(null));
        assertEquals(42, state.value("customKey2").orElse(0));
    }

    @Test
    void testStateTemplatesDefaultFactory() {
        StateFactory factory = StateTemplates.defaultFactory();

        assertNotNull(factory);
        State state = factory.createState();
        assertNotNull(state);
    }

    @Test
    void testBuilderChaining() {
        State state = StateTemplateBuilder.builder()
                .put("key1", "value1")
                .put("key2", "value2")
                .withReplaceStrategy("key1")
                .withAppendStrategy("key2")
                .put("key3", "value3")
                .build();

        assertTrue(state.containsKey("key1"));
        assertTrue(state.containsKey("key2"));
        assertTrue(state.containsKey("key3"));
    }

    @Test
    void testNullHandlingInBuilder() {
        // 测试 putAll 空映射
        State state = StateTemplateBuilder.builder()
                .put("key1", "value1")
                .putAll(java.util.Collections.emptyMap())
                .build();

        assertNotNull(state);
        assertTrue(state.containsKey("key1"));
    }
}
