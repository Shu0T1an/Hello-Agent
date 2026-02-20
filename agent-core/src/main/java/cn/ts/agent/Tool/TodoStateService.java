package cn.ts.agent.Tool;

import cn.ts.agent.constant.StateKeys;
import cn.ts.graph.state.State;
import org.springframework.ai.chat.model.ToolContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.ts.agent.Tool.ToolContextConstants.TOOL_CALL_ID_CONTEXT_KEY;
import static cn.ts.agent.Tool.ToolContextConstants.TOOL_EXTRA_STATE_KEY;
import static cn.ts.agent.Tool.ToolContextConstants.TOOL_STATE_CONTEXT_KEY;
import static cn.ts.agent.Tool.ToolContextConstants.TOOL_TRANSIENT_CONTEXT_KEY;

/**
 * Todo 状态服务，负责解析/校验/读写 todos 与元信息。
 */
public class TodoStateService {

    public static final String ERROR_INVALID_REQUEST = "TODO_INVALID_REQUEST";
    public static final String ERROR_INVALID_TRANSITION = "TODO_INVALID_TRANSITION";
    public static final String ERROR_STATE_CONFLICT = "TODO_STATE_CONFLICT";
    public static final String ERROR_NOT_FOUND = "TODO_NOT_FOUND";

    public TodoListSnapshot resolveCurrentSnapshot(ToolContext toolContext) {
        Map<String, Object> mergedView = resolveMergedView(toolContext);
        return parseSnapshot(mergedView);
    }

    public MutationResult upsertTodos(ToolContext toolContext,
                                      List<TodoItem> upserts,
                                      Long expectedVersion,
                                      boolean strictVersionCheck,
                                      String operation) {
        requireTodos(upserts);

        TodoListSnapshot before = resolveCurrentSnapshot(toolContext);
        enforceVersion(before.meta().version(), expectedVersion, strictVersionCheck);
        if (isReplayByToolCallId(toolContext, before.meta())) {
            return MutationResult.idempotent(before);
        }

        Map<String, TodoItem> existingById = new LinkedHashMap<>(indexById(before.items()));
        int changed = 0;
        Instant now = Instant.now();

        for (TodoItem input : upserts) {
            TodoItem normalized = normalizeInput(input, now);
            TodoItem existing = existingById.get(normalized.id());
            if (existing != null) {
                ensureTransitionAllowed(existing.status(), normalized.status(), normalized.id());
                normalized = normalized
                        .withCreatedAt(existing.createdAt())
                        .withUpdatedAt(now.toString());
                if (!existing.equals(normalized)) {
                    changed++;
                }
            } else {
                changed++;
            }
            existingById.put(normalized.id(), normalized);
        }

        List<TodoItem> next = new ArrayList<>(existingById.values());
        TodoListSnapshot after = writeSnapshot(toolContext, before, next, operation, changed);
        return new MutationResult(before, after, changed, false);
    }

    public MutationResult completeTodo(ToolContext toolContext,
                                       String todoId,
                                       Long expectedVersion,
                                       boolean strictVersionCheck) {
        String id = requireText(todoId, "todoId");

        TodoListSnapshot before = resolveCurrentSnapshot(toolContext);
        enforceVersion(before.meta().version(), expectedVersion, strictVersionCheck);
        if (isReplayByToolCallId(toolContext, before.meta())) {
            return MutationResult.idempotent(before);
        }

        List<TodoItem> next = new ArrayList<>(before.items().size());
        boolean found = false;
        int changed = 0;
        Instant now = Instant.now();
        for (TodoItem item : before.items()) {
            if (!item.id().equals(id)) {
                next.add(item);
                continue;
            }
            found = true;
            ensureTransitionAllowed(item.status(), TodoStatus.COMPLETED, id);
            TodoItem completed = item
                    .withStatus(TodoStatus.COMPLETED)
                    .withUpdatedAt(now.toString());
            next.add(completed);
            if (!item.equals(completed)) {
                changed++;
            }
        }

        if (!found) {
            throw new TodoToolException(ERROR_NOT_FOUND, "Todo not found: " + id);
        }

        TodoListSnapshot after = writeSnapshot(toolContext, before, next, "complete_todo", changed);
        return new MutationResult(before, after, changed, false);
    }

