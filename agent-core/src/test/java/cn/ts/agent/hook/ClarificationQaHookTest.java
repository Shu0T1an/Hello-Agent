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

        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) updates.get(StateKeys.MESSAGES);
        Message last = messages.get(messages.size() - 1);
        assertInstanceOf(UserMessage.class, last);
        assertTrue(((UserMessage) last).getText().contains("Budget is 100k"));
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
