package cn.ts.agent.example;

import cn.ts.agent.api.AgentConfig;
import cn.ts.agent.api.AgentResult;
import cn.ts.agent.core.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * ReAct Agent 使用示例
 * <p>
 * 演示如何创建和使用 ReactAgent
 * </p>
 *
 * @author tianshuo
 */
@SpringBootApplication
public class ReactAgentExample {

    public static void main(String[] args) {
        // 启动 Spring 上下文
        ConfigurableApplicationContext context = SpringApplication.run(ReactAgentExample.class, args);

        // 获取 ChatModel 和 Tools
        ChatModel chatModel = context.getBean(ChatModel.class);
        ExampleTools tools = context.getBean(ExampleTools.class);

        // 创建 ReactAgent
        ReactAgent agent = new ReactAgent(
                "CalculatorAgent",
                "一个可以进行数学计算的智能助手",
                chatModel,
                tools
        );

        System.out.println("=== ReAct Agent 示例 ===");
        System.out.println("Agent 名称: " + agent.getName());
        System.out.println("Agent 描述: " + agent.getDescription());
        System.out.println();

        // 示例 1: 简单计算
        System.out.println("示例 1: 计算问题");
        AgentResult result1 = agent.invoke("帮我计算 123 + 456 等于多少？");
        System.out.println("问题: 帮我计算 123 + 456 等于多少？");
        System.out.println("回答: " + result1.getOutput());
        System.out.println("成功: " + result1.isSuccess());
        System.out.println();

        // 示例 2: 复杂计算
        System.out.println("示例 2: 复杂计算");
        AgentResult result2 = agent.invoke("计算 (100 - 25) * 4 等于多少？");
        System.out.println("问题: 计算 (100 - 25) * 4 等于多少？");
        System.out.println("回答: " + result2.getOutput());
        System.out.println("成功: " + result2.isSuccess());
        System.out.println();

        // 示例 3: 时间查询
        System.out.println("示例 3: 时间查询");
        AgentResult result3 = agent.invoke("现在几点了？");
        System.out.println("问题: 现在几点了？");
        System.out.println("回答: " + result3.getOutput());
        System.out.println("成功: " + result3.isSuccess());
        System.out.println();

        // 示例 4: 天气查询
        System.out.println("示例 4: 天气查询");
        AgentResult result4 = agent.invoke("帮我查一下北京的天气");
        System.out.println("问题: 帮我查一下北京的天气");
        System.out.println("回答: " + result4.getOutput());
        System.out.println("成功: " + result4.isSuccess());
        System.out.println();

        // 示例 5: 使用自定义配置
        System.out.println("示例 5: 自定义配置");
        AgentConfig customConfig = new AgentConfig.Builder()
                .maxIterations(20)
                .temperature(0.3)
                .build();
        AgentResult result5 = agent.invoke("帮我做很多次计算：先算 5 + 3，然后算结果乘以 2，最后再减去 4", customConfig);
        System.out.println("问题: 帮我做很多次计算：先算 5 + 3，然后算结果乘以 2，最后再减去 4");
        System.out.println("回答: " + result5.getOutput());
        System.out.println("成功: " + result5.isSuccess());
        System.out.println();

        // 关闭上下文
        context.close();
    }

    /**
     * 创建 ExampleTools Bean
     */
    @Bean
    public ExampleTools exampleTools() {
        return new ExampleTools();
    }
}
