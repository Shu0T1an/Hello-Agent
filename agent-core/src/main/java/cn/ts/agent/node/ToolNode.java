package cn.ts.agent.node;

import cn.ts.graph.node.NodeAction;
import cn.ts.graph.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cn.ts.agent.Tool.ToolContextConstants.TOOL_CALL_ID_CONTEXT_KEY;
import static cn.ts.agent.Tool.ToolContextConstants.TOOL_CALL_NAME_CONTEXT_KEY;
import static cn.ts.agent.Tool.ToolContextConstants.TOOL_EXTRA_STATE_KEY;
import static cn.ts.agent.Tool.ToolContextConstants.TOOL_STATE_CONTEXT_KEY;
import static cn.ts.agent.Tool.ToolContextConstants.TOOL_TRANSIENT_CONTEXT_KEY;
import static cn.ts.agent.Tool.ToolUtils.getAllToolCallbacksFromTools;

/**
 * 工具节点：处理工具调用
 * <p>
 * 功能：
 * 1. 从 State 中获取 AssistantMessage 的 toolCalls
 * 2. 执行工具调用
 * 3. 将 ToolResponseMessage 追加到 messages 列表
 * 4. 递增 iteration 计数
 * 5. 提供执行信息供 NodeExecutor 创建执行记录
 * </p>
 *
 * @author tianshuo
 */
