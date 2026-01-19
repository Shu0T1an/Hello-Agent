package cn.ts.agent.model;

import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatModelRequest 单元测试
 *
 * @author tianshuo
 */
class ChatModelRequestTest {

    @Test
    void testBuildRequestWithInput() {
        State state = new MapState(Map.of("input", "Hello, AI!"));
        ChatModelRequest request = ChatModelRequest.builder(state)
                .systemPrompt("You are a helpful assistant.")
                .build();

        List<Message> messages = request.getMessages();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0) instanceof UserMessage);
        // UserMessage 的 content 通过 getText() 获取
        assertEquals("Hello, AI!", ((UserMessage) messages.get(0)).getText());
    }

    @Test
    void testBuildRequestWithExistingMessages() {
        List<Message> existingMsgs = List.of(new UserMessage("Previous message"));
        State state = new MapState(Map.of("messages", existingMsgs));

        ChatModelRequest request = ChatModelRequest.builder(state).build();

        List<Message> messages = request.getMessages();
        assertEquals(1, messages.size());
        assertEquals("Previous message", ((UserMessage) messages.get(0)).getText());
    }

    @Test
    void testBuildRequestWithEmptyState() {
        State state = new MapState(Map.of());
        ChatModelRequest request = ChatModelRequest.builder(state).build();

        List<Message> messages = request.getMessages();
        assertTrue(messages.isEmpty());
    }

    @Test
    void testBuilderPattern() {
        State state = new MapState(Map.of("input", "Test input"));

        ChatModelRequest request = ChatModelRequest.builder(state)
                .systemPrompt("System prompt")
                .build();

        assertNotNull(request);
        assertEquals(1, request.getMessages().size());
    }

    @Test
    void testGetMessagesReturnsNewList() {
        State state = new MapState(Map.of("input", "Test"));
        ChatModelRequest request = ChatModelRequest.builder(state).build();

        List<Message> messages1 = request.getMessages();
        List<Message> messages2 = request.getMessages();

        // 验证返回的是新列表，不是同一个引用
        assertNotSame(messages1, messages2);
        assertEquals(messages1.size(), messages2.size());
    }

    @Test
    void testNullToolCallbacks() {
        State state = new MapState(Map.of("input", "Test"));

        // 不设置 toolCallbacks，应该使用空列表
        ChatModelRequest request = ChatModelRequest.builder(state)
                .toolCallbacks(null)
                .build();

        assertNotNull(request);
        assertEquals(1, request.getMessages().size());
    }
}
