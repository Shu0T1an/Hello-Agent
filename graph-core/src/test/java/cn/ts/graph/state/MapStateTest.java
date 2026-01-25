package cn.ts.graph.state;

import cn.ts.graph.state.strategy.AppendStrategy;
import cn.ts.graph.state.strategy.KeyStrategy;
import cn.ts.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MapState 单元测试
 * <p>
 * 测试基于 Map 的状态实现，包括值的更新、合并和策略应用
 * </p>
 *
 * @author tianshuo
 */
class MapStateTest {

    @Test
    void testConstructorCreatesEmptyState() {
        MapState state = new MapState();

        assertEquals(0, state.size());
        assertFalse(state.value("any_key").isPresent());
    }

    @Test
    void testConstructorWithInitialData() {
        Map<String, Object> initialData = new HashMap<>();
        initialData.put("key1", "value1");
        initialData.put("key2", 123);

        MapState state = new MapState(initialData);

        assertEquals(2, state.size());
        assertEquals("value1", state.value("key1").orElse(null));
        assertEquals(123, state.value("key2").orElse(null));
    }

    @Test
    void testConstructorWithNullInitialData() {
        MapState state = new MapState(null);

        assertEquals(0, state.size());
    }

    @Test
    void testUpdate() {
        MapState state = new MapState();

        State result = state.update("key1", "value1");

        // 验证链式调用返回自身
        assertSame(state, result);

        assertEquals("value1", state.value("key1").orElse(null));
        assertEquals(1, state.size());
    }

    @Test
    void testUpdateReplacesValueByDefault() {
        MapState state = new MapState();
        state.update("key1", "value1");
        state.update("key1", "value2");

        // 默认使用替换策略
        assertEquals("value2", state.value("key1").orElse(null));
        assertEquals(1, state.size());
    }

    @Test
    void testUpdateWithNullRemovesKey() {
        MapState state = new MapState();
        state.update("key1", "value1");
        assertTrue(state.containsKey("key1"));

        state.update("key1", null);

        assertFalse(state.containsKey("key1"));
        assertEquals(0, state.size());
    }

    @Test
    void testUpdateWithNullKeyThrowsException() {
        MapState state = new MapState();
        state.update("key1", "value1");

        // 空键和非空值会抛出 NullPointerException（因为 ConcurrentHashMap 不允许 null 键）
        assertThrows(NullPointerException.class, () -> state.update(null, "value2"));

        // 验证状态未被修改
        assertEquals(1, state.size());
        assertEquals("value1", state.value("key1").orElse(null));
    }

    @Test
    void testMerge() {
        MapState state = new MapState();

        Map<String, Object> updates = new HashMap<>();
        updates.put("key1", "value1");
        updates.put("key2", 123);

        State result = state.merge(updates);

        // 验证链式调用返回自身
        assertSame(state, result);

        assertEquals("value1", state.value("key1").orElse(null));
        assertEquals(123, state.value("key2").orElse(null));
        assertEquals(2, state.size());
    }

    @Test
    void testMergeWithNullDoesNothing() {
        MapState state = new MapState();
        state.update("key1", "value1");

        state.merge(null);

        assertEquals(1, state.size());
        assertEquals("value1", state.value("key1").orElse(null));
    }

    @Test
    void testMergeReplacesByDefault() {
        MapState state = new MapState();
        state.update("key1", "value1");

        state.merge(Map.of("key1", "value2"));

        assertEquals("value2", state.value("key1").orElse(null));
    }

    @Test
    void testValue() {
        MapState state = new MapState();
        state.update("key1", "value1");

        Optional<String> value = state.value("key1");

        assertTrue(value.isPresent());
        assertEquals("value1", value.get());
    }

    @Test
    void testValueReturnsOptionalEmptyForNonExistentKey() {
        MapState state = new MapState();

        Optional<Object> value = state.value("non_existent");

        assertFalse(value.isPresent());
    }

    @Test
    void testValueWithNullKeyReturnsOptionalEmpty() {
        MapState state = new MapState();
        state.update("key1", "value1");

        Optional<Object> value = state.value(null);

        assertFalse(value.isPresent());
    }

    @Test
    void testValueWithType() {
        MapState state = new MapState();
        state.update("count", 42);
        state.update("name", "test");

        Integer count = state.<Integer>value("count").orElse(null);
        String name = state.<String>value("name").orElse(null);

        assertEquals(Integer.valueOf(42), count);
        assertEquals("test", name);
    }

    @Test
    void testContainsKey() {
        MapState state = new MapState();
        state.update("key1", "value1");

        assertTrue(state.containsKey("key1"));
        assertFalse(state.containsKey("key2"));
    }

