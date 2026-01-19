package cn.ts.agent.api;

/**
 * Agent 执行配置
 * <p>
 * 定义 Agent 执行时的配置参数
 * </p>
 *
 * @author tianshuo
 */
public class AgentConfig {

    private final int maxIterations;
    private final double temperature;
    private final String systemPrompt;

    private AgentConfig(Builder builder) {
        this.maxIterations = builder.maxIterations;
        this.temperature = builder.temperature;
        this.systemPrompt = builder.systemPrompt;
    }

    /**
     * 获取默认配置
     *
     * @return 默认配置
     */
    public static AgentConfig defaultConfig() {
        return new Builder().build();
    }

    /**
     * 获取最大迭代次数
     *
     * @return 最大迭代次数
     */
    public int getMaxIterations() {
        return maxIterations;
    }

    /**
     * 获取温度参数
     *
     * @return 温度参数
     */
    public double getTemperature() {
        return temperature;
    }

    /**
     * 获取系统提示词
     *
     * @return 系统提示词
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * Builder 模式
     */
    public static class Builder {
        private int maxIterations = 10;
        private double temperature = 0.7;
        private String systemPrompt = "You are a helpful assistant.";

        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public AgentConfig build() {
            return new AgentConfig(this);
        }
    }
}