    public MutationResult deleteTodo(ToolContext toolContext,
                                     String todoId,
                                     Long expectedVersion,
                                     boolean strictVersionCheck) {
        String id = requireText(todoId, "todoId");

        TodoListSnapshot before = resolveCurrentSnapshot(toolContext);
        enforceVersion(before.meta().version(), expectedVersion, strictVersionCheck);
        if (isReplayByToolCallId(toolContext, before.meta())) {
            return MutationResult.idempotent(before);
        }

        List<TodoItem> next = before.items().stream()
                .filter(item -> !item.id().equals(id))
                .toList();

        if (next.size() == before.items().size()) {
            throw new TodoToolException(ERROR_NOT_FOUND, "Todo not found: " + id);
        }

        TodoListSnapshot after = writeSnapshot(toolContext, before, next, "delete_todo", 1);
        return new MutationResult(before, after, 1, false);
    }

    public MutationResult clearTodos(ToolContext toolContext,
                                     Long expectedVersion,
                                     boolean strictVersionCheck) {
        TodoListSnapshot before = resolveCurrentSnapshot(toolContext);
        enforceVersion(before.meta().version(), expectedVersion, strictVersionCheck);
        if (isReplayByToolCallId(toolContext, before.meta())) {
            return MutationResult.idempotent(before);
        }

        int changed = before.items().size();
        TodoListSnapshot after = writeSnapshot(toolContext, before, List.of(), "clear_todos", changed);
        return new MutationResult(before, after, changed, false);
    }

    public List<TodoItem> filterAndSort(List<TodoItem> items, TodoStatus status, String query) {
        String normalizedQuery = query == null ? null : query.trim().toLowerCase(Locale.ROOT);
        return items.stream()
                .filter(item -> status == null || item.status() == status)
                .filter(item -> normalizedQuery == null || normalizedQuery.isBlank()
                        || item.content().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .sorted(Comparator
                        .comparingInt((TodoItem item) -> item.status().sortOrder())
                        .thenComparing(TodoItem::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private TodoListSnapshot writeSnapshot(ToolContext toolContext,
                                           TodoListSnapshot before,
                                           List<TodoItem> nextItems,
                                           String operation,
                                           int changedCount) {
        Map<String, Object> extraState = getExtraState(toolContext);
        long nextVersion = before.meta().version() + 1;
        String now = Instant.now().toString();
        String toolCallId = getToolCallId(toolContext);

        List<Map<String, Object>> persistedItems = nextItems.stream()
                .map(this::toPersistedMap)
                .toList();

        Map<String, Object> meta = new HashMap<>();
        meta.put("version", nextVersion);
        meta.put("updatedAt", now);
        meta.put("updatedByToolCallId", toolCallId);
        meta.put("lastOperation", operation);

        extraState.put(StateKeys.TODOS, persistedItems);
        extraState.put(StateKeys.TODOS_META, meta);

        putTransientMetric(toolContext, "todoVersionBefore", before.meta().version());
        putTransientMetric(toolContext, "todoVersionAfter", nextVersion);
        putTransientMetric(toolContext, "changedCount", changedCount);

        return new TodoListSnapshot(nextItems, TodoMeta.of(nextVersion, now, toolCallId, operation));
    }

    private Map<String, Object> toPersistedMap(TodoItem item) {
        Map<String, Object> out = new HashMap<>();
        out.put("id", item.id());
        out.put("content", item.content());
        out.put("status", item.status().value());
        if (item.priority() != null) {
            out.put("priority", item.priority().value());
        }
        out.put("createdAt", item.createdAt());
        out.put("updatedAt", item.updatedAt());
        return out;
    }

    private TodoListSnapshot parseSnapshot(Map<String, Object> view) {
        Object rawTodos = view.get(StateKeys.TODOS);
        Object rawMeta = view.get(StateKeys.TODOS_META);
        TodoMeta meta = parseMeta(rawMeta);
        List<TodoItem> items = parseItems(rawTodos);
        return new TodoListSnapshot(items, meta);
    }

    private TodoMeta parseMeta(Object rawMeta) {
        if (!(rawMeta instanceof Map<?, ?> map)) {
            return TodoMeta.empty();
        }
        long version = asLong(map.get("version"), 0L);
        String updatedAt = asString(map.get("updatedAt"));
        String updatedByToolCallId = asString(map.get("updatedByToolCallId"));
        String lastOperation = asString(map.get("lastOperation"));
        return TodoMeta.of(version, updatedAt, updatedByToolCallId, lastOperation);
    }

    private List<TodoItem> parseItems(Object rawTodos) {
        if (!(rawTodos instanceof List<?> list)) {
            return List.of();
        }
        List<TodoItem> items = new ArrayList<>(list.size());
        int idx = 0;
        for (Object raw : list) {
            items.add(parseSingle(raw, idx++));
        }
        return items;
    }

    private TodoItem parseSingle(Object raw, int index) {
        if (raw instanceof TodoItem todoItem) {
            return normalizeInput(todoItem, Instant.now());
        }
        if (raw instanceof Map<?, ?> map) {
            String content = asString(map.get("content"));
            String id = asString(map.get("id"));
            TodoStatus status = TodoStatus.fromValue(asString(map.get("status")));
            TodoPriority priority = TodoPriority.fromValue(asString(map.get("priority")));
            String createdAt = asString(map.get("createdAt"));
            String updatedAt = asString(map.get("updatedAt"));
            return normalizeInput(new TodoItem(id, content, status, priority, createdAt, updatedAt), Instant.now(), index);
        }
        throw new TodoToolException(ERROR_INVALID_REQUEST, "Invalid todo data type: " + raw.getClass().getName());
    }

    private TodoItem normalizeInput(TodoItem source, Instant now) {
        return normalizeInput(source, now, -1);
    }

    private TodoItem normalizeInput(TodoItem source, Instant now, int fallbackIndex) {
        if (source == null) {
            throw new TodoToolException(ERROR_INVALID_REQUEST, "Todo item cannot be null");
        }
        String content = requireText(source.content(), "todo.content");
        TodoStatus status = source.status();
        if (status == null) {
            throw new TodoToolException(ERROR_INVALID_REQUEST, "todo.status is required");
        }
        String nowValue = now.toString();
        String id = source.id();
        if (id == null || id.isBlank()) {
            id = fallbackIndex >= 0
                    ? legacyId(content, status, fallbackIndex)
                    : UUID.randomUUID().toString();
        }
        String createdAt = source.createdAt() == null || source.createdAt().isBlank()
                ? nowValue
                : source.createdAt();
        String updatedAt = source.updatedAt() == null || source.updatedAt().isBlank()
                ? nowValue
                : source.updatedAt();
        return new TodoItem(id, content, status, source.priority(), createdAt, updatedAt);
    }

    private String legacyId(String content, TodoStatus status, int index) {
        return "legacy-" + index + "-" + Math.abs(Objects.hash(content, status.value(), index));
    }

    private Map<String, Object> resolveMergedView(ToolContext toolContext) {
        Map<String, Object> context = requireContext(toolContext);
        Map<String, Object> merged = new HashMap<>(extractStateData(context.get(TOOL_STATE_CONTEXT_KEY)));
        Object extra = context.get(TOOL_EXTRA_STATE_KEY);
        if (extra instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    merged.put(key, entry.getValue());
                }
            }
        }
        return merged;
    }

    private Map<String, Object> extractStateData(Object stateObj) {
        if (stateObj == null) {
            return Map.of();
        }
        if (stateObj instanceof State state) {
            return state.data();
        }
        if (stateObj instanceof Map<?, ?> map) {
            Map<String, Object> data = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    data.put(key, entry.getValue());
                }
            }
            return data;
        }
        throw new TodoToolException(ERROR_INVALID_REQUEST, "Unsupported state object: " + stateObj.getClass().getName());
    }

