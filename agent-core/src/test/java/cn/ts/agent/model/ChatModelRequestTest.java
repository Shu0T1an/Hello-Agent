package cn.ts.agent.model;

import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ChatModelRequest 单元测试
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

        assertNotSame(messages1, messages2);
        assertEquals(messages1.size(), messages2.size());
    }

    @Test
    void testNullToolCallbacks() {
        State state = new MapState(Map.of("input", "Test"));

        ChatModelRequest request = ChatModelRequest.builder(state)
                .toolCallbacks(null)
                .build();

        assertNotNull(request);
        assertEquals(1, request.getMessages().size());
    }

    @Test
    void injectsFallbackUserMessageWhenRequestMessagesHaveNoUserRole() {
        State state = new MapState(Map.of(
                "input", "fallback-user",
                "messages", List.of(new AssistantMessage("assistant-only"))
        ));
        ChatModelRequest request = ChatModelRequest.builder(state).build();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        ChatClient chatClient = mockChatClient(messagesCaptor);
        request.buildRequest(chatClient);

        List<Message> sentMessages = messagesCaptor.getValue();
        assertTrue(sentMessages.stream()
                .anyMatch(message -> message instanceof UserMessage user && "fallback-user".equals(user.getText())));
    }

    @Test
    void filtersToolResponsesWithMissingToolCallId() {
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("call-1", "function", "task", "{}");
        AssistantMessage assistantWithToolCall = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall))
                .build();
        ToolResponseMessage invalidToolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("", "task", "ok")))
                .build();

        State state = new MapState(Map.of(
                "input", "fallback-user",
                "messages", List.of(assistantWithToolCall, invalidToolResponse)
        ));
        ChatModelRequest request = ChatModelRequest.builder(state).build();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        ChatClient chatClient = mockChatClient(messagesCaptor);
        request.buildRequest(chatClient);

        List<Message> sentMessages = messagesCaptor.getValue();
        assertTrue(sentMessages.stream().noneMatch(ToolResponseMessage.class::isInstance));
    }

    @Test
    void keepsToolResponsesWhenToolCallIdMatchesAssistantToolCall() {
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("call-1", "function", "task", "{}");
        AssistantMessage assistantWithToolCall = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall))
                .build();
        ToolResponseMessage validToolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "task", "ok")))
                .build();

        State state = new MapState(Map.of(
                "messages", List.of(new UserMessage("do it"), assistantWithToolCall, validToolResponse)
        ));
        ChatModelRequest request = ChatModelRequest.builder(state).build();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        ChatClient chatClient = mockChatClient(messagesCaptor);
        request.buildRequest(chatClient);

        List<Message> sentMessages = messagesCaptor.getValue();
        long toolMessageCount = sentMessages.stream().filter(ToolResponseMessage.class::isInstance).count();
        assertEquals(1L, toolMessageCount);
    }

    private ChatClient mockChatClient(ArgumentCaptor<List<Message>> messagesCaptor) {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(messagesCaptor.capture())).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(anyList())).thenReturn(requestSpec);
        return chatClient;
    }
}
