package cn.ts.agent.tool;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-reliability todo tools. Only new APIs are kept.
 */
public class WriteToDoTool {

    private static final Logger logger = LoggerFactory.getLogger(WriteToDoTool.class);

    private static final AtomicLong TODO_TOOL_CALLS_TOTAL = new AtomicLong();
    private static final AtomicLong TODO_TOOL_FAILURES_TOTAL = new AtomicLong();
    private static final AtomicLong TODO_STATE_CONFLICT_TOTAL = new AtomicLong();
    private static final AtomicLong TODO_INVALID_TRANSITION_TOTAL = new AtomicLong();

    private final TodoStateService todoStateService = new TodoStateService();

    @Tool(name = "upsert_todos",
            description = """
                    Incrementally create/update todos by id.
                    If id is absent, a new todo is created with a generated id.
                    Enforces todo status transition rules.
                    """)
    public String upsertTodos(
            @ToolParam(description = "Incremental todo upsert payload")
            UpsertTodosRequest request,
            ToolContext toolContext) {
        return executeWithMetrics("upsert_todos", () -> {
            if (request == null || request.todos() == null) {
                throw new TodoToolException(TodoStateService.ERROR_INVALID_REQUEST, "request.todos is required");
            }
            List<TodoStateService.TodoItem> todos = request.todos().stream()
                    .map(this::toDomainTodo)
                    .toList();

            TodoStateService.MutationResult result = todoStateService.upsertTodos(
                    toolContext,
                    todos,
                    request.expectedVersion(),
                    Boolean.TRUE.equals(request.strictVersionCheck()),
                    "upsert_todos");
            return formatMutationSummary("upsert_todos", result);
        });
    }

    @Tool(name = "complete_todo",
            description = "Mark a single todo as completed by id.")
    public String completeTodo(
            @ToolParam(description = "Completion request")
            CompleteTodoRequest request,
            ToolContext toolContext) {
        return executeWithMetrics("complete_todo", () -> {
            if (request == null) {
                throw new TodoToolException(TodoStateService.ERROR_INVALID_REQUEST, "request is required");
            }
            TodoStateService.MutationResult result = todoStateService.completeTodo(
                    toolContext,
                    request.id(),
                    request.expectedVersion(),
                    Boolean.TRUE.equals(request.strictVersionCheck()));
            return formatMutationSummary("complete_todo", result);
        });
    }

    @Tool(name = "delete_todo",
            description = "Delete a single todo by id. Usually requires approval.")
    public String deleteTodo(
            @ToolParam(description = "Delete request")
            DeleteTodoRequest request,
            ToolContext toolContext) {
        return executeWithMetrics("delete_todo", () -> {
            if (request == null) {
                throw new TodoToolException(TodoStateService.ERROR_INVALID_REQUEST, "request is required");
            }
            TodoStateService.MutationResult result = todoStateService.deleteTodo(
                    toolContext,
                    request.id(),
                    request.expectedVersion(),
                    Boolean.TRUE.equals(request.strictVersionCheck()));
            return formatMutationSummary("delete_todo", result);
        });
    }

    @Tool(name = "clear_todos",
            description = "Clear all todos for current session. Usually requires approval.")
    public String clearTodos(
            @ToolParam(description = "Clear request")
            ClearTodosRequest request,
            ToolContext toolContext) {
        return executeWithMetrics("clear_todos", () -> {
            Long expectedVersion = request == null ? null : request.expectedVersion();
            Boolean strictCheck = request == null ? null : request.strictVersionCheck();
            TodoStateService.MutationResult result = todoStateService.clearTodos(
                    toolContext,
                    expectedVersion,
                    Boolean.TRUE.equals(strictCheck));
            return formatMutationSummary("clear_todos", result);
        });
    }

    @Tool(name = "list_todos",
            description = """
                    List todos in structured text.
                    Supports filtering by status and query.
                    Sorted by status and updatedAt.
                    """)
    public String listTodos(
            @ToolParam(description = "Optional list filters")
            ListTodosRequest request,
            ToolContext toolContext) {
        return executeWithMetrics("list_todos", () -> {
            TodoStateService.TodoListSnapshot snapshot = todoStateService.resolveCurrentSnapshot(toolContext);
            TodoStateService.TodoStatus statusFilter = request == null ? null : toDomainStatus(request.status());
            String query = request == null ? null : request.query();

            List<TodoStateService.TodoItem> listed = todoStateService.filterAndSort(snapshot.items(), statusFilter, query);
            if (listed.isEmpty()) {
                return "No todos matched filters (version=" + snapshot.meta().version() + ")";
            }
            StringBuilder out = new StringBuilder();
            out.append("Todos (version=").append(snapshot.meta().version()).append("):\n");
            for (TodoStateService.TodoItem item : listed) {
                out.append(String.format("  - [%s] %s (id=%s)%n", item.status().value(), item.content(), item.id()));
            }
            return out.toString();
        });
    }