public class ToolNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(ToolNode.class);

    private final List<ToolCallback> toolCallbacks;

    /**
     * 创建工具节点（无工具）
     */
    public ToolNode() {
        this.toolCallbacks = new ArrayList<>();
    }

    /**
     * 创建工具节点（使用工具对象数组）
     * <p>
     * 使用 Spring AI 的 ToolCallbacks.from() 方法自动从工具对象中提取
     * 带有 @Tool 注解的方法，转换为 ToolCallback
     * </p>
     *
     * @param tools 工具对象数组
     */
    public ToolNode(Object... tools) {
        this.toolCallbacks = new ArrayList<>(getAllToolCallbacksFromTools(tools));
        logger.debug("ToolNode initialized with {} tool callbacks from {} tool objects",
                toolCallbacks.size(), tools.length);
    }

    /**
     * 创建工具节点（使用 ToolCallback 列表）
     *
     * @param toolCallbacks 工具回调列表
     */
    public ToolNode(List<ToolCallback> toolCallbacks) {
        this.toolCallbacks = toolCallbacks != null
                ? new ArrayList<>(toolCallbacks)
                : new ArrayList<>();
    }

    @Override
    public Map<String, Object> apply(State state) throws Exception {
        Instant startTime = Instant.now();
        logger.debug("ToolNode processing");

        // 1. 验证消息并获取最后一条 AssistantMessage
        Optional<AssistantMessage> lastMessageOpt = validateAndGetLastMessage(state);
        if (lastMessageOpt.isEmpty()) {
            return Map.of();
        }
        AssistantMessage am = lastMessageOpt.get();

        Map<String, Object> extraStateFromToolCall = new HashMap<>();

        // 2. 执行所有工具调用并收集执行信息
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        List<Map<String, Object>> executions = new ArrayList<>();

        for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
            ToolExecutionResult result = executeToolCall(tc, state, extraStateFromToolCall, startTime);
            responses.add(result.response());
            executions.add(result.executionInfo());
        }

        // 3. 创建 ToolResponseMessage
        ToolResponseMessage toolResponseMessage = new ToolResponseMessage(responses);

        // 4. 递增 iteration
        int nextIteration = state.<Integer>value("iteration").orElse(0) + 1;
        logger.debug("ToolNode completed, iteration: {}", nextIteration);

        // 5. 构建结果
        Map<String, Object> result = new HashMap<>();
        result.put("messages", List.of(toolResponseMessage));
        result.put("iteration", nextIteration);

        // 6. 添加执行信息供 NodeExecutor 使用
        result.put("__execution_info__", buildExecutionInfo(startTime, executions));

        // 7. 合并工具调用产生的额外状态
        result.putAll(extraStateFromToolCall);

        return result;
    }

    /**
     * 构建执行信息
     *
     * @param startTime   开始时间
     * @param executions 执行记录列表
     * @return 执行信息 Map
     */
    private Map<String, Object> buildExecutionInfo(Instant startTime, List<Map<String, Object>> executions) {
        Map<String, Object> execInfo = new HashMap<>();
        execInfo.put("nodeType", "tool");
        execInfo.put("startTime", startTime.toString());
        execInfo.put("executions", executions);
        return execInfo;
    }

    /**
     * 验证消息并获取最后一条 AssistantMessage
     * <p>
     * 执行以下检查：
     * 1. 消息列表是否为空
     * 2. 最后一条消息是否为 AssistantMessage
     * 3. AssistantMessage 是否包含工具调用
     * </p>
     *
     * @param state 当前状态
     * @return 包含有效 AssistantMessage 的 Optional，如果验证失败则返回空
     */
    private Optional<AssistantMessage> validateAndGetLastMessage(State state) {
        // 获取当前消息列表
        List<Message> messages = new ArrayList<>(
                state.value("messages", new ArrayList<Message>())
        );

        // 检查是否有消息
        if (messages.isEmpty()) {
            logger.debug("No messages in state, returning empty result");
            return Optional.empty();
        }

        // 获取最后一条消息
        Message last = messages.get(messages.size() - 1);

        // 检查是否为 AssistantMessage
        if (!(last instanceof AssistantMessage am)) {
            logger.debug("Last message is not AssistantMessage, type: {}", last.getClass());
            return Optional.empty();
        }

        // 检查是否有工具调用
        if (!am.hasToolCalls()) {
            logger.debug("AssistantMessage has no tool calls");
            return Optional.empty();
        }

        return Optional.of(am);
    }

    /**
     * 执行单个工具调用
     *
     * @param toolCall               工具调用对象
     * @param state                  状态
     * @param extraStateFromToolCall 额外状态
     * @param nodeStartTime          节点开始时间
     * @return 工具执行结果（响应和执行信息）
     */
    private ToolExecutionResult executeToolCall(
            AssistantMessage.ToolCall toolCall,
            State state,
            Map<String, Object> extraStateFromToolCall,
            Instant nodeStartTime) {

        logger.debug("Executing tool call: {}", toolCall.name());

        ToolCallback tool = findTool(toolCall.name());
        String result;
        boolean success;
        String errorCode = null;

        Instant toolStartTime = Instant.now();

        Map<String, Object> toolContextMap = new HashMap<>();
        toolContextMap.put(TOOL_STATE_CONTEXT_KEY, state);
        toolContextMap.put(TOOL_EXTRA_STATE_KEY, extraStateFromToolCall);
        toolContextMap.put(TOOL_CALL_ID_CONTEXT_KEY, toolCall.id());
        toolContextMap.put(TOOL_CALL_NAME_CONTEXT_KEY, toolCall.name());
        Map<String, Object> transientContext = new HashMap<>();
        toolContextMap.put(TOOL_TRANSIENT_CONTEXT_KEY, transientContext);

        if (tool != null) {
            try {
                result = tool.call(toolCall.arguments(), new ToolContext(toolContextMap));
                success = true;
                logger.debug("Tool {} executed successfully", toolCall.name());
            } catch (Exception e) {
                logger.error("Tool {} execution failed: {}", toolCall.name(), e.getMessage());
                errorCode = extractErrorCode(e);
                result = "Error: [" + errorCode + "] " + e.getMessage();
                success = false;
            }
        } else {
            logger.warn("Tool not found: {}", toolCall.name());
            result = "Error: Tool not found: " + toolCall.name();
            success = false;
            errorCode = "TOOL_NOT_FOUND";
        }

        Instant toolEndTime = Instant.now();

        ToolResponseMessage.ToolResponse response =
                new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), result);

        Map<String, Object> executionInfo = createExecutionInfo(
                toolCall.id(), toolCall.name(), toolCall.arguments(),
                result, success, errorCode, toolStartTime, toolEndTime, transientContext
        );

        return new ToolExecutionResult(response, executionInfo);
    }

    /**
     * 创建单个工具执行的执行信息
     *
     * @param id          工具调用 ID
     * @param name        工具名称
     * @param arguments   工具参数
     * @param result      执行结果
     * @param success     是否成功
     * @param startTime   开始时间
     * @param endTime     结束时间
     * @return 执行信息 Map
     */
    private Map<String, Object> createExecutionInfo(
            String id, String name, String arguments, String result,
            boolean success, String errorCode, Instant startTime, Instant endTime,
            Map<String, Object> transientContext) {

        long duration = java.time.Duration.between(startTime, endTime).toMillis();

        Map<String, Object> executionInfo = new HashMap<>();
        executionInfo.put("id", id);
        executionInfo.put("name", name);
        executionInfo.put("arguments", arguments);
        executionInfo.put("result", result);
        executionInfo.put("success", success);
        if (errorCode != null) {
            executionInfo.put("errorCode", errorCode);
        }
        executionInfo.put("duration", duration);
        if (transientContext != null) {
            if (transientContext.containsKey("todoVersionBefore")) {
                executionInfo.put("todoVersionBefore", transientContext.get("todoVersionBefore"));
            }
            if (transientContext.containsKey("todoVersionAfter")) {
                executionInfo.put("todoVersionAfter", transientContext.get("todoVersionAfter"));
            }
            if (transientContext.containsKey("changedCount")) {
                executionInfo.put("changedCount", transientContext.get("changedCount"));
            }
        }
        return executionInfo;
    }

    private String extractErrorCode(Exception e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof cn.ts.agent.Tool.TodoToolException todoException) {
                return todoException.getErrorCode();
            }
            current = current.getCause();
        }
        return "TOOL_EXECUTION_ERROR";
    }

    /**
     * 查找工具
     *
     * @param name 工具名称
     * @return ToolCallback 或 null
     */
    private ToolCallback findTool(String name) {
        return toolCallbacks.stream()
                .filter(t -> t.getToolDefinition().name().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * 添加工具回调
     *
     * @param toolCallback 工具回调
     */
    public void addToolCallback(ToolCallback toolCallback) {
        this.toolCallbacks.add(toolCallback);
    }

    /**
     * 获取工具回调列表
     *
     * @return 工具回调列表的不可修改副本
     */
    public List<ToolCallback> getToolCallbacks() {
        return List.copyOf(toolCallbacks);
    }

    /**
     * 工具执行结果（内部记录类）
     *
     * @param response       工具响应
     * @param executionInfo  执行信息
     */
    private record ToolExecutionResult(
            ToolResponseMessage.ToolResponse response,
            Map<String, Object> executionInfo
    ) {}
}