    @Test
    void testContainsKeyWithNullReturnsFalse() {
        MapState state = new MapState();
        state.update("key1", "value1");

        assertFalse(state.containsKey(null));
    }

    @Test
    void testKeys() {
        MapState state = new MapState();
        state.update("key1", "value1");
        state.update("key2", "value2");
        state.update("key3", "value3");

        Iterable<String> keys = state.keys();

        List<String> keyList = new ArrayList<>();
        keys.forEach(keyList::add);

        assertEquals(3, keyList.size());
        assertTrue(keyList.contains("key1"));
        assertTrue(keyList.contains("key2"));
        assertTrue(keyList.contains("key3"));
    }

    @Test
    void testDataReturnsUnmodifiableMap() {
        MapState state = new MapState();
        state.update("key1", "value1");

        Map<String, Object> data = state.data();

        assertEquals(1, data.size());
        assertEquals("value1", data.get("key1"));

        // 尝试修改应该抛出异常
        assertThrows(UnsupportedOperationException.class, () -> data.put("key2", "value2"));
    }

    @Test
    void testClear() {
        MapState state = new MapState();
        state.update("key1", "value1");
        state.update("key2", "value2");
        assertEquals(2, state.size());

        state.clear();

        assertEquals(0, state.size());
        assertFalse(state.containsKey("key1"));
        assertFalse(state.containsKey("key2"));
    }

    @Test
    void testSize() {
        MapState state = new MapState();
        assertEquals(0, state.size());

        state.update("key1", "value1");
        assertEquals(1, state.size());

        state.update("key2", "value2");
        assertEquals(2, state.size());

        state.clear();
        assertEquals(0, state.size());
    }

    @Test
    void testRegisterKeyStrategy() {
        MapState state = new MapState();
        KeyStrategy<List<String>> strategy = AppendStrategy.getInstance();

        State result = state.registerKeyStrategy("messages", strategy);

        // 验证链式调用返回自身
        assertSame(state, result);
    }

    @Test
    void testRegisterKeyStrategyWithNullDoesNothing() {
        MapState state = new MapState();

        // 不应该抛出异常
        state.registerKeyStrategy(null, AppendStrategy.getInstance());
        state.registerKeyStrategy("key1", null);
    }

