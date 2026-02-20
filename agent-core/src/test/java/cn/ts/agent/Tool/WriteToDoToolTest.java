package cn.ts.agent.Tool;

import cn.ts.agent.constant.StateKeys;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.ts.agent.Tool.ToolContextConstants.TOOL_CALL_ID_CONTEXT_KEY;
import static cn.ts.agent.Tool.ToolContextConstants.TOOL_EXTRA_STATE_KEY;
import static cn.ts.agent.Tool.ToolContextConstants.TOOL_STATE_CONTEXT_KEY;
import static cn.ts.agent.Tool.ToolContextConstants.TOOL_TRANSIENT_CONTEXT_KEY;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteToDoToolTest {

    @Test
    void upsertRejectsInvalidRequest() {
        WriteToDoTool tool = new WriteToDoTool();
        ToolContext toolContext = new ToolContext(buildContext(new MapState()));
        assertThrows(TodoToolException.class, () -> tool.upsertTodos(null, toolContext));
    }

    @Test
    void listTodosReadsSameRoundExtraState() {
        WriteToDoTool tool = new WriteToDoTool();
        ToolContext toolContext = new ToolContext(buildContext(new MapState()));

        WriteToDoTool.UpsertTodosRequest request = new WriteToDoTool.UpsertTodosRequest(
                List.of(new WriteToDoTool.TodoItem(null, "plan feature", WriteToDoTool.TodoStatus.PENDING, null)),
                null,
                null
        );
        tool.upsertTodos(request, toolContext);

        String result = tool.listTodos(new WriteToDoTool.ListTodosRequest(null, "plan"), toolContext);
        assertTrue(result.contains("plan feature"));
        assertTrue(result.contains("version=1"));
    }

    @Test
    void listTodosSupportsCheckpointDeserializedMaps() {
        WriteToDoTool tool = new WriteToDoTool();
        Map<String, Object> todo = new HashMap<>();
        todo.put("id", "legacy-1");
        todo.put("content", "from checkpoint");
        todo.put("status", "in_progress");

        Map<String, Object> meta = new HashMap<>();
        meta.put("version", 7L);

        State state = new MapState(Map.of(
                StateKeys.TODOS, List.of(todo),
                StateKeys.TODOS_META, meta
        ));
        ToolContext toolContext = new ToolContext(buildContext(state));

        String result = tool.listTodos(new WriteToDoTool.ListTodosRequest(null, null), toolContext);
        assertTrue(result.contains("from checkpoint"));
        assertTrue(result.contains("version=7"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void upsertRejectsInvalidTransition() {
        WriteToDoTool tool = new WriteToDoTool();
        Map<String, Object> context = buildContext(new MapState());
        ToolContext toolContext = new ToolContext(context);

        tool.upsertTodos(new WriteToDoTool.UpsertTodosRequest(
                List.of(new WriteToDoTool.TodoItem(null, "transition task", WriteToDoTool.TodoStatus.PENDING, null)),
                null,
                null
        ), toolContext);

        Map<String, Object> extra = (Map<String, Object>) context.get(TOOL_EXTRA_STATE_KEY);
        List<Map<String, Object>> todos = (List<Map<String, Object>>) extra.get(StateKeys.TODOS);
        String id = String.valueOf(todos.get(0).get("id"));

        WriteToDoTool.UpsertTodosRequest invalid = new WriteToDoTool.UpsertTodosRequest(
                List.of(new WriteToDoTool.TodoItem(id, "transition task", WriteToDoTool.TodoStatus.COMPLETED, null)),
                null,
                null
        );
        assertThrows(TodoToolException.class, () -> tool.upsertTodos(invalid, toolContext));
    }

    @Test
    void upsertIsIdempotentForSameToolCallIdReplay() {
        WriteToDoTool tool = new WriteToDoTool();
        Map<String, Object> context = buildContext(new MapState());
        context.put(TOOL_CALL_ID_CONTEXT_KEY, "tool-call-1");
        ToolContext toolContext = new ToolContext(context);

        WriteToDoTool.UpsertTodosRequest request = new WriteToDoTool.UpsertTodosRequest(
                List.of(new WriteToDoTool.TodoItem(null, "idempotent task", WriteToDoTool.TodoStatus.PENDING, null)),
                null,
                null
        );

        String first = tool.upsertTodos(request, toolContext);
        String second = tool.upsertTodos(request, toolContext);

        assertTrue(first.contains("version=0->1"));
        assertTrue(second.contains("idempotentReplay=true"));
        assertTrue(second.contains("version=1->1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void completeDeleteAndClearWork() {
        WriteToDoTool tool = new WriteToDoTool();
        Map<String, Object> context = buildContext(new MapState());
        ToolContext toolContext = new ToolContext(context);

        tool.upsertTodos(new WriteToDoTool.UpsertTodosRequest(
                List.of(new WriteToDoTool.TodoItem(null, "task1", WriteToDoTool.TodoStatus.PENDING, null)),
                null,
                null
        ), toolContext);

        Map<String, Object> extra = (Map<String, Object>) context.get(TOOL_EXTRA_STATE_KEY);
        List<Map<String, Object>> todos = (List<Map<String, Object>>) extra.get(StateKeys.TODOS);
        String id = String.valueOf(todos.get(0).get("id"));

        tool.upsertTodos(new WriteToDoTool.UpsertTodosRequest(
                List.of(new WriteToDoTool.TodoItem(id, "task1", WriteToDoTool.TodoStatus.IN_PROGRESS, null)),
                null,
                null
        ), toolContext);
        String completeResult = tool.completeTodo(new WriteToDoTool.CompleteTodoRequest(id, null, null), toolContext);
        String deleteResult = tool.deleteTodo(new WriteToDoTool.DeleteTodoRequest(id, null, null), toolContext);
        String clearResult = tool.clearTodos(new WriteToDoTool.ClearTodosRequest(null, null), toolContext);

        assertTrue(completeResult.contains("complete_todo ok"));
        assertTrue(deleteResult.contains("delete_todo ok"));
        assertTrue(clearResult.contains("clear_todos ok"));
    }

    private Map<String, Object> buildContext(State state) {
        Map<String, Object> context = new HashMap<>();
        context.put(TOOL_STATE_CONTEXT_KEY, state);
        context.put(TOOL_EXTRA_STATE_KEY, new HashMap<String, Object>());
        context.put(TOOL_TRANSIENT_CONTEXT_KEY, new HashMap<String, Object>());
        context.put("messages", new ArrayList<>());
        return context;
    }
}
