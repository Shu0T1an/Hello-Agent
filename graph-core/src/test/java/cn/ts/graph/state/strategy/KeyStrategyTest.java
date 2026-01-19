package cn.ts.graph.state.strategy;

import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KeyStrategy 单元测试
 * <p>
 * 测试各种状态合并策略的行为
 * </p>
 *
 * @author tianshuo
 */
class KeyStrategyTest {

    @Test
    void testReplaceStrategy() {
        ReplaceStrategy<Integer> strategy = ReplaceStrategy.getInstance();

        // 测试基本替换
        assertEquals(10, strategy.merge(5, 10));
        assertEquals(10, strategy.merge(null, 10));
        assertNull(strategy.merge(5, null));

        // 测试字符串替换
        ReplaceStrategy<String> stringStrategy = ReplaceStrategy.getInstance();
        assertEquals("new", stringStrategy.merge("old", "new"));

        // 测试对象替换
        ReplaceStrategy<List<String>> listStrategy = ReplaceStrategy.getInstance();
        List<String> oldList = List.of("a", "b");
        List<String> newList = List.of("c", "d");
        assertSame(newList, listStrategy.merge(oldList, newList));
    }

    @Test
    void testAppendStrategyWithList() {
        AppendStrategy<List<String>> strategy = AppendStrategy.getInstance();

        // 测试 List 追加
        List<String> existing = new ArrayList<>(List.of("a", "b"));
        List<String> newValue = List.of("c");

        List<String> result = strategy.merge(existing, newValue);
        assertEquals(List.of("a", "b", "c"), result);

        // 验证旧列表未被修改
        assertEquals(List.of("a", "b"), existing);
    }

    @Test
    void testAppendStrategyWithMap() {
        AppendStrategy<Map<String, Integer>> strategy = AppendStrategy.getInstance();

        // 测试 Map 合并
        Map<String, Integer> existing = new HashMap<>(Map.of("a", 1, "b", 2));
        Map<String, Integer> newValue = Map.of("c", 3, "a", 10); // a 会被覆盖

        Map<String, Integer> result = strategy.merge(existing, newValue);
        assertEquals(3, result.size());
        assertEquals(10, result.get("a")); // 新值覆盖旧值
        assertEquals(2, result.get("b"));
        assertEquals(3, result.get("c"));

        // 验证旧 Map 未被修改
        assertEquals(1, existing.get("a"));
    }

    @Test
    void testAppendStrategyWithString() {
        AppendStrategy<String> strategy = AppendStrategy.getInstance();

        // 测试 String 连接
        String result = strategy.merge("Hello", " World");
        assertEquals("Hello World", result);

        // 测试空字符串
        assertEquals("Hello", strategy.merge("", "Hello"));
        assertEquals("World", strategy.merge(null, "World"));
        assertEquals("Hello", strategy.merge("Hello", null));
    }

    @Test
    void testAppendStrategyWithNull() {
        AppendStrategy<List<String>> strategy = AppendStrategy.getInstance();

        // 测试 null 值处理
        List<String> list = List.of("a", "b");
        assertSame(list, strategy.merge(null, list));
        assertSame(list, strategy.merge(list, null));
        assertNull(strategy.merge(null, null));
    }

    @Test
    void testAppendStrategyWithUnsupportedType() {
        AppendStrategy<Integer> strategy = AppendStrategy.getInstance();

        // 对于不支持的类型，回退到替换策略
        assertEquals(10, strategy.merge(5, 10));
        assertEquals(Integer.valueOf(10), strategy.merge(null, 10));
    }

