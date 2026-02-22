package cn.ts.agent.hook;

import cn.ts.agent.constant.StateKeys;
import cn.ts.graph.checkpoint.InterruptionMetadata;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.hook.HookPosition;
import cn.ts.graph.hook.HookPositions;
import cn.ts.graph.hook.JumpTo;
import cn.ts.graph.hook.ModelHook;
import cn.ts.graph.node.AsyncNodeActionWithConfig;
import cn.ts.graph.node.InterruptableAction;
import cn.ts.graph.state.State;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Interrupts execution when model output indicates user requirements are ambiguous,
 * and resumes after user clarification answers are provided.
 */
@HookPositions(HookPosition.AFTER_MODEL)
public class ClarificationQaHook extends ModelHook implements InterruptableAction, AsyncNodeActionWithConfig {

    private static final Logger logger = LoggerFactory.getLogger(ClarificationQaHook.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MODE_CLARIFICATION_QA = "clarification_qa";
    private static final String KEY_MODE = "mode";
    private static final String KEY_QUESTIONS = "questions";
    private static final String KEY_REASON = "reason";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_CLARIFICATION_NEEDED = "clarification_needed";
    private static final String KEY_CLARIFICATION_NEEDED_CAMEL = "clarificationNeeded";
    private static final String KEY_NEED_CLARIFICATION = "need_clarification";
    private static final String KEY_CLARIFICATION_ANSWERS = "clarificationAnswers";

    private final int maxQuestions;
    private final int maxRounds;
    private final String interruptionMessage;

    private ClarificationQaHook(Builder builder) {
        this.maxQuestions = builder.maxQuestions;
        this.maxRounds = builder.maxRounds;
        this.interruptionMessage = builder.interruptionMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getName() {
        return "ClarificationQaHook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> applyAsync(State state, RunnableConfig config) {
        return afterModel(state, config);
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(State state, RunnableConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> feedbackData = config.feedbackData();
            if (!isClarificationFeedback(feedbackData)) {
                return Map.of();
            }
            return processFeedback(state, feedbackData);
        });
    }

    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, State state, RunnableConfig config) {
        if (config.feedbackData() != null && !config.feedbackData().isEmpty()) {
            return Optional.empty();
        }

        int currentRound = state.<Integer>value(StateKeys.CLARIFICATION_ROUND).orElse(0);
        if (currentRound >= maxRounds) {
            logger.debug("Skip clarification interruption because max rounds reached: {}", currentRound);
            return Optional.empty();
        }

        Optional<Decision> decisionOptional = extractDecision(state);
        if (decisionOptional.isEmpty()) {
            return Optional.empty();
        }

        Decision decision = decisionOptional.get();
        Optional<String> lastSignature = state.value(StateKeys.CLARIFICATION_LAST_SIGNATURE);
        if (lastSignature.isPresent() && lastSignature.get().equals(decision.signature())) {
            logger.debug("Skip repeated clarification interruption with the same signature.");
            return Optional.empty();
        }

        List<Map<String, Object>> questionsPayload = new ArrayList<>();
        int index = 1;
        for (String question : decision.questions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "q" + index++);
            item.put("question", question);
            item.put("required", true);
            questionsPayload.add(item);
        }

        Map<String, Object> customData = new LinkedHashMap<>();
        customData.put(KEY_MODE, MODE_CLARIFICATION_QA);
        customData.put(KEY_MESSAGE, interruptionMessage);
        customData.put(KEY_QUESTIONS, questionsPayload);
        if (!decision.reason().isBlank()) {
            customData.put(KEY_REASON, decision.reason());
        }

