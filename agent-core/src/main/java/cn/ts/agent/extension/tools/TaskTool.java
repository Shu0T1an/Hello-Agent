package cn.ts.agent.extension.tools;

import cn.ts.agent.api.AgentResult;
import cn.ts.agent.core.ReactAgent;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;

/**
 * Tool used by the main agent to delegate isolated tasks to subagents.
 */
public class TaskTool {

    private final Map<String, ReactAgent> subAgents;

    public TaskTool(Map<String, ReactAgent> subAgents) {
        this.subAgents = subAgents;
    }

    @Tool(name = "task", description = "Delegate an isolated task to a subagent by subagent_type.")
    public String task(
            @ToolParam(description = "Task payload containing description and subagent_type")
            TaskRequest request,
            ToolContext toolContext) {
        if (request == null || request.description == null || request.description.isBlank()) {
            return "Error: description is required";
        }
        if (request.subagentType == null || request.subagentType.isBlank()) {
            return "Error: subagent_type is required";
        }
        if (subAgents == null || !subAgents.containsKey(request.subagentType)) {
            return "Error: unknown subagent_type '" + request.subagentType + "', available: "
                    + (subAgents == null ? "[]" : subAgents.keySet());
        }

        ReactAgent subAgent = subAgents.get(request.subagentType);
        try {
            AgentResult result = subAgent.invoke(request.description);
            if (result == null) {
                return "Error executing subagent task: empty result";
            }
            if (result.isSuccess()) {
                return result.getOutput();
            }
            Throwable error = result.getError();
            return "Error executing subagent task: " + (error != null ? error.getMessage() : "unknown error");
        } catch (Exception e) {
            return "Error executing subagent task: " + e.getMessage();
        }
    }

    public static class TaskRequest {

        @JsonProperty(required = true)
        @JsonPropertyDescription("Detailed description of the task to be performed by the subagent")
        public String description;

        @JsonProperty(required = true, value = "subagent_type")
        @JsonPropertyDescription("The type of subagent to use for this task")
        public String subagentType;

        public TaskRequest() {
        }

        public TaskRequest(String description, String subagentType) {
            this.description = description;
            this.subagentType = subagentType;
        }
    }
}
