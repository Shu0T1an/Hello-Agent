package cn.ts.agent.hook;

import cn.ts.agent.api.AgentResult;
import cn.ts.agent.core.ReactAgent;
import cn.ts.agent.example.ExampleTools;
import cn.ts.graph.GraphResult;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.hook.JumpTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hook 集成示例
 * <p>
 * 展示如何使用 HumanInTheLoopHook 进行人工审批
 * </p>
 *
 * @author tianshuo
 */
public class HookIntegrationExample {

    private static final Logger logger = LoggerFactory.getLogger(HookIntegrationExample.class);

    /**
     * 基本使用示例
     */
    public static void basicExample(ChatModel chatModel) {
        // 创建带 Hook 的 Agent
        ReactAgent agent = ReactAgent.builder()
                .name("ApprovalsAgent")
                .description("需要审批的 Agent")
                .chatModel(chatModel)
                .tools(new ExampleTools())
                .hooks(List.of(
                        HumanInTheLoopHook.builder()
                                .approvalOn("deleteFile", "删除文件，不可逆操作")
                                .approvalOn("sendEmail", "发送邮件")
                                .build()
                ))
                .build();

        // 执行 Agent
        AgentResult result = agent.invoke("请帮我删除 test.txt 文件");

        if (result.isSuccess()) {
            logger.info("Agent 执行成功: {}", result.getOutput());
        } else {
            logger.error("Agent 执行失败: {}", result.getError().getMessage());
        }
    }

    /**
     * 带反馈恢复的示例
     */
    public static void exampleWithFeedback(ChatModel chatModel) {
        ReactAgent agent = ReactAgent.builder()
                .name("ApprovalsAgent")
                .description("需要审批的 Agent")
                .chatModel(chatModel)
                .tools(new ExampleTools())
                .hooks(List.of(
                        HumanInTheLoopHook.builder()
                                .approvalOn("deleteFile", "删除文件，不可逆操作")
                                .build()
                ))
                .build();

        // 第一次执行 - 可能会中断
        GraphResult graphResult = agent.getGraph().invoke(Map.of(
                "input", "请帮我删除 test.txt 文件",
                "max_iterations", 10,
                "iteration", 0,
                "messages", new ArrayList<Message>(),
                "execute_record", new ArrayList<Map<String, Object>>()
        ));

        // 检查是否有中断
        if (graphResult.finalState().value("interruption").isPresent()) {
            logger.info("执行被中断，需要用户审批");

            // 准备反馈数据
            List<Map<String, Object>> feedbacks = new ArrayList<>();

            // 假设用户批准了第一个工具调用，拒绝了第二个
            Map<String, Object> feedback1 = new HashMap<>();
            feedback1.put("id", "tool_call_1");
            feedback1.put("result", "APPROVED");
            feedbacks.add(feedback1);

            Map<String, Object> feedback2 = new HashMap<>();
            feedback2.put("id", "tool_call_2");
            feedback2.put("result", "REJECTED");
            feedbacks.add(feedback2);

            Map<String, Object> feedbackData = new HashMap<>();
            feedbackData.put("feedbacks", feedbacks);

            // 使用反馈恢复执行
            RunnableConfig config = RunnableConfig.builder()
                    .feedbackData(feedbackData)
                    .jumpTo(JumpTo.MODEL) // 跳转回 MODEL 节点
                    .build();

            GraphResult resumedResult = agent.getGraph().invoke(
                    graphResult.finalState().data(),
                    config
            );

            logger.info("恢复执行结果: {}", resumedResult.isSuccess() ? "成功" : "失败");
        }
    }

    /**
     * 全工具审批示例
     */
    public static void exampleWithAllApproval(ChatModel chatModel) {
        ReactAgent agent = ReactAgent.builder()
                .name("StrictApprovalsAgent")
                .description("所有工具都需要审批的 Agent")
                .chatModel(chatModel)
                .tools(new ExampleTools())
                .hooks(List.of(
                        HumanInTheLoopHook.builder()
                                .requireApprovalForAll(true)
                                .approvalMessage("请注意：所有工具调用都需要您的审批")
                                .build()
                ))
                .build();

        AgentResult result = agent.invoke("请帮我搜索今天的天气");
        logger.info("执行结果: {}", result.isSuccess() ? "成功" : "失败");
    }
}
