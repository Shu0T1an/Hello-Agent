package cn.ts.agent.hook;

import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.hook.HookPosition;
import cn.ts.graph.hook.HookPositions;
import cn.ts.graph.hook.JumpTo;
import cn.ts.graph.hook.ModelHook;
import cn.ts.graph.node.AsyncNodeActionWithConfig;
import cn.ts.graph.node.InterruptableAction;
import cn.ts.graph.checkpoint.InterruptionMetadata;
import cn.ts.graph.state.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 人工审批 Hook
 * <p>
 * 在 LLM 调用后检查是否有需要审批的工具调用
 * 如果有，则中断执行等待用户反馈
 * </p>
 *
 * @author tianshuo
 */
@HookPositions(HookPosition.AFTER_MODEL)
public class HumanInTheLoopHook extends ModelHook implements InterruptableAction, AsyncNodeActionWithConfig {

    private static final Logger logger = LoggerFactory.getLogger(HumanInTheLoopHook.class);

    private final Map<String, ToolConfig> approvalTools;
    private final boolean requireApprovalForAll;
    private final String approvalMessage;
    /**
     * 线程局部变量，用于存储待处理的反馈
     * 确保每个线程有自己的副本，避免并发问题
     */
    private final ThreadLocal<List<ToolFeedback>> pendingFeedbacks = ThreadLocal.withInitial(ArrayList::new);

    private HumanInTheLoopHook(Builder builder) {
        this.approvalTools = Map.copyOf(builder.approvalTools);
        this.requireApprovalForAll = builder.requireApprovalForAll;
        this.approvalMessage = builder.approvalMessage != null
                ? builder.approvalMessage
                : "需要人工审批以下工具调用：";
    }

    @Override
    public String getName() {
        return "HumanInTheLoopHook";
    }

    // ===== AsyncNodeActionWithConfig 接口实现 =====

    /**
     * 实现 AsyncNodeActionWithConfig 的 applyAsync 方法
     * <p>
     * 作为节点执行时，NodeExecutor 会优先调用此方法（带 config）
     * </p>
     */
    @Override
    public CompletableFuture<Map<String, Object>> applyAsync(State state, RunnableConfig config) {
        // 现在可以使用 config 了！
        return afterModel(state, config);
    }

    // ===== InterruptableAction 接口实现 =====