    private Map<String, Object> getExtraState(ToolContext toolContext) {
        Map<String, Object> context = requireContext(toolContext);
        Object extraObj = context.get(TOOL_EXTRA_STATE_KEY);
        if (!(extraObj instanceof Map<?, ?> map)) {
            throw new TodoToolException(ERROR_INVALID_REQUEST, "Tool extra state is missing");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> casted = (Map<String, Object>) map;
        return casted;
    }

    private String getToolCallId(ToolContext toolContext) {
        Map<String, Object> context = requireContext(toolContext);
        return asString(context.get(TOOL_CALL_ID_CONTEXT_KEY));
    }

    private boolean isReplayByToolCallId(ToolContext toolContext, TodoMeta meta) {
        String toolCallId = getToolCallId(toolContext);
        return toolCallId != null && !toolCallId.isBlank() && toolCallId.equals(meta.updatedByToolCallId());
    }

    private Map<String, TodoItem> indexById(List<TodoItem> todos) {
        Map<String, TodoItem> index = new LinkedHashMap<>();
        for (TodoItem item : todos) {
            index.put(item.id(), item);
        }
        return index;
    }

    private void enforceVersion(long currentVersion, Long expectedVersion, boolean strictVersionCheck) {
        if (!strictVersionCheck || expectedVersion == null) {
            return;
        }
        if (expectedVersion != currentVersion) {
            throw new TodoToolException(ERROR_STATE_CONFLICT,
                    "Todo version conflict, expected=" + expectedVersion + ", actual=" + currentVersion);
        }
    }

    private void ensureTransitionAllowed(TodoStatus from, TodoStatus to, String todoId) {
        if (from == to) {
            return;
        }
        if (to == TodoStatus.BLOCKED) {
            return;
        }
        if (from == TodoStatus.BLOCKED && to == TodoStatus.IN_PROGRESS) {
            return;
        }
        if (from == TodoStatus.PENDING && to == TodoStatus.IN_PROGRESS) {
            return;
        }
        if (from == TodoStatus.IN_PROGRESS && to == TodoStatus.COMPLETED) {
            return;
        }
        throw new TodoToolException(ERROR_INVALID_TRANSITION,
                "Invalid status transition for todo " + todoId + ": " + from.value() + " -> " + to.value());
    }

    private void requireTodos(List<TodoItem> todos) {
        if (todos == null || todos.isEmpty()) {
            throw new TodoToolException(ERROR_INVALID_REQUEST, "todos must not be null or empty");
        }
    }

    private String requireText(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new TodoToolException(ERROR_INVALID_REQUEST, field + " must not be blank");
        }
        return text.trim();
    }