    @Test
    void testMapStateWithStrategies() {
        MapState state = new MapState();
        state.registerKeyStrategy("messages", AppendStrategy.getInstance());
        state.registerKeyStrategy("count", ReplaceStrategy.getInstance());

        // 测试追加策略
        state.update("messages", List.of("msg1"));
        state.update("messages", List.of("msg2"));
        state.update("messages", List.of("msg3"));


        @SuppressWarnings("unchecked")
        List<String> messages = (List<String>) (Object) state.value("messages").orElse(null);
        assertNotNull(messages);
        assertEquals(List.of("msg1", "msg2", "msg3"), messages);

        // 测试替换策略
        state.update("count", 1);
        state.update("count", 2);
        state.update("count", 3);

        Integer count = state.<Integer>value("count").orElse(null);
        assertNotNull(count);
        assertEquals(3, count);
    }

    @Test
    void testMapStateMergeWithStrategies() {
        MapState state = new MapState();
        state.registerKeyStrategy("items", AppendStrategy.getInstance());
        state.registerKeyStrategy("total", ReplaceStrategy.getInstance());

        // 使用 merge 方法批量更新
        state.merge(Map.of(
                "items", List.of("a", "b"),
                "total", 10
        ));

        assertEquals(List.of("a", "b"), state.value("items").orElse(null));
        assertEquals(10, state.value("total").orElse(null));

        // 再次 merge
        state.merge(Map.of(
                "items", List.of("c"),
                "total", 15
        ));

        assertEquals(List.of("a", "b", "c"), state.value("items").orElse(null));
        assertEquals(15, state.value("total").orElse(null));
    }

    @Test
    void testMapStateDefaultBehavior() {
        MapState state = new MapState();

        // 未注册策略时，默认使用替换策略
        state.update("key1", "value1");
        state.update("key1", "value2");

        assertEquals("value2", state.value("key1").orElse(null));
    }

    @Test
    void testMapStateRemoveValue() {
        MapState state = new MapState();
        state.registerKeyStrategy("items", AppendStrategy.getInstance());

        state.update("items", List.of("a", "b"));
        assertNotNull(state.value("items").orElse(null));

        // 设置为 null 应该删除键
        state.update("items", null);
        assertFalse(state.containsKey("items"));
    }

    @Test
    void testStrategySingleton() {
        // 验证策略是单例的
        ReplaceStrategy<Integer> replace1 = ReplaceStrategy.getInstance();
        ReplaceStrategy<String> replace2 = ReplaceStrategy.getInstance();
        assertSame(replace1, replace2);

        AppendStrategy<Integer> append1 = AppendStrategy.getInstance();
        AppendStrategy<String> append2 = AppendStrategy.getInstance();
        assertSame(append1, append2);
    }

    @Test
    void testEmptyListAppend() {
        AppendStrategy<List<String>> strategy = AppendStrategy.getInstance();

        // 追加空列表
        List<String> existing = new ArrayList<>(List.of("a", "b"));
        List<String> result = strategy.merge(existing, List.of());

        assertEquals(List.of("a", "b"), result);
    }

    @Test
    void testComplexScenario() {
        // 模拟 ReAct 循环中的状态更新
        MapState state = new MapState();
        state.registerKeyStrategy("messages", AppendStrategy.getInstance());
        state.registerKeyStrategy("iteration", ReplaceStrategy.getInstance());

        // 第一次迭代
        state.merge(Map.of(
                "messages", List.of("user: What's the weather?"),
                "iteration", 0
        ));

        state.merge(Map.of(
                "messages", List.of("assistant: I'll check the weather for you."),
                "iteration", 1
        ));

        state.merge(Map.of(
                "messages", List.of("tool: Weather in Beijing is 25°C"),
                "iteration", 2
        ));

        // 验证 messages 被正确追加
        @SuppressWarnings("unchecked")
        List<String> messages = (List<String>) (Object) state.value("messages").orElse(null);
        assertNotNull(messages);
        assertEquals(3, messages.size());
        assertEquals("user: What's the weather?", messages.get(0));
        assertEquals("assistant: I'll check the weather for you.", messages.get(1));
        assertEquals("tool: Weather in Beijing is 25°C", messages.get(2));

        // 验证 iteration 被正确替换
        assertEquals(2, state.<Integer>value("iteration").orElse(null));
    }
}
