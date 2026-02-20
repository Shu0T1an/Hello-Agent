package cn.ts.agent.Prompt;

/**
 * Prompt constants.
 */
public class PromptTemplete {

    private static final String TODO_INSTRUCTION = """
            ## `upsert_todos` / `list_todos` / `complete_todo`

            You have access to todo management tools to handle complex objectives.
            Prefer incremental updates with `upsert_todos` instead of full list replacement.
            Use `list_todos` to check progress and `complete_todo` immediately after finishing work.

            ## Important To-Do List Usage Notes
            - Do not call todo-writing tools multiple times in parallel.
            - Keep status transitions valid: pending -> in_progress -> completed.
            - Use `delete_todo` and `clear_todos` only when explicitly required.
            """;

    private PromptTemplete() {
    }
}