    private Map<String, Object> requireContext(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            throw new TodoToolException(ERROR_INVALID_REQUEST, "Tool context is not available");
        }
        return toolContext.getContext();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long asLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void putTransientMetric(ToolContext toolContext, String key, Object value) {
        Map<String, Object> context = requireContext(toolContext);
        Object transientObj = context.get(TOOL_TRANSIENT_CONTEXT_KEY);
        if (!(transientObj instanceof Map<?, ?> map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> casted = (Map<String, Object>) map;
        casted.put(key, value);
    }

    public record TodoListSnapshot(List<TodoItem> items, TodoMeta meta) {
    }

    public record TodoMeta(long version, String updatedAt, String updatedByToolCallId, String lastOperation) {
        public static TodoMeta empty() {
            return new TodoMeta(0L, null, null, null);
        }

        public static TodoMeta of(long version, String updatedAt, String updatedByToolCallId, String lastOperation) {
            return new TodoMeta(version, updatedAt, updatedByToolCallId, lastOperation);
        }
    }

    public record MutationResult(TodoListSnapshot before,
                                 TodoListSnapshot after,
                                 int changedCount,
                                 boolean idempotentReplay) {
        public static MutationResult idempotent(TodoListSnapshot snapshot) {
            return new MutationResult(snapshot, snapshot, 0, true);
        }
    }

    public record TodoItem(String id,
                           String content,
                           TodoStatus status,
                           TodoPriority priority,
                           String createdAt,
                           String updatedAt) {

        public TodoItem withStatus(TodoStatus status) {
            return new TodoItem(id, content, status, priority, createdAt, updatedAt);
        }

        public TodoItem withCreatedAt(String createdAt) {
            return new TodoItem(id, content, status, priority, createdAt, updatedAt);
        }

        public TodoItem withUpdatedAt(String updatedAt) {
            return new TodoItem(id, content, status, priority, createdAt, updatedAt);
        }
    }

    public enum TodoStatus {
        PENDING("pending", 1),
        IN_PROGRESS("in_progress", 0),
        BLOCKED("blocked", 2),
        COMPLETED("completed", 3);

        private final String value;
        private final int sortOrder;

        TodoStatus(String value, int sortOrder) {
            this.value = value;
            this.sortOrder = sortOrder;
        }

        public String value() {
            return value;
        }

        public int sortOrder() {
            return sortOrder;
        }

        public static TodoStatus fromValue(String value) {
            if (value == null || value.isBlank()) {
                throw new TodoToolException(ERROR_INVALID_REQUEST, "todo.status is required");
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (TodoStatus status : values()) {
                if (status.value.equals(normalized) || status.name().equalsIgnoreCase(normalized)) {
                    return status;
                }
            }
            throw new TodoToolException(ERROR_INVALID_REQUEST,
                    "Unknown todo status: " + value + ", valid: pending,in_progress,blocked,completed");
        }
    }

    public enum TodoPriority {
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high");

        private final String value;

        TodoPriority(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static TodoPriority fromValue(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (TodoPriority priority : values()) {
                if (priority.value.equals(normalized) || priority.name().equalsIgnoreCase(normalized)) {
                    return priority;
                }
            }
            throw new TodoToolException(ERROR_INVALID_REQUEST,
                    "Unknown todo priority: " + value + ", valid: low,medium,high");
        }
    }
}