    /**
     * 在 LLM 调用后检查是否需要审批
     */
    @Override
    public CompletableFuture<Map<String, Object>> afterModel(State state, RunnableConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> feedbackData = config.feedbackData();
            if (feedbackData != null && !feedbackData.isEmpty()) {
                logger.debug("HumanInTheLoopHook received feedback payload, mode={}, keys={}",
                        feedbackData.get("mode"), feedbackData.keySet());
                return processFeedback(state, feedbackData);
            }

            logger.debug("HumanInTheLoopHook has no feedback payload, skip feedback processing.");
            return Map.of();
        });
    }

    /**
     * 检查是否需要中断
     */
    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, State state, RunnableConfig config) {
        // 获取最后的消息
        if(config.feedbackData()!=null && !config.feedbackData().isEmpty()) {
            return Optional.empty();
        }

        List<Message> messages = state.value("messages", new ArrayList<>());
        if (messages.isEmpty()) {
            return Optional.empty();
        }

        Message last = messages.get(messages.size() - 1);
        if (!(last instanceof AssistantMessage assistantMessage)) {
            return Optional.empty();
        }

        // 检查是否有工具调用
        if (!assistantMessage.hasToolCalls()) {
            return Optional.empty();
        }

        // 查找需要审批的工具调用
        List<ToolFeedback> feedbacks = new ArrayList<>();
        for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
            if (needsApproval(toolCall.name())) {
                ToolConfig toolConfig = approvalTools.get(toolCall.name());
                // toolCall.arguments() 返回 String，需要转换为 Map
                Map<String, Object> argsMap = parseArguments(toolCall.arguments());
                ToolFeedback feedback = ToolFeedback.builder(
                        toolCall.id(),
                        toolCall.name())
                        .arguments(argsMap)
                        .description(toolConfig != null ? toolConfig.description() : "")
                        .result(FeedbackResult.PENDING) // 默认待审批
                        .build();
                feedbacks.add(feedback);
            }
        }

        if (feedbacks.isEmpty()) {
            return Optional.empty();
        }

        // 保存待处理的反馈到线程局部变量
        this.pendingFeedbacks.set(feedbacks);

        // 创建中断元数据
        Map<String, Object> customData = new HashMap<>();
        customData.put("tool_feedbacks", feedbacks);
        customData.put("message", approvalMessage);

        InterruptionMetadata metadata = InterruptionMetadata.builder(nodeId, state)
                .message(approvalMessage)
                .customData(customData)
                .build();

        logger.info("执行中断，等待人工审批: {} 个工具调用需要审批", feedbacks.size());
        return Optional.of(metadata);
    }

    /**
     * 处理用户反馈
     */
    private Map<String, Object> processFeedback(State state, Map<String, Object> feedbackData) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> feedbackList = (List<Map<String, Object>>) feedbackData.get("feedbacks");

        if (feedbackList == null || feedbackList.isEmpty()) {
            logger.debug("HumanInTheLoopHook skip feedback processing because tool feedback list is empty. mode={}",
                    feedbackData.get("mode"));
            return Map.of();
        }

        // 处理反馈
        boolean allApproved = true;
        boolean hasRejection = false;
        List<AssistantMessage.ToolCall> modifiedToolCalls = new ArrayList<>();

        List<Message> messages = state.<List<Message>>value("messages").orElse(new ArrayList<>());
        if (!messages.isEmpty()) {
            Message last = messages.get(messages.size() - 1);
            if (last instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
                for (AssistantMessage.ToolCall originalCall : assistantMessage.getToolCalls()) {
                    // 查找对应的反馈
                    Map<String, Object> feedback = findFeedback(feedbackList, originalCall.id());
                    if (feedback != null) {
                        String result = (String) feedback.get("result");
                        FeedbackResult feedbackResult = FeedbackResult.valueOf(result);

                        if (feedbackResult == FeedbackResult.REJECTED) {
                            allApproved = false;
                            hasRejection = true;
                        } else if (feedbackResult == FeedbackResult.MODIFIED) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> modifiedArgs = (Map<String, Object>) feedback.get("modifiedArguments");
                            if (modifiedArgs != null) {
                                // 创建修改后的 ToolCall
                                modifiedToolCalls.add(createModifiedToolCall(originalCall, modifiedArgs));
                            } else {
                                modifiedToolCalls.add(originalCall);
                            }
                        } else {
                            // APPROVED
                            modifiedToolCalls.add(originalCall);
                        }
                    } else {
                        // 没有反馈，保持原样
                        modifiedToolCalls.add(originalCall);
                    }
                }

                // 更新消息中的 ToolCalls
                if (!modifiedToolCalls.equals(assistantMessage.getToolCalls())) {
                    // 创建新的 AssistantMessage
                    List<Message> newMessages = new ArrayList<>(messages.subList(0, messages.size() - 1));
                    AssistantMessage newMessage = AssistantMessage.builder()
                            .content(assistantMessage.getText())
                            .toolCalls(modifiedToolCalls)
                            .properties(assistantMessage.getMetadata())
                            .build();

                    newMessages.add(newMessage);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("messages", newMessages);
                    updates.put("_tool_calls_modified", true);

                    // 如果有拒绝，跳转到 END
                    if (hasRejection) {
                        updates.put("jump_to", JumpTo.END);
                    }

                    return updates;
                }
            }
        }

        return Map.of();
    }

    private Map<String, Object> findFeedback(List<Map<String, Object>> feedbackList, String toolCallId) {
        return feedbackList.stream()
                .filter(f -> toolCallId.equals(f.get("id")))
                .findFirst()
                .orElse(null);
    }

    private AssistantMessage.ToolCall createModifiedToolCall(AssistantMessage.ToolCall original, Map<String, Object> modifiedArgs) {
        // 创建修改后的 ToolCall
        // ToolCall 构造函数: (id, name, arguments, result)
        // 其中 id, name, arguments, result 都是 String 类型
        String argumentsJson = toJsonString(modifiedArgs);
        return new AssistantMessage.ToolCall(
                original.id(),
                original.name(),
                argumentsJson,
                "" // result 默认为空字符串
        );
    }

    /**
     * 将 Map 转换为 JSON 字符串
     *
     * @param map 参数 Map
     * @return JSON 字符串
     */
    private String toJsonString(Map<String, Object> map) {
        // 简单实现
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        // 如果包含原始字符串，直接返回
        if (map.containsKey("_raw")) {
            return map.get("_raw").toString();
        }
        // TODO: 使用 JSON 库序列化
        return map.toString();
    }

    private boolean needsApproval(String toolName) {
        if (requireApprovalForAll) {
            return true;
        }
        return approvalTools.containsKey(toolName);
    }

    /**
     * 解析工具调用参数
     * <p>
     * 将 JSON 字符串解析为 Map
     * </p>
     *
     * @param argumentsJson JSON 字符串
     * @return 参数 Map
     */
    private Map<String, Object> parseArguments(String argumentsJson) {
        // 简单实现：如果是空或null，返回空 Map
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        // TODO: 使用 JSON 库解析
        // 暂时返回包含原始字符串的 Map
        return Map.of("_raw", argumentsJson);
    }

    /**
     * 获取待处理的反馈列表
     *
     * @return 待处理的反馈列表
     */
    public List<ToolFeedback> getPendingFeedbacks() {
        List<ToolFeedback> feedbacks = pendingFeedbacks.get();
        return feedbacks != null ? new ArrayList<>(feedbacks) : new ArrayList<>();
    }

    /**
     * 清除待处理的反馈
     * <p>
     * 在处理完反馈后调用，释放线程局部变量
     * </p>
     */
    public void clearPendingFeedbacks() {
        pendingFeedbacks.remove();
    }

    /**
     * 创建 Builder
     *
     * @return Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder 类
     */
    public static class Builder {
        private final Map<String, ToolConfig> approvalTools = new HashMap<>();
        private boolean requireApprovalForAll = false;
        private String approvalMessage;

        /**
         * 添加需要审批的工具
         *
         * @param toolName     工具名称
         * @param description  工具描述
         * @return this
         */
        public Builder approvalOn(String toolName, String description) {
            this.approvalTools.put(toolName, ToolConfig.of(toolName, description));
            return this;
        }

        /**
         * 添加需要审批的工具（只有名称）
         *
         * @param toolName 工具名称
         * @return this
         */
        public Builder approvalOn(String toolName) {
            this.approvalTools.put(toolName, ToolConfig.of(toolName));
            return this;
        }

        /**
         * 设置是否所有工具都需要审批
         *
         * @param requireAll true 表示所有工具都需要审批
         * @return this
         */
        public Builder requireApprovalForAll(boolean requireAll) {
            this.requireApprovalForAll = requireAll;
            return this;
        }

        /**
         * 设置审批提示消息
         *
         * @param message 提示消息
         * @return this
         */
        public Builder approvalMessage(String message) {
            this.approvalMessage = message;
            return this;
        }

        /**
         * 构建 HumanInTheLoopHook
         *
         * @return HumanInTheLoopHook 实例
         */
        public HumanInTheLoopHook build() {
            return new HumanInTheLoopHook(this);
        }
    }
}
