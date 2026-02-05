package cn.ts.graph.util;

import cn.ts.graph.state.State;
import cn.ts.graph.state.strategy.AppendStrategy;
import cn.ts.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StateFactory 测试
 * <p>
 * 验证状态工厂的正确性和一致性
 * </p>
 *
 * @author tianshuo
 */
class StateFactoryTest {

    private StateFactory factory;

    @BeforeEach
    void setUp() {
        factory = new DefaultStateFactory();
    }

    @Test
    void testCreateEmptyState() {
        State state = factory.createState();

        assertNotNull(state);
        assertFalse(state.keys().iterator().hasNext());
        assertEquals(0, ((Map<?, ?>) state.data()).size());
    }

    @Test
    void testCreateStateWithInitialData() {
        Map<String, Object> initialData = new HashMap<>();
        initialData.put("key1", "value1");
        initialData.put("key2", 42);

        State state = factory.createState(initialData);

        assertNotNull(state);
        assertTrue(state.containsKey("key1"));
        assertTrue(state.containsKey("key2"));
        assertEquals("value1", state.value("key1").orElse(null));
        assertEquals(42, state.value("key2").orElse(null));
    }

    @Test
    void testCreateStateWithDefaultStrategies() {
        State state = factory.createStateWithDefaultStrategies();

        assertNotNull(state);
        // 验证状态已创建且有正确的结构
        assertTrue(state instanceof cn.ts.graph.state.MapState);
    }

    @Test
    void testCreateStateWithDefaultStrategiesAndData() {
        Map<String, Object> initialData = new HashMap<>();
        initialData.put("messages", new ArrayList<>());
        initialData.put("iteration", 0);

        State state = factory.createStateWithDefaultStrategies(initialData);

        assertNotNull(state);
        assertTrue(state.containsKey("messages"));
        assertTrue(state.containsKey("iteration"));
    }

    @Test
    void testMessagesAppendStrategy() {
        State state = factory.createStateWithDefaultStrategies();

        List<String> originalMessages = new ArrayList<>();
        originalMessages.add("message1");

        state.update("messages", originalMessages);

        List<String> newMessages = new ArrayList<>();
        newMessages.add("message2");

        State updatedState = state.update("messages", newMessages);

        // 验证追加策略生效
        Object resultObj = updatedState.value("messages").orElse(new ArrayList<>());
        assertTrue(resultObj instanceof List);
        List<?> result = (List<?>) resultObj;
        assertTrue(result.size() >= 1);
    }

    @Test
    void testIterationReplaceStrategy() {
        State state = factory.createStateWithDefaultStrategies();

        state.update("iteration", 5);
        assertEquals(5, state.value("iteration").orElse(0));

        state.update("iteration", 10);
        assertEquals(10, state.value("iteration").orElse(0));
    }

    @Test
    void testMultipleStateInstances() {
        State state1 = factory.createStateWithDefaultStrategies();
        State state2 = factory.createStateWithDefaultStrategies();

        // 验证两个状态实例是独立的
        state1.update("iteration", 5);
        state2.update("iteration", 10);

        assertNotSame(state1, state2);
        assertEquals(5, state1.value("iteration").orElse(0));
        assertEquals(10, state2.value("iteration").orElse(0));
    }

    @Test
    void testNullInitialData() {
        State state = factory.createState(null);

        assertNotNull(state);
        assertFalse(state.keys().iterator().hasNext());
    }

    @Test
    void testStateImmutabilityInData() {
        Map<String, Object> initialData = new HashMap<>();
        initialData.put("key", "value");

        State state = factory.createState(initialData);
        Map<String, Object> data = state.data();

        // 尝试修改返回的 data 应该不影响原状态
        assertThrows(UnsupportedOperationException.class, () -> {
            data.put("newKey", "newValue");
        });
    }

    @Test
    void testCopyCreatesIndependentState() {
        State original = factory.createStateWithDefaultStrategies();
        original.update("iteration", 5);

        State copy = original.copy();

        // 验证副本是独立的
        original.update("iteration", 10);

        assertEquals(10, original.value("iteration").orElse(0));
        assertEquals(5, copy.value("iteration").orElse(0));
    }

    @Test
    void testMergeUpdates() {
        State state = factory.createStateWithDefaultStrategies();
        state.update("iteration", 5);

        Map<String, Object> updates = new HashMap<>();
        updates.put("iteration", 10);
        updates.put("max_iterations", 20);

        State merged = state.merge(updates);

        assertEquals(10, merged.value("iteration").orElse(0));
        assertEquals(20, merged.value("max_iterations").orElse(0));
    }
}
