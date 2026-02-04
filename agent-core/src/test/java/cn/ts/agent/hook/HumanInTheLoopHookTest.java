package cn.ts.agent.hook;

import cn.ts.agent.core.ReactAgent;
import cn.ts.agent.example.ExampleTools;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HumanInTheLoopHook 测试
 *
 * @author tianshuo
 */
@Disabled("需要真实的 ChatModel 才能运行")
class HumanInTheLoopHookTest {


    /**
     * 测试基本的 Hook 创建和配置
     */
    @Test
    void testHookCreation() {
        HumanInTheLoopHook hook = HumanInTheLoopHook.builder()
                .approvalOn("deleteFile", "删除文件，不可逆操作")
                .approvalOn("sendEmail", "发送邮件")
                .build();

        assertEquals("HumanInTheLoopHook", hook.getName());
        assertEquals("ReactAgent", hook.getAgentName());
    }

    /**
     * 测试全工具审批模式
     */
    @Test
    void testRequireApprovalForAll() {
        HumanInTheLoopHook hook = HumanInTheLoopHook.builder()
                .requireApprovalForAll(true)
                .approvalMessage("所有工具调用都需要审批")
                .build();

        assertEquals("HumanInTheLoopHook", hook.getName());
    }

    /**
     * 测试带 Hook 的 Agent 创建
     */
    @Test
    @Disabled("需要真实的 ChatModel")
    void testAgentWithHook() {
        // 这个测试需要真实的 ChatModel 才能运行
        // 这里只是展示如何创建带 Hook 的 Agent
//
//        ReactAgent agent = ReactAgent.builder()
//                .name("TestAgent")
//                .description("测试 Agent")
//                .chatModel(chatModel)
//                .tools(new ExampleTools())
//                .hooks(List.of(
//                        HumanInTheLoopHook.builder()
//                                .approvalOn("deleteFile", "删除文件，不可逆操作")
//                                .approvalOn("sendEmail", "发送邮件")
//                                .build()
//                ))
//                .build();
//
//        assertNotNull(agent);

    }
}
