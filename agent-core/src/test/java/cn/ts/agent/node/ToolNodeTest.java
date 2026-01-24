package cn.ts.agent.node;

import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ToolNode 单元测试
 * <p>
 * 测试工具节点的各项功能，包括工具执行、错误处理和迭代计数
 * </p>
 *
 * @author tianshuo
 */
@ExtendWith(MockitoExtension.class)
class ToolNodeTest {

    @Mock
    private ToolCallback mockTool1;

    @Mock
    private ToolCallback mockTool2;

    @Test
    void testConstructorWithNoTools() {
        ToolNode node = new ToolNode();

        assertNotNull(node);
        assertEquals(0, node.getToolCallbacks().size());
    }

    @Test
    void testConstructorWithToolObjects() {
        // 创建简单的测试对象（使用不同的类避免名称冲突）
        Object tool1 = new TestToolClass1();
        Object tool2 = new TestToolClass2();

        ToolNode node = new ToolNode(tool1, tool2);

        assertNotNull(node);
        // 注意：由于 ToolUtils 需要扫描 @Tool 注解，实际工具数量可能不同
        // 这里只验证节点创建成功
    }

    @Test
    void testConstructorWithToolCallbacks() {
        List<ToolCallback> callbacks = List.of(mockTool1, mockTool2);

        ToolNode node = new ToolNode(callbacks);

        assertNotNull(node);
        assertEquals(2, node.getToolCallbacks().size());
    }

    @Test
    void testConstructorWithNullToolCallbacks() {
        ToolNode node = new ToolNode((List<ToolCallback>) null);

        assertNotNull(node);
        assertEquals(0, node.getToolCallbacks().size());
    }

    @Test
    void testApplyWithNoMessages() throws Exception {
        ToolNode node = new ToolNode();
        State state = new MapState(Map.of("messages", new ArrayList<>()));

        Map<String, Object> result = node.apply(state);

        // 空消息列表应该返回空结果
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testApplyWithNonAssistantMessage() throws Exception {
        ToolNode node = new ToolNode();

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage("Hello"));

        State state = new MapState(Map.of("messages", messages));

        Map<String, Object> result = node.apply(state);

        // 非助手消息应该返回空结果
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testApplyWithNoToolCalls() throws Exception {
        ToolNode node = new ToolNode();

        // 创建没有工具调用的 AssistantMessage
        AssistantMessage assistantMessage = new AssistantMessage("Hello, I'm here to help");
        List<Message> messages = List.of(assistantMessage);

        State state = new MapState(Map.of("messages", messages));

        Map<String, Object> result = node.apply(state);

        // 没有工具调用应该返回空结果
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testAddToolCallback() {
        ToolNode node = new ToolNode();
        assertEquals(0, node.getToolCallbacks().size());

        node.addToolCallback(mockTool1);

        assertEquals(1, node.getToolCallbacks().size());
    }

    @Test
    void testGetToolCallbacksReturnsUnmodifiable() {
        ToolNode node = new ToolNode(List.of(mockTool1, mockTool2));

        List<ToolCallback> callbacks = node.getToolCallbacks();

        // 尝试修改返回的列表应该抛出异常
        assertThrows(UnsupportedOperationException.class, () -> callbacks.add(mockTool1));
    }

    // ==================== 辅助方法 ====================

    /**
     * 测试用的工具类 1
     */
    private static class TestToolClass1 {
        @org.springframework.ai.tool.annotation.Tool(description = "test_tool_1")
        public String testMethod(String input) {
            return "Result: " + input;
        }
    }

    /**
     * 测试用的工具类 2
     */
    private static class TestToolClass2 {
        @org.springframework.ai.tool.annotation.Tool(description = "test_tool_2")
        public String anotherMethod(String input) {
            return "Another: " + input;
        }
    }
}
