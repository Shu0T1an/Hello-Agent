package cn.ts.agent.hook;

import cn.ts.agent.core.ReactAgent;
import cn.ts.agent.example.ExampleTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

/**
 * 自定义 Hook 示例
 * <p>
 * 展示如何创建和使用自定义 Hook
 * </p>
 *
 * @author tianshuo
 */
public class CustomHookExample {

    private static final Logger logger = LoggerFactory.getLogger(CustomHookExample.class);

    /**
     * 使用多个 Hook 的示例
     */
    public static void multipleHooksExample(ChatModel chatModel) {
        ReactAgent agent = ReactAgent.builder()
                .name("MultiHookAgent")
                .description("使用多个 Hook 的 Agent")
                .chatModel(chatModel)
                .tools(new ExampleTools())
                .hooks(List.of(
                        // 日志 Hook - 记录执行过程
                        LoggingHook.builder()
                                .prefix("[MyAgent]")
                                .logMessages(true)
                                .logState(true)
                                .build(),

                        // 人工审批 Hook - 审批危险操作
                        HumanInTheLoopHook.builder()
                                .approvalOn("deleteFile", "删除文件，不可逆操作")
                                .approvalOn("sendEmail", "发送邮件")
                                .build()
                ))
                .build();

        logger.info("Agent 创建成功: {}", agent.getName());
    }

    /**
     * 创建自定义 Hook 的示例
     * <p>
     * 这个示例展示如何创建一个简单的 Hook
     * </p>
     */
    public static void createCustomHookExample() {
        /*
        // 自定义 Hook 示例

        @HookPositions(HookPosition.BEFORE_MODEL)
        public class TimingHook extends ModelHook {
            private final Map<String, Long> startTimes = new ConcurrentHashMap<>();

            @Override
            public String getName() {
                return "TimingHook";
            }

            @Override
            public CompletableFuture<Map<String, Object>> beforeModel(
                    State state, RunnableConfig config) {
                return CompletableFuture.supplyAsync(() -> {
                    String key = UUID.randomUUID().toString();
                    startTimes.put(key, System.currentTimeMillis());
                    return Map.of("_timing_key", key);
                });
            }

            @Override
            public CompletableFuture<Map<String, Object>> afterModel(
                    State state, RunnableConfig config) {
                return CompletableFuture.supplyAsync(() -> {
                    String key = state.value("_timing_key").orElse(null);
                    if (key != null && startTimes.containsKey(key)) {
                        long elapsed = System.currentTimeMillis() - startTimes.get(key);
                        logger.info("LLM 调用耗时: {} ms", elapsed);
                        startTimes.remove(key);
                    }
                    return Map.of();
                });
            }
        }
        */
    }

    /**
     * Hook 执行顺序示例
     * <p>
     * Hook 按照添加顺序执行：
     * 1. LoggingHook.beforeModel
     * 2. MODEL 节点
     * 3. LoggingHook.afterModel
     * 4. HumanInTheLoopHook.afterModel
     * </p>
     */
    public static void hookExecutionOrderExample(ChatModel chatModel) {
        ReactAgent agent = ReactAgent.builder()
                .name("OrderedHooksAgent")
                .description("展示 Hook 执行顺序的 Agent")
                .chatModel(chatModel)
                .tools(new ExampleTools())
                .hooks(List.of(
                        // 第一个 Hook
                        LoggingHook.builder()
                                .prefix("[Step1]")
                                .build(),

                        // 第二个 Hook
                        HumanInTheLoopHook.builder()
                                .approvalOn("deleteFile", "删除文件")
                                .build()
                ))
                .build();

        // 执行顺序：
        // START -> LoggingHook.before -> MODEL -> LoggingHook.after -> HumanInTheLoopHook.after -> ...
    }
}