        InterruptionMetadata metadata = InterruptionMetadata.builder(nodeId, state)
                .message(interruptionMessage)
                .customData(customData)
                .build();
        return Optional.of(metadata);
    }

    private Map<String, Object> processFeedback(State state, Map<String, Object> feedbackData) {
        List<Answer> answers = extractAnswers(feedbackData.get(KEY_CLARIFICATION_ANSWERS));
        if (answers.isEmpty()) {
            return Map.of();
        }

        List<Message> existing = state.<List<Message>>value(StateKeys.MESSAGES).orElseGet(ArrayList::new);
        List<Message> newMessages = new ArrayList<>(existing);
        String clarificationInput = formatClarificationInput(answers);
        newMessages.add(new UserMessage(clarificationInput));

        int currentRound = state.<Integer>value(StateKeys.CLARIFICATION_ROUND).orElse(0);
        Map<String, Object> updates = new HashMap<>();
        updates.put(StateKeys.MESSAGES, newMessages);
        updates.put(StateKeys.INPUT, clarificationInput);
        updates.put(StateKeys.CLARIFICATION_ROUND, currentRound + 1);
        updates.put(StateKeys.CLARIFICATION_LAST_SIGNATURE, buildAnswerSignature(answers));
        updates.put(StateKeys.JUMP_TO, JumpTo.MODEL);
        return updates;
    }

    private Optional<Decision> extractDecision(State state) {
        List<Message> messages = state.<List<Message>>value(StateKeys.MESSAGES).orElseGet(ArrayList::new);
        if (messages.isEmpty()) {
            return Optional.empty();
        }

        Message last = messages.get(messages.size() - 1);
        if (!(last instanceof AssistantMessage assistantMessage)) {
            return Optional.empty();
        }
        String text = assistantMessage.getText();
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        Optional<JsonNode> nodeOptional = parseJsonNode(text);
        if (nodeOptional.isEmpty()) {
            return Optional.empty();
        }

        JsonNode node = nodeOptional.get();
        boolean clarificationNeeded = node.path(KEY_CLARIFICATION_NEEDED).asBoolean(false)
                || node.path(KEY_CLARIFICATION_NEEDED_CAMEL).asBoolean(false)
                || node.path(KEY_NEED_CLARIFICATION).asBoolean(false);
        if (!clarificationNeeded) {
            return Optional.empty();
        }

        List<String> questions = extractQuestions(node.path(KEY_QUESTIONS));
        if (questions.isEmpty()) {
            return Optional.empty();
        }

        String reason = node.path(KEY_REASON).asText("");
        return Optional.of(new Decision(questions, reason, buildQuestionSignature(questions)));
    }

    private Optional<JsonNode> parseJsonNode(String rawText) {
        String normalized = normalizeJsonPayload(rawText);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OBJECT_MAPPER.readTree(normalized));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String normalizeJsonPayload(String rawText) {
        String candidate = rawText.trim();
        if (candidate.startsWith("```")) {
            int firstBrace = candidate.indexOf('{');
            int lastBrace = candidate.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                return candidate.substring(firstBrace, lastBrace + 1).trim();
            }
        }
        int firstBrace = candidate.indexOf('{');
        int lastBrace = candidate.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return candidate.substring(firstBrace, lastBrace + 1).trim();
        }
        return candidate;
    }

    private List<String> extractQuestions(JsonNode questionsNode) {
        if (!questionsNode.isArray()) {
            return List.of();
        }
        List<String> questions = new ArrayList<>();
        for (JsonNode item : questionsNode) {
            if (questions.size() >= maxQuestions) {
                break;
            }
            String question;
            if (item.isTextual()) {
                question = item.asText("");
            } else {
                question = item.path("question").asText("");
            }
            String trimmed = question.trim();
            if (!trimmed.isEmpty()) {
                questions.add(trimmed);
            }
        }
        return questions;
    }

    private boolean isClarificationFeedback(Map<String, Object> feedbackData) {
        if (feedbackData == null || feedbackData.isEmpty()) {
            return false;
        }
        Object mode = feedbackData.get(KEY_MODE);
        return MODE_CLARIFICATION_QA.equals(mode);
    }

    private List<Answer> extractAnswers(Object answersObject) {
        if (!(answersObject instanceof List<?> rawList)) {
            return List.of();
        }
        List<Answer> answers = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object idObject = map.get("id");
            Object answerObject = map.get("answer");
            String id = idObject == null ? "" : idObject.toString().trim();
            String answer = answerObject == null ? "" : answerObject.toString().trim();
            if (id.isEmpty() || answer.isEmpty()) {
                continue;
            }
            answers.add(new Answer(id, answer));
        }
        return answers;
    }

    private String formatClarificationInput(List<Answer> answers) {
        StringBuilder builder = new StringBuilder();
        builder.append("Clarification details:\n");
        for (Answer answer : answers) {
            builder.append("- ").append(answer.id()).append(": ").append(answer.answer()).append('\n');
        }
        builder.append("Please continue with the original request using the details above.");
        return builder.toString();
    }

    private String buildQuestionSignature(List<String> questions) {
        return questions.stream()
                .map(question -> question.toLowerCase(Locale.ROOT).trim())
                .filter(value -> !value.isEmpty())
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    private String buildAnswerSignature(List<Answer> answers) {
        return answers.stream()
                .map(answer -> (answer.id() + ":" + answer.answer()).toLowerCase(Locale.ROOT))
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    private record Decision(List<String> questions, String reason, String signature) {
        private Decision {
            questions = List.copyOf(questions);
            reason = Objects.requireNonNullElse(reason, "");
            signature = Objects.requireNonNullElse(signature, "");
        }
    }

    private record Answer(String id, String answer) {
    }

    public static class Builder {
        private int maxQuestions = 3;
        private int maxRounds = 2;
        private String interruptionMessage = "I need more details before I can continue. Please answer the questions below.";

        public Builder maxQuestions(int maxQuestions) {
            if (maxQuestions <= 0) {
                throw new IllegalArgumentException("maxQuestions must be greater than 0");
            }
            this.maxQuestions = maxQuestions;
            return this;
        }

        public Builder maxRounds(int maxRounds) {
            if (maxRounds <= 0) {
                throw new IllegalArgumentException("maxRounds must be greater than 0");
            }
            this.maxRounds = maxRounds;
            return this;
        }

        public Builder interruptionMessage(String interruptionMessage) {
            if (interruptionMessage == null || interruptionMessage.isBlank()) {
                throw new IllegalArgumentException("interruptionMessage must not be blank");
            }
            this.interruptionMessage = interruptionMessage;
            return this;
        }

        public ClarificationQaHook build() {
            return new ClarificationQaHook(this);
        }
    }
}