    @Test
    void testUpdateWithStrategyUsesAppendStrategy() {
        MapState state = new MapState();
        state.registerKeyStrategy("messages", AppendStrategy.getInstance());

        List<String> list1 = List.of("msg1");
        List<String> list2 = List.of("msg2");
        List<String> list3 = List.of("msg3");

        state.update("messages", list1);
        state.update("messages", list2);
        state.update("messages", list3);

        @SuppressWarnings("unchecked")
        List<String> result = state.<List<String>>value("messages").orElse(null);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains("msg1"));
        assertTrue(result.contains("msg2"));
        assertTrue(result.contains("msg3"));
    }

    @Test
    void testUpdateWithStrategyUsesReplaceStrategy() {
        MapState state = new MapState();
        state.registerKeyStrategy("count", ReplaceStrategy.getInstance());

        state.update("count", 1);
        state.update("count", 2);
        state.update("count", 3);

        assertEquals(Integer.valueOf(3), state.<Integer>value("count").orElse(null));
    }

    @Test
    void testUpdateWithCustomStrategy() {
        MapState state = new MapState();

        // 自定义策略：将字符串值转换为大写
        KeyStrategy<String> uppercaseStrategy = (existing, newValue) ->
                newValue != null ? newValue.toUpperCase() : null;

        state.registerKeyStrategy("text", uppercaseStrategy);
        state.update("text", "hello");

        assertEquals("HELLO", state.value("text").orElse(null));
    }

    @Test
    void testMergeWithStrategy() {
        MapState state = new MapState();
        state.registerKeyStrategy("items", AppendStrategy.getInstance());

        state.merge(Map.of("items", List.of("a", "b")));
        state.merge(Map.of("items", List.of("c")));
        state.merge(Map.of("items", List.of("d")));

        @SuppressWarnings("unchecked")
        List<String> items = state.<List<String>>value("items").orElse(null);

        assertNotNull(items);
        assertEquals(4, items.size());
        assertTrue(items.contains("a"));
        assertTrue(items.contains("b"));
        assertTrue(items.contains("c"));
        assertTrue(items.contains("d"));
    }

    @Test
    void testMergeWithMixedStrategies() {
        MapState state = new MapState();
        state.registerKeyStrategy("messages", AppendStrategy.getInstance());
        state.registerKeyStrategy("count", ReplaceStrategy.getInstance());

        state.merge(Map.of(
                "messages", List.of("msg1"),
                "count", 1
        ));

        state.merge(Map.of(
                "messages", List.of("msg2"),
                "count", 2
        ));

        @SuppressWarnings("unchecked")
        List<String> messages = state.<List<String>>value("messages").orElse(null);
        Integer count = state.<Integer>value("count").orElse(null);

        assertEquals(2, messages.size());
        assertTrue(messages.contains("msg1"));
        assertTrue(messages.contains("msg2"));

        assertEquals(Integer.valueOf(2), count);
    }

    @Test
    void testUpdateWithStrategyAndNullValueRemovesKey() {
        MapState state = new MapState();
        state.registerKeyStrategy("messages", AppendStrategy.getInstance());

        state.update("messages", List.of("msg1"));
        assertTrue(state.containsKey("messages"));

        // 即使有策略，null 值也应该删除键
        state.update("messages", null);

        assertFalse(state.containsKey("messages"));
    }

    @Test
    void testCopy() {
        MapState state = new MapState();
        state.registerKeyStrategy("messages", AppendStrategy.getInstance());
        state.update("key1", "value1");
        state.update("key2", "value2");

        MapState copy = state.copy();

        // 验证副本包含相同的数据
        assertEquals(2, copy.size());
        assertEquals("value1", copy.value("key1").orElse(null));
        assertEquals("value2", copy.value("key2").orElse(null));

        // 验证副本有相同的策略
        copy.update("messages", List.of("msg1"));
        copy.update("messages", List.of("msg2"));

        @SuppressWarnings("unchecked")
        List<String> messages = copy.<List<String>>value("messages").orElse(null);
        assertEquals(2, messages.size());

        // 修改副本不应影响原状态
        copy.update("key1", "modified");
        assertEquals("value1", state.value("key1").orElse(null));
        assertEquals("modified", copy.value("key1").orElse(null));
    }

    @Test
    void testToString() {
        MapState state = new MapState();
        state.update("key1", "value1");
        state.update("key2", 123);

        String str = state.toString();

        assertTrue(str.contains("MapState"));
        assertTrue(str.contains("key1"));
        assertTrue(str.contains("value1"));
    }

    @Test
    void testComplexScenarioSimulatingReActLoop() {
        // 模拟 ReAct 循环中的状态变化
        MapState state = new MapState();
        state.registerKeyStrategy("messages", AppendStrategy.getInstance());
        state.registerKeyStrategy("iteration", ReplaceStrategy.getInstance());

        // 初始状态
        state.merge(Map.of(
                "messages", List.of("user: What's the weather?"),
                "iteration", 0
        ));

        // 第一次迭代 - LLM 响应
        state.merge(Map.of(
                "messages", List.of("assistant: I'll check the weather."),
                "iteration", 1
        ));

        // 第二次迭代 - 工具调用结果
        state.merge(Map.of(
                "messages", List.of("tool: Weather is 25°C"),
                "iteration", 2
        ));

        // 第三次迭代 - 最终响应
        state.merge(Map.of(
                "messages", List.of("assistant: The weather is 25°C today."),
                "iteration", 3
        ));

        // 验证最终状态
        @SuppressWarnings("unchecked")
        List<String> messages = state.<List<String>>value("messages").orElse(null);
        Integer iteration = state.<Integer>value("iteration").orElse(null);

        assertEquals(4, messages.size());
        assertEquals("user: What's the weather?", messages.get(0));
        assertEquals("assistant: I'll check the weather.", messages.get(1));
        assertEquals("tool: Weather is 25°C", messages.get(2));
        assertEquals("assistant: The weather is 25°C today.", messages.get(3));

        assertEquals(Integer.valueOf(3), iteration);
    }

    @Test
    void testUpdateSameKeyWithDifferentStrategies() {
        MapState state = new MapState();
        state.registerKeyStrategy("append_key", AppendStrategy.getInstance());
        state.registerKeyStrategy("replace_key", ReplaceStrategy.getInstance());

        // 使用追加策略
        state.update("append_key", List.of("a"));
        state.update("append_key", List.of("b"));
        state.update("append_key", List.of("c"));

        @SuppressWarnings("unchecked")
        List<String> appendResult = state.<List<String>>value("append_key").orElse(null);
        assertEquals(3, appendResult.size());

        // 使用替换策略
        state.update("replace_key", "value1");
        state.update("replace_key", "value2");
        state.update("replace_key", "value3");

        assertEquals("value3", state.value("replace_key").orElse(null));
    }
}
