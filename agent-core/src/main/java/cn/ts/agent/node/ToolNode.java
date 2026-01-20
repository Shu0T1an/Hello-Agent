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
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.springframework.ai.model.tool.ToolCallingChatOptions.validateToolCallbacks;

/**
 * 工具节点：处理工具调用
 * <p>
 * 功能：
 * 1. 从 State 中获取 AssistantMessage 的 toolCalls
 * 2. 执行工具调用
 * 3. 将 ToolResponseMessage 追加到 messages 列表
 * 4. 递增 iteration 计数
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
        this.toolCallbacks = new ArrayList<>();

        if (tools != null && tools.length > 0) {
            // 使用 Spring AI 提供的 ToolCallbacks.from() 方法
            // 自动从工具对象中提取 ToolCallback
            ToolCallback[] callbacks = getToolCallbacksFromTools(Arrays.asList(tools));
            this.toolCallbacks.addAll(Arrays.asList(callbacks));
            logger.debug("ToolNode initialized with {} tool callbacks from {} tool objects",
                    toolCallbacks.size(), tools.length);
        }
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
        logger.debug("ToolNode processing state: {}", state);

        // 1. 获取当前消息列表
        List<Message> messages = new ArrayList<>(
                state.value("messages", new ArrayList<Message>())
        );

        // 2. 检查是否有消息
        if (messages.isEmpty()) {
            logger.debug("No messages in state, returning empty result");
            return Map.of();
        }

        // 3. 获取最后一条消息
        Message last = messages.get(messages.size() - 1);

        // 4. 检查是否为 AssistantMessage
        if (!(last instanceof AssistantMessage am)) {
            logger.debug("Last message is not AssistantMessage, type: {}", last.getClass());
            return Map.of();
        }

        // 5. 检查是否有工具调用
        if (!am.hasToolCalls()) {
            logger.debug("AssistantMessage has no tool calls");
            return Map.of();
        }

        // 6. 执行所有工具调用
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
            logger.debug("Executing tool call: {}", tc.name());
            ToolCallback tool = findTool(tc.name());
            if (tool != null) {
                try {
                    String result = tool.call(tc.arguments(), new ToolContext(Map.of()));
                    responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));
                    logger.debug("Tool {} executed successfully", tc.name());
                } catch (Exception e) {
                    logger.error("Tool {} execution failed: {}", tc.name(), e.getMessage());
                    responses.add(new ToolResponseMessage.ToolResponse(
                            tc.id(), tc.name(), "Error: " + e.getMessage()
                    ));
                }
            } else {
                logger.warn("Tool not found: {}", tc.name());
                responses.add(new ToolResponseMessage.ToolResponse(
                        tc.id(), tc.name(), "Error: Tool not found: " + tc.name()
                ));
            }
        }

        // 7. 创建 ToolResponseMessage
        ToolResponseMessage toolResponseMessage = new ToolResponseMessage(responses);

        // 8. 递增 iteration
        int iteration = state.<Integer>value("iteration").orElse(0);
        int nextIteration = iteration + 1;
        logger.debug("ToolNode completed, iteration: {} -> {}", iteration, nextIteration);

        // 只返回新增的 ToolResponseMessage，让 AppendStrategy 追加到现有列表
        return Map.of(
                "messages", List.of(toolResponseMessage),
                "iteration", nextIteration
        );
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


    public ToolCallback[] getToolCallbacksFromTools(List<Object> tools) {
        var toolCallbacks = tools.stream()
                .map(toolObject -> Stream
                        .of(ReflectionUtils.getDeclaredMethods(
                                AopUtils.isAopProxy(toolObject) ? AopUtils.getTargetClass(toolObject) : toolObject.getClass()))
                        .filter(toolMethod -> toolMethod.isAnnotationPresent(Tool.class))
                        .filter(toolMethod -> !isFunctionalType(toolMethod))
                        .map(toolMethod -> MethodToolCallback.builder()
                                .toolDefinition(ToolDefinitions.from(toolMethod))
                                .toolMetadata(ToolMetadata.from(toolMethod))
                                .toolMethod(toolMethod)
                                .toolObject(toolObject)
                                .toolCallResultConverter(ToolUtils.getToolCallResultConverter(toolMethod))
                                .build())
                        .toArray(ToolCallback[]::new))
                .flatMap(Stream::of)
                .toArray(ToolCallback[]::new);

        validateToolCallbacks(List.of(toolCallbacks));

        return toolCallbacks;
    }

    private boolean isFunctionalType(Method toolMethod) {
        var isFunction = ClassUtils.isAssignable(toolMethod.getReturnType(), Function.class)
                || ClassUtils.isAssignable(toolMethod.getReturnType(), Supplier.class)
                || ClassUtils.isAssignable(toolMethod.getReturnType(), Consumer.class);

        if (isFunction) {
            logger.warn("Method {} is annotated with @Tool but returns a functional type. "
                    + "This is not supported and the method will be ignored.", toolMethod.getName());
        }

        return isFunction;
    }


}
