package cn.ts.web.channel.runtime;

import cn.ts.agent.constant.StateKeys;
import cn.ts.web.agent.dto.AgentResponse;
import cn.ts.web.agent.service.AgentExecutionService;
import cn.ts.web.channel.dto.ChannelInboundMessage;
import cn.ts.web.channel.entity.ChannelSessionMappingEntity;
import cn.ts.web.channel.mapper.ChannelSessionMappingMapper;
import cn.ts.web.channel.runtime.command.ChannelCommandRegistry;
import cn.ts.web.channel.runtime.command.ChannelSlashCommandParser;
import cn.ts.web.channel.runtime.command.ClearCommandHandler;
import cn.ts.web.session.dto.GraphStateVO;
import cn.ts.web.session.dto.SessionDetailDTO;
import cn.ts.web.session.service.GraphStateService;
import cn.ts.web.session.service.MessageConversionService;
import cn.ts.web.session.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelMessageDispatcherTest {

    @Mock
    private AgentExecutionService agentExecutionService;

    @Mock
    private SessionService sessionService;

    @Mock
    private ChannelSessionMappingMapper channelSessionMappingMapper;

    @Mock
    private GraphStateService graphStateService;

    @Mock
    private MessageConversionService messageConversionService;

    private ChannelMessageDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        ChannelSlashCommandParser parser = new ChannelSlashCommandParser();
        ClearCommandHandler clearCommandHandler = new ClearCommandHandler(sessionService, channelSessionMappingMapper);
        ChannelCommandRegistry registry = new ChannelCommandRegistry(List.of(clearCommandHandler));

        dispatcher = new ChannelMessageDispatcher(
                agentExecutionService,
                sessionService,
                channelSessionMappingMapper,
                graphStateService,
                messageConversionService,
                parser,
                registry
        );
        setDefaultAgent("general-purpose");
    }

    @Test
    void dispatchWithReply_ShouldCollectLlmChunksAndInvokeCallback() {
        ChannelInboundMessage inbound = inbound("dingtalk", "conversation-1", "hello");

        ChannelSessionMappingEntity mapping = new ChannelSessionMappingEntity();
        mapping.setInternalSessionId("session-1");
        when(channelSessionMappingMapper.selectByExternal("dingtalk", "conversation-1")).thenReturn(mapping);
        when(sessionService.sessionExists("session-1")).thenReturn(true);
        when(agentExecutionService.executeAgentStreamWithSession(anyString(), anyMap(), anyString(), any(Duration.class)))
                .thenReturn(Flux.just(chunkResponse("he"), chunkResponse("llo")));

        List<String> replies = new ArrayList<>();
        dispatcher.dispatch(inbound, replies::add);

        assertEquals(List.of("hello"), replies);

        ArgumentCaptor<Map<String, Object>> stateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(agentExecutionService).executeAgentStreamWithSession(
                eq("general-purpose"),
                stateCaptor.capture(),
                eq("session-1"),
                eq(Duration.ofSeconds(120))
        );
        assertStateWithUserInput(stateCaptor.getValue(), "hello");
    }

    @Test
    void dispatchWithReply_ShouldUseFallbackMessageWhenNoChunk() {
        ChannelInboundMessage inbound = inbound("dingtalk", "conversation-2", "help");

        ChannelSessionMappingEntity mapping = new ChannelSessionMappingEntity();
        mapping.setInternalSessionId("session-2");
        when(channelSessionMappingMapper.selectByExternal("dingtalk", "conversation-2")).thenReturn(mapping);
        when(sessionService.sessionExists("session-2")).thenReturn(true);
        when(agentExecutionService.executeAgentStreamWithSession(anyString(), anyMap(), anyString(), any(Duration.class)))
                .thenReturn(Flux.just(AgentResponse.builder().message("busy").build()));

        List<String> replies = new ArrayList<>();
        dispatcher.dispatch(inbound, replies::add);

        assertEquals(List.of("busy"), replies);
    }

    @Test
    void dispatch_ShouldCreateSessionAndPersistMapping_WhenNoMappingExists() {
        ChannelInboundMessage inbound = inbound("dingtalk", "conversation-3", "create session");

        SessionDetailDTO sessionDetail = session("session-new-1");
        when(channelSessionMappingMapper.selectByExternal("dingtalk", "conversation-3")).thenReturn(null);
        when(sessionService.sessionExists("conversation-3")).thenReturn(false);
        when(sessionService.createSession(anyString(), anyString())).thenReturn(sessionDetail);
        when(agentExecutionService.executeAgentStreamWithSession(anyString(), anyMap(), anyString(), any(Duration.class)))
                .thenReturn(Flux.empty());

        dispatcher.dispatch(inbound);

        verify(sessionService).createSession("general-purpose", "Channel Session");
        ArgumentCaptor<ChannelSessionMappingEntity> mappingCaptor = ArgumentCaptor.forClass(ChannelSessionMappingEntity.class);
        verify(channelSessionMappingMapper).insert(mappingCaptor.capture());
        assertEquals("dingtalk", mappingCaptor.getValue().getChannelType());
        assertEquals("conversation-3", mappingCaptor.getValue().getExternalSessionId());
        assertEquals("session-new-1", mappingCaptor.getValue().getInternalSessionId());

        ArgumentCaptor<Map<String, Object>> stateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(agentExecutionService).executeAgentStreamWithSession(
                eq("general-purpose"),
                stateCaptor.capture(),
                eq("session-new-1"),
                eq(Duration.ofSeconds(120))
        );
        assertStateWithUserInput(stateCaptor.getValue(), "create session");
    }

    @Test
    void dispatch_ShouldRefreshMapping_WhenMappedSessionMissing() {
        ChannelInboundMessage inbound = inbound("dingtalk", "conversation-4", "refresh session");

        ChannelSessionMappingEntity mapping = new ChannelSessionMappingEntity();
        mapping.setInternalSessionId("session-old-1");
        when(channelSessionMappingMapper.selectByExternal("dingtalk", "conversation-4")).thenReturn(mapping);
        when(sessionService.sessionExists("session-old-1")).thenReturn(false);
        when(sessionService.createSession(anyString(), anyString())).thenReturn(session("session-new-2"));
        when(agentExecutionService.executeAgentStreamWithSession(anyString(), anyMap(), anyString(), any(Duration.class)))
                .thenReturn(Flux.empty());

        dispatcher.dispatch(inbound);

        verify(channelSessionMappingMapper).updateInternalSessionId("dingtalk", "conversation-4", "session-new-2");
        verify(channelSessionMappingMapper, never()).insert(any());
        verify(agentExecutionService).executeAgentStreamWithSession(
                eq("general-purpose"),
                anyMap(),
                eq("session-new-2"),
                eq(Duration.ofSeconds(120))
        );
    }

    @Test
    void dispatch_ShouldAppendToExistingMessages_WhenHistoryExists() {
        ChannelInboundMessage inbound = inbound("dingtalk", "conversation-5", "new question");

        ChannelSessionMappingEntity mapping = new ChannelSessionMappingEntity();
        mapping.setInternalSessionId("session-5");

        GraphStateVO graphStateVO = new GraphStateVO();
        Map<String, Object> stateData = new LinkedHashMap<>();
        stateData.put(StateKeys.MESSAGES, List.of(Map.of("messageType", "USER", "text", "old question")));
        graphStateVO.setStateData(stateData);

        when(channelSessionMappingMapper.selectByExternal("dingtalk", "conversation-5")).thenReturn(mapping);
        when(sessionService.sessionExists("session-5")).thenReturn(true);
        when(graphStateService.sessionExists("session-5")).thenReturn(true);
        when(graphStateService.getGraphState("session-5")).thenReturn(graphStateVO);
        when(messageConversionService.convertStateToMessages(any(List.class)))
                .thenReturn(new ArrayList<>(List.of(new UserMessage("old question"))));
        when(agentExecutionService.executeAgentStreamWithSession(anyString(), anyMap(), anyString(), any(Duration.class)))
                .thenReturn(Flux.empty());

        dispatcher.dispatch(inbound);

        verify(agentExecutionService).executeAgentStreamWithSession(
                eq("general-purpose"),
                argThat(state -> {
                    Object messagesObj = state.get(StateKeys.MESSAGES);
                    if (!(messagesObj instanceof List<?> list) || list.size() != 2) {
                        return false;
                    }
                    if (!(list.get(0) instanceof UserMessage first) || !(list.get(1) instanceof UserMessage second)) {
                        return false;
                    }
                    return "old question".equals(first.getText())
                            && "new question".equals(second.getText())
                            && "new question".equals(state.get(StateKeys.INPUT));
                }),
                eq("session-5"),
                eq(Duration.ofSeconds(120))
        );
    }

    @Test
    void dispatch_ShouldHandleClearCommand_AndSkipAgentExecution() {
        ChannelInboundMessage inbound = inbound("dingtalk", "conversation-6", "/clear");
        when(sessionService.createSession(anyString(), anyString())).thenReturn(session("session-clear-1"));
        when(channelSessionMappingMapper.updateInternalSessionId("dingtalk", "conversation-6", "session-clear-1"))
                .thenReturn(1);

        List<String> replies = new ArrayList<>();
        dispatcher.dispatch(inbound, replies::add);

        verify(sessionService).createSession("general-purpose", "Channel Session");
        verify(channelSessionMappingMapper).updateInternalSessionId("dingtalk", "conversation-6", "session-clear-1");
        verify(agentExecutionService, never()).executeAgentStreamWithSession(anyString(), anyMap(), anyString(), any(Duration.class));
        assertEquals(List.of("已清空上下文，开始新对话。"), replies);
    }

    @Test
    void dispatch_ShouldInsertMapping_WhenClearCommandUpdateMiss() {
        ChannelInboundMessage inbound = inbound("dingtalk", "conversation-7", "/clear");
        when(sessionService.createSession(anyString(), anyString())).thenReturn(session("session-clear-2"));
        when(channelSessionMappingMapper.updateInternalSessionId("dingtalk", "conversation-7", "session-clear-2"))
                .thenReturn(0);

        dispatcher.dispatch(inbound);

        ArgumentCaptor<ChannelSessionMappingEntity> mappingCaptor = ArgumentCaptor.forClass(ChannelSessionMappingEntity.class);
        verify(channelSessionMappingMapper).insert(mappingCaptor.capture());
        assertEquals("dingtalk", mappingCaptor.getValue().getChannelType());
        assertEquals("conversation-7", mappingCaptor.getValue().getExternalSessionId());
        assertEquals("session-clear-2", mappingCaptor.getValue().getInternalSessionId());
        verify(agentExecutionService, never()).executeAgentStreamWithSession(anyString(), anyMap(), anyString(), any(Duration.class));
    }

    @Test
    void dispatch_ShouldReplyUnknownCommand_AndSkipAgentExecution() {
        ChannelInboundMessage inbound = inbound("dingtalk", "conversation-8", "/abc");

        List<String> replies = new ArrayList<>();
        dispatcher.dispatch(inbound, replies::add);

        assertEquals(List.of("未知命令：/abc，当前支持：/clear"), replies);
        verify(agentExecutionService, never()).executeAgentStreamWithSession(anyString(), anyMap(), anyString(), any(Duration.class));
        verify(sessionService, never()).createSession(anyString(), anyString());
    }

    private ChannelInboundMessage inbound(String channelType, String channelSessionId, String text) {
        ChannelInboundMessage inbound = new ChannelInboundMessage();
        inbound.setChannelType(channelType);
        inbound.setChannelSessionId(channelSessionId);
        inbound.setText(text);
        return inbound;
    }

    private SessionDetailDTO session(String sessionId) {
        SessionDetailDTO detail = new SessionDetailDTO();
        detail.setId(sessionId);
        return detail;
    }

    private AgentResponse chunkResponse(String chunk) {
        return AgentResponse.builder()
                .message(chunk)
                .metadata(Map.of("chunk", chunk))
                .build();
    }

    @SuppressWarnings("unchecked")
    private void assertStateWithUserInput(Map<String, Object> state, String input) {
        assertEquals(input, state.get(StateKeys.INPUT));
        Object messagesObj = state.get(StateKeys.MESSAGES);
        assertInstanceOf(List.class, messagesObj);
        List<?> messages = (List<?>) messagesObj;
        assertEquals(1, messages.size());
        assertInstanceOf(UserMessage.class, messages.get(0));
        assertEquals(input, ((UserMessage) messages.get(0)).getText());
        assertTrue(((UserMessage) messages.get(0)).getText() != null);
    }

    private void setDefaultAgent(String value) {
        try {
            var field = ChannelMessageDispatcher.class.getDeclaredField("defaultAgent");
            field.setAccessible(true);
            field.set(dispatcher, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
