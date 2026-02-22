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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern CLARIFICATION_TRUE_PATTERN = Pattern.compile(
            "(?i)(clarification_needed|clarificationneeded|need_clarification)\\s*[:=]\\s*(true|1|yes)");
    private static final Pattern REASON_PATTERN = Pattern.compile("(?im)^\\s*reason\\s*[:：]\\s*(.+)$");
    private static final Pattern QUESTIONS_HEADER_PATTERN = Pattern.compile("(?im)^\\s*questions\\s*[:：]\\s*$");
    private static final Pattern BULLET_QUESTION_PATTERN = Pattern.compile("^(?:[-*•]|\\d+[.)、])\\s*(.+)$");

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
                if (feedbackData != null && !feedbackData.isEmpty()) {
                    logger.debug("Skip clarification feedback processing, mode={}, keys={}",
                            feedbackData.get(KEY_MODE), feedbackData.keySet());
                }
                return Map.of();
            }
            logger.debug("Process clarification feedback, keys={}", feedbackData.keySet());
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
            logger.warn("Ignore clarification feedback because answers are empty.");
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
        logger.info("Clarification feedback accepted, answers={}, nextJump={}", answers.size(), JumpTo.MODEL);
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
        Optional<Decision> fromText = extractDecisionFromPayload(text);
        if (fromText.isPresent()) {
            return fromText;
        }

        if (assistantMessage.hasToolCalls()) {
            for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                Optional<Decision> fromToolArgs = extractDecisionFromPayload(toolCall.arguments());
                if (fromToolArgs.isPresent()) {
                    return fromToolArgs;
                }
            }
        }

        return Optional.empty();
    }

    private Optional<Decision> extractDecisionFromPayload(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return Optional.empty();
        }

        Optional<JsonNode> nodeOptional = parseJsonNode(rawPayload);
        if (nodeOptional.isPresent()) {
            Optional<Decision> fromJson = extractDecisionFromJson(nodeOptional.get());
            if (fromJson.isPresent()) {
                return fromJson;
            }
        }
        return extractDecisionFromStructuredText(rawPayload);
    }

    private Optional<Decision> extractDecisionFromJson(JsonNode node) {
        boolean clarificationNeeded = readBoolean(node, KEY_CLARIFICATION_NEEDED)
                || readBoolean(node, KEY_CLARIFICATION_NEEDED_CAMEL)
                || readBoolean(node, KEY_NEED_CLARIFICATION);
        if (!clarificationNeeded) {
            return Optional.empty();
        }

        List<String> questions = extractQuestions(node.path(KEY_QUESTIONS));
        if (questions.isEmpty()) {
            questions = extractQuestions(node.path("question"));
        }
        if (questions.isEmpty()) {
            return Optional.empty();
        }

        String reason = node.path(KEY_REASON).asText("");
        return Optional.of(new Decision(questions, reason, buildQuestionSignature(questions)));
    }

    private Optional<Decision> extractDecisionFromStructuredText(String rawPayload) {
        String normalized = normalizeText(rawPayload);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        List<String> questions = extractQuestionsFromText(normalized);
        if (questions.isEmpty()) {
            return Optional.empty();
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        boolean clarificationNeeded = CLARIFICATION_TRUE_PATTERN.matcher(lower).find()
                || lower.contains("需要澄清")
                || lower.contains("需要补充")
                || lower.contains("信息不足");
        if (!clarificationNeeded) {
            return Optional.empty();
        }

        String reason = "";
        Matcher reasonMatcher = REASON_PATTERN.matcher(normalized);
        if (reasonMatcher.find()) {
            reason = reasonMatcher.group(1).trim();
        }

        return Optional.of(new Decision(questions, reason, buildQuestionSignature(questions)));
    }

    private boolean readBoolean(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        String text = value.asText("").trim().toLowerCase(Locale.ROOT);
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
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
        String candidate = normalizeText(rawText).trim();
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

    private String normalizeText(String rawText) {
        if (rawText == null) {
            return "";
        }
        return rawText
                .replace('\u201c', '"')
                .replace('\u201d', '"')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\uff1a', ':')
                .replace('\uff0c', ',')
                .replace('\u3001', ',')
                .replace('\uff3b', '[')
                .replace('\uff3d', ']')
                .replace('\u3010', '[')
                .replace('\u3011', ']')
                .replace('\uff5b', '{')
                .replace('\uff5d', '}')
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private List<String> extractQuestions(JsonNode questionsNode) {
        List<String> questions = new ArrayList<>();
        if (questionsNode.isArray()) {
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
                addQuestion(questions, question);
            }
            return questions;
        }

        if (questionsNode.isTextual()) {
            for (String line : normalizeText(questionsNode.asText("")).split("\n")) {
                addQuestion(questions, line);
                if (questions.size() >= maxQuestions) {
                    break;
                }
            }
            return questions;
        }

        if (questionsNode.isObject()) {
            addQuestion(questions, questionsNode.path("question").asText(""));
        }

        return questions;
    }

    private List<String> extractQuestionsFromText(String text) {
        List<String> questions = new ArrayList<>();
        boolean inQuestionsSection = false;
        for (String rawLine : text.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                if (inQuestionsSection) {
                    break;
                }
                continue;
            }

            if (!inQuestionsSection && QUESTIONS_HEADER_PATTERN.matcher(line).matches()) {
                inQuestionsSection = true;
                continue;
            }

            if (inQuestionsSection) {
                Matcher bulletMatcher = BULLET_QUESTION_PATTERN.matcher(line);
                if (bulletMatcher.find()) {
                    addQuestion(questions, bulletMatcher.group(1));
                } else {
                    addQuestion(questions, line);
                }
            } else if (line.endsWith("?") || line.endsWith("？")) {
                addQuestion(questions, line);
            }

            if (questions.size() >= maxQuestions) {
                break;
            }
        }
        return questions;
    }

    private void addQuestion(List<String> questions, String candidate) {
        if (questions.size() >= maxQuestions || candidate == null) {
            return;
        }
        String normalized = candidate
                .replaceFirst("^[\\-•*\\d.)、\\s]+", "")
                .trim();
        if (!normalized.isEmpty()) {
            questions.add(normalized);
        }
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
