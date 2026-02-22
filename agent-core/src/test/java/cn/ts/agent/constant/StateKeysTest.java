package cn.ts.agent.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StateKeys 常量类测试
 * <p>
 * 验证常量定义的正确性和一致性
 * </p>
 *
 * @author tianshuo
 */
class StateKeysTest {

    @Test
    void testStateKeysConstants() {
        // 验证基础状态键常量不为空且符合预期
        assertNotNull(StateKeys.INPUT);
        assertEquals("input", StateKeys.INPUT);

        assertNotNull(StateKeys.MESSAGES);
        assertEquals("messages", StateKeys.MESSAGES);

        assertNotNull(StateKeys.ITERATION);
        assertEquals("iteration", StateKeys.ITERATION);

        assertNotNull(StateKeys.MAX_ITERATIONS);
        assertEquals("max_iterations", StateKeys.MAX_ITERATIONS);

        assertNotNull(StateKeys.EXECUTE_RECORD);
        assertEquals("execute_record", StateKeys.EXECUTE_RECORD);
    }

    @Test
    void testChatResponseStateKey() {
        assertEquals("chat_response", StateKeys.CHAT_RESPONSE);
    }

    @Test
    void testJumpToStateKey() {
        assertEquals("jump_to", StateKeys.JUMP_TO);
    }

    @Test
    void testAgentRelatedStateKeys() {
        assertEquals("current_agent", StateKeys.CURRENT_AGENT);
        assertEquals("agent_history", StateKeys.AGENT_HISTORY);
    }

    @Test
    void testInterruptionStateKeys() {
        assertEquals("interruption", StateKeys.INTERRUPTION);
        assertEquals("interrupted", StateKeys.INTERRUPTED);
        assertEquals("clarification_round", StateKeys.CLARIFICATION_ROUND);
        assertEquals("clarification_last_signature", StateKeys.CLARIFICATION_LAST_SIGNATURE);
    }

    @Test
    void testStateKeysUniqueness() {
        // 验证所有状态键都是唯一的
        assertNotEquals(StateKeys.INPUT, StateKeys.MESSAGES);
        assertNotEquals(StateKeys.ITERATION, StateKeys.MAX_ITERATIONS);
        assertNotEquals(StateKeys.CURRENT_AGENT, StateKeys.AGENT_HISTORY);
        assertNotEquals(StateKeys.INTERRUPTION, StateKeys.INTERRUPTED);
        assertNotEquals(StateKeys.CLARIFICATION_ROUND, StateKeys.CLARIFICATION_LAST_SIGNATURE);
    }

    @Test
    void testStateKeysNotModified() {
        // 确保常量不会被意外修改
        String originalInput = StateKeys.INPUT;
        String originalMessages = StateKeys.MESSAGES;

        // 尝试修改（这应该不影响常量本身，只是验证字符串内容）
        String tempInput = StateKeys.INPUT + "_temp";
        assertNotEquals(StateKeys.INPUT, tempInput);
        assertEquals(StateKeys.INPUT, originalInput);
        assertEquals(StateKeys.MESSAGES, originalMessages);
    }

    @Test
    void testStateKeysFormat() {
        // 验证状态键命名规范：小写，使用下划线分隔
        String regex = "^[a-z][a-z0-9_]*$";

        assertTrue(StateKeys.INPUT.matches(regex));
        assertTrue(StateKeys.MESSAGES.matches(regex));
        assertTrue(StateKeys.ITERATION.matches(regex));
        assertTrue(StateKeys.MAX_ITERATIONS.matches(regex));
        assertTrue(StateKeys.EXECUTE_RECORD.matches(regex));
        assertTrue(StateKeys.CHAT_RESPONSE.matches(regex));
        assertTrue(StateKeys.JUMP_TO.matches(regex));
        assertTrue(StateKeys.CURRENT_AGENT.matches(regex));
        assertTrue(StateKeys.AGENT_HISTORY.matches(regex));
        assertTrue(StateKeys.INTERRUPTION.matches(regex));
        assertTrue(StateKeys.INTERRUPTED.matches(regex));
        assertTrue(StateKeys.CLARIFICATION_ROUND.matches(regex));
        assertTrue(StateKeys.CLARIFICATION_LAST_SIGNATURE.matches(regex));
    }
}
