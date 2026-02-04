package cn.ts.agent.Tool;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;

import static cn.ts.agent.Tool.ToolContextConstants.TOOL_EXTRA_STATE_KEY;
import static cn.ts.agent.Tool.ToolContextConstants.TOOL_STATE_CONTEXT_KEY;

/**
 * @author: ts
 * @description
 * @create: 2026/1/31 12:11
 */
public class WriteToDoTool {


    /**
     * 更新 Todo 列表
     *
     * @param request 包含 todos 列表的请求对象
     * @param toolContext 工具上下文，用于访问和更新状态
     * @return 操作结果消息
     */
    @Tool(name = "update_todos",
            description = """
                Use this tool to create and manage a structured task list for your current work session.

                ## When to Use This Tool
                - Complex multi-step tasks (3 or more steps)
                - Non-trivial tasks requiring careful planning
                - User explicitly requests todo list
                - User provides multiple tasks

                ## How to Use This Tool
                1. Mark task as in_progress BEFORE starting work
                2. Mark task as completed IMMEDIATELY after finishing
                3. Always have at least one task in_progress until all done

                ## Task States
                - pending: Task not yet started
                - in_progress: Currently working on
                - completed: Task finished successfully
                """)
    public String updateTodos(
            @ToolParam(description = "List of todo items with content and status")
            UpdateTodosRequest request,
            ToolContext toolContext) {

        try {
            // 1. 从 ToolContext 获取状态数据
            Map<String, Object> contextData = toolContext.getContext();
            if (contextData == null) {
                return "Error: Tool context is not available";
            }

            // 2. 获取可更新状态（使用 ToolContextConstants 中定义的键）
            Object extraStateObj = contextData.get(TOOL_EXTRA_STATE_KEY);
            if (extraStateObj == null) {
                return "Error: Extra state is not initialized";
            }

            if (!(extraStateObj instanceof Map)) {
                return "Error: Extra state has invalid type";
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> extraState = (Map<String, Object>) extraStateObj;

            // 3. 将 Todo 列表写入状态
            extraState.put("todos", request.todos());

            // 4. 返回成功消息
            StringBuilder result = new StringBuilder("Todo list updated:\n");
            for (TodoItem todo : request.todos()) {
                result.append(String.format("  - [%s] %s\n",
                        todo.status().getValue(), todo.content()));
            }

            return result.toString();

        } catch (Exception e) {
            return "Error: Failed to update todos - " + e.getMessage();
        }
    }

    /**
     * 获取当前 Todo 列表（可选，用于调试）
     */
    @Tool(name = "get_todos",
            description = "Get the current todo list from the state. Use this to check current progress.")
    public String getTodos(ToolContext toolContext) {
        try {
            Map<String, Object> contextData = toolContext.getContext();
            if (contextData == null) {
                return "No context available";
            }

            // 从可更新状态中读取 todos（使用 ToolContextConstants 中定义的键）
            @SuppressWarnings("unchecked")
            Map<String, Object> state = (Map<String, Object>) contextData.get(TOOL_STATE_CONTEXT_KEY);

            if (state == null || !state.containsKey("todos")) {
                return "No todos found in state";
            }

            @SuppressWarnings("unchecked")
            List<TodoItem> todos = (List<TodoItem>) state.get("todos");

            if (todos == null || todos.isEmpty()) {
                return "Todo list is empty";
            }

            StringBuilder result = new StringBuilder("Current todos:\n");
            for (TodoItem todo : todos) {
                result.append(String.format("  - [%s] %s\n",
                        todo.status().getValue(), todo.content()));
            }

            return result.toString();

        } catch (Exception e) {
            return "Error: Failed to get todos - " + e.getMessage();
        }
    }


    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum TodoStatus {
        PENDING("pending"),
        IN_PROGRESS("in_progress"),
        COMPLETED("completed");

        private final String value;

        TodoStatus(String value) {
            this.value = value;
        }

        @JsonCreator
        public static TodoStatus fromValue(String value) {
            if (value == null) {
                throw new IllegalArgumentException("Status value cannot be null");
            }

            // First try to match against the lowercase values
            for (TodoStatus status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }

            // Fallback: try to match against enum constant names (case-insensitive)
            try {
                return TodoStatus.valueOf(value.toUpperCase());
            }
            catch (IllegalArgumentException e) {
                // If that fails too, throw a helpful error
                throw new IllegalArgumentException(
                        "Unknown status: " + value + ". Valid values are: pending, in_progress, completed");
            }
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    public record UpdateTodosRequest(List<TodoItem> todos) {
    }
    public record TodoItem(String content, TodoStatus status) {
    }


}