    private TodoStateService.TodoItem toDomainTodo(TodoItem item) {
        if (item == null) {
            throw new TodoToolException(TodoStateService.ERROR_INVALID_REQUEST, "todo item cannot be null");
        }
        return new TodoStateService.TodoItem(
                item.id(),
                item.content(),
                toDomainStatus(item.status()),
                toDomainPriority(item.priority()),
                null,
                null
        );
    }

    private TodoStateService.TodoStatus toDomainStatus(TodoStatus status) {
        if (status == null) {
            return null;
        }
        return TodoStateService.TodoStatus.fromValue(status.getValue());
    }

    private TodoStateService.TodoPriority toDomainPriority(TodoPriority priority) {
        if (priority == null) {
            return null;
        }
        return TodoStateService.TodoPriority.fromValue(priority.getValue());
    }

    private String formatMutationSummary(String operation, TodoStateService.MutationResult result) {
        return String.format(
                "%s ok: changed=%d, version=%d->%d, idempotentReplay=%s",
                operation,
                result.changedCount(),
                result.before().meta().version(),
                result.after().meta().version(),
                result.idempotentReplay());
    }

    private String executeWithMetrics(String toolName, ToolAction action) {
        long calls = TODO_TOOL_CALLS_TOTAL.incrementAndGet();
        logger.debug("todo_tool_calls_total={} tool={}", calls, toolName);
        try {
            return action.run();
        } catch (TodoToolException e) {
            countTypedFailure(e);
            long failures = TODO_TOOL_FAILURES_TOTAL.incrementAndGet();
            logger.warn("todo_tool_failures_total={} tool={} code={} message={}",
                    failures, toolName, e.getErrorCode(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            long failures = TODO_TOOL_FAILURES_TOTAL.incrementAndGet();
            logger.warn("todo_tool_failures_total={} tool={} code=TODO_UNEXPECTED_ERROR message={}",
                    failures, toolName, e.getMessage());
            throw e;
        }
    }

    private void countTypedFailure(TodoToolException e) {
        if (TodoStateService.ERROR_STATE_CONFLICT.equals(e.getErrorCode())) {
            long conflicts = TODO_STATE_CONFLICT_TOTAL.incrementAndGet();
            logger.debug("todo_state_conflict_total={}", conflicts);
        }
        if (TodoStateService.ERROR_INVALID_TRANSITION.equals(e.getErrorCode())) {
            long invalidTransitions = TODO_INVALID_TRANSITION_TOTAL.incrementAndGet();
            logger.debug("todo_invalid_transition_total={}", invalidTransitions);
        }
    }

    @FunctionalInterface
    private interface ToolAction {
        String run();
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum TodoStatus {
        PENDING("pending"),
        IN_PROGRESS("in_progress"),
        BLOCKED("blocked"),
        COMPLETED("completed");

        private final String value;

        TodoStatus(String value) {
            this.value = value;
        }

        @JsonCreator
        public static TodoStatus fromValue(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim().toLowerCase();
            for (TodoStatus status : values()) {
                if (status.value.equals(normalized) || status.name().equalsIgnoreCase(normalized)) {
                    return status;
                }
            }
            throw new IllegalArgumentException(
                    "Unknown status: " + value + ". Valid values: pending,in_progress,blocked,completed");
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum TodoPriority {
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high");

        private final String value;

        TodoPriority(String value) {
            this.value = value;
        }

        @JsonCreator
        public static TodoPriority fromValue(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim().toLowerCase();
            for (TodoPriority priority : values()) {
                if (priority.value.equals(normalized) || priority.name().equalsIgnoreCase(normalized)) {
                    return priority;
                }
            }
            throw new IllegalArgumentException("Unknown priority: " + value + ". Valid values: low,medium,high");
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    public record TodoItem(String id, String content, TodoStatus status, TodoPriority priority) {
    }

    public record UpsertTodosRequest(List<TodoItem> todos, Long expectedVersion, Boolean strictVersionCheck) {
    }

    public record CompleteTodoRequest(String id, Long expectedVersion, Boolean strictVersionCheck) {
    }

    public record DeleteTodoRequest(String id, Long expectedVersion, Boolean strictVersionCheck) {
    }

    public record ClearTodosRequest(Long expectedVersion, Boolean strictVersionCheck) {
    }

    public record ListTodosRequest(TodoStatus status, String query) {
    }
}
