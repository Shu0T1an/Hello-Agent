package cn.ts.agent.hook;

import cn.ts.agent.constant.StateKeys;
import cn.ts.graph.checkpoint.InterruptionMetadata;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.hook.JumpTo;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClarificationQaHookTest {

    @Test
    void interruptShouldReturnMetadataWhenClarificationJsonDetected() {
        ClarificationQaHook hook = ClarificationQaHook.builder()
                .maxQuestions(3)
                .maxRounds(2)
                .build();

        String json = """
                {
                  "clarification_needed": true,
                  "reason": "missing important constraints",
                  "questions": ["What is your budget?", "Which platform should we target?", "What is the timeline?"]
                }
                """;
        State state = stateWithMessages(List.of(
                new UserMessage("Build me a system"),
                new AssistantMessage(json)
        ));

        Optional<InterruptionMetadata> interruption = hook.interrupt(
                "_AGENT_MODEL_",
                state,
                RunnableConfig.defaultConfig()
        );

        assertTrue(interruption.isPresent());
        Map<String, Object> customData = interruption.get().getCustomData();
        assertEquals("clarification_qa", customData.get("mode"));
        Object questionsObj = customData.get("questions");
        assertInstanceOf(List.class, questionsObj);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) questionsObj;
        assertEquals(3, questions.size());
        assertEquals("q1", questions.get(0).get("id"));
    }

    @Test
    void interruptShouldSkipWhenMaxRoundsReached() {
        ClarificationQaHook hook = ClarificationQaHook.builder()
                .maxRounds(2)
                .build();

        String json = """
                {
                  "clarification_needed": true,
                  "questions": ["Need more details"]
                }
                """;
        State state = stateWithData(Map.of(
                StateKeys.MESSAGES, List.of(new AssistantMessage(json)),
                StateKeys.CLARIFICATION_ROUND, 2
        ));

        Optional<InterruptionMetadata> interruption = hook.interrupt(
                "_AGENT_MODEL_",
                state,
                RunnableConfig.defaultConfig()
        );

        assertFalse(interruption.isPresent());
    }

    @Test
    void afterModelShouldAppendClarificationAnswerAndJumpToModel() throws Exception {
        ClarificationQaHook hook = ClarificationQaHook.builder().build();

        State state = stateWithMessages(List.of(
                new UserMessage("Build me a system"),
                new AssistantMessage("{\"clarification_needed\":true,\"questions\":[\"What budget?\"]}")
        ));

        RunnableConfig config = RunnableConfig.builder()
                .feedbackData(Map.of(
                        "mode", "clarification_qa",
                        "clarificationAnswers", List.of(
                                Map.of("id", "q1", "answer", "Budget is 100k")
                        )
                ))
                .build();

        Map<String, Object> updates = hook.afterModel(state, config).get();
        assertEquals(JumpTo.MODEL, updates.get(StateKeys.JUMP_TO));
        assertEquals(1, updates.get(StateKeys.CLARIFICATION_ROUND));
        assertTrue(updates.containsKey(StateKeys.MESSAGES));
        assertTrue(updates.containsKey(StateKeys.CLARIFICATION_LAST_FEEDBACK_SIGNATURE));

        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) updates.get(StateKeys.MESSAGES);
        assertEquals(1, messages.size());
        Message last = messages.get(messages.size() - 1);
        assertInstanceOf(UserMessage.class, last);
        assertTrue(((UserMessage) last).getText().contains("Budget is 100k"));
    }

    @Test
    void afterModelShouldProcessClarificationFeedbackOnlyOncePerSignature() throws Exception {
        ClarificationQaHook hook = ClarificationQaHook.builder().build();
        MapState state = new MapState();
        state.update(StateKeys.MESSAGES, new ArrayList<>(List.of(
                new UserMessage("Calc two numbers"),
                new AssistantMessage("{\"clarification_needed\":true,\"questions\":[\"q1?\"]}")
        )));

        RunnableConfig config = RunnableConfig.builder()
                .feedbackData(Map.of(
                        "mode", "clarification_qa",
                        "clarificationAnswers", List.of(
                                Map.of("id", "q1", "answer", "12"),
                                Map.of("id", "q2", "answer", "22")
                        )
                ))
                .build();

        Map<String, Object> first = hook.afterModel(state, config).get();
        assertFalse(first.isEmpty());
        state.merge(first);

        Map<String, Object> second = hook.afterModel(state, config).get();
        assertTrue(second.isEmpty());
    }

    @Test
    void afterModelShouldProcessAgainWhenClarificationFeedbackSignatureChanges() throws Exception {
        ClarificationQaHook hook = ClarificationQaHook.builder().build();
        MapState state = new MapState();
        state.update(StateKeys.MESSAGES, new ArrayList<>(List.of(
                new UserMessage("Calc two numbers"),
                new AssistantMessage("{\"clarification_needed\":true,\"questions\":[\"q1?\"]}")
        )));

        RunnableConfig firstConfig = RunnableConfig.builder()
                .feedbackData(Map.of(
                        "mode", "clarification_qa",
                        "clarificationAnswers", List.of(
                                Map.of("id", "q1", "answer", "12")
                        )
                ))
                .build();
        Map<String, Object> first = hook.afterModel(state, firstConfig).get();
        assertFalse(first.isEmpty());
        state.merge(first);

        RunnableConfig changedConfig = RunnableConfig.builder()
                .feedbackData(Map.of(
                        "mode", "clarification_qa",
                        "clarificationAnswers", List.of(
                                Map.of("id", "q1", "answer", "13")
                        )
                ))
                .build();
        Map<String, Object> second = hook.afterModel(state, changedConfig).get();
        assertFalse(second.isEmpty());
        assertEquals(JumpTo.MODEL, second.get(StateKeys.JUMP_TO));
    }

    @Test
    void interruptShouldHandleChineseQuotesAndPunctuation() {
        ClarificationQaHook hook = ClarificationQaHook.builder()
                .maxQuestions(3)
                .build();

        String nonStandardJson = """
                {
                  “clarification_needed”：true，
                  “reason”：“用户没有提供两个数字”，
                  “questions”：[“请告诉我第一个数字是多少？”, “请告诉我第二个数字是多少？”]
                }
                """;

        State state = stateWithMessages(List.of(
                new UserMessage("给我计算两个数的和"),
                new AssistantMessage(nonStandardJson)
        ));

        Optional<InterruptionMetadata> interruption = hook.interrupt(
                "_AGENT_MODEL_",
                state,
                RunnableConfig.defaultConfig()
        );

        assertTrue(interruption.isPresent());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) interruption.get().getCustomData().get("questions");
        assertEquals(2, questions.size());
    }

    @Test
    void interruptShouldFallbackToNonJsonStructuredText() {
        ClarificationQaHook hook = ClarificationQaHook.builder().build();

        String plainTextDecision = """
                clarification_needed: true
                reason: 缺少用于计算的具体数字
                questions:
                - 请告诉我第一个数字是多少？
                - 请告诉我第二个数字是多少？
                """;
        State state = stateWithMessages(List.of(
                new UserMessage("帮我把两个数字相加"),
                new AssistantMessage(plainTextDecision)
        ));

        Optional<InterruptionMetadata> interruption = hook.interrupt(
                "_AGENT_MODEL_",
                state,
                RunnableConfig.defaultConfig()
        );

        assertTrue(interruption.isPresent());
        assertEquals("clarification_qa", interruption.get().getCustomData().get("mode"));
    }

    @Test
    void interruptShouldReadClarificationFromToolCallArguments() {
        ClarificationQaHook hook = ClarificationQaHook.builder().build();

        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call-clarification",
                "function",
                "clarification_router",
                """
                        {
                          "clarification_needed": true,
                          "questions": ["请告诉我第一个数字是多少？", "请告诉我第二个数字是多少？"]
                        }
                        """
        );
        AssistantMessage assistant = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall))
                .build();

        State state = stateWithMessages(List.of(
                new UserMessage("给我计算两个数的和"),
                assistant
        ));

        Optional<InterruptionMetadata> interruption = hook.interrupt(
                "_AGENT_MODEL_",
                state,
                RunnableConfig.defaultConfig()
        );

        assertTrue(interruption.isPresent());
    }

    private State stateWithMessages(List<Message> messages) {
        MapState state = new MapState();
        state.update(StateKeys.MESSAGES, new ArrayList<>(messages));
        return state;
    }

    private State stateWithData(Map<String, Object> data) {
        return new MapState(data);
    }
}
