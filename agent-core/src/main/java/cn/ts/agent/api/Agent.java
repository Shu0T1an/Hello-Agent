package cn.ts.agent.api;

/**
 * Agent 核心接口
 * <p>
 * 定义 Agent 的基本行为，包括名称、描述和执行接口
 * </p>
 *
 * @author tianshuo
 */
public interface Agent {

    /**
     * 使用默认配置执行 Agent
     *
     * @param input 用户输入
     * @return Agent 执行结果
     */
    AgentResult invoke(String input);

    /**
     * 使用指定配置执行 Agent
     *
     * @param input 用户输入
     * @param config Agent 执行配置
     * @return Agent 执行结果
     */
    AgentResult invoke(String input, AgentConfig config);

    /**
     * 获取 Agent 名称
     *
     * @return Agent 名称
     */
    String getName();

    /**
     * 获取 Agent 描述
     *
     * @return Agent 描述
     */
    String getDescription();
}
