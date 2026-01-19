package cn.ts.agent.api;

import cn.ts.graph.GraphResult;

/**
 * Agent 执行结果
 * <p>
 * 封装 Agent 执行后的结果信息
 * </p>
 *
 * @author tianshuo
 */
public class AgentResult {

    private final String output;
    private final GraphResult graphResult;
    private final boolean success;
    private final Throwable error;

    private AgentResult(Builder builder) {
        this.output = builder.output;
        this.graphResult = builder.graphResult;
        this.success = builder.success;
        this.error = builder.error;
    }

    /**
     * 创建一个成功的执行结果
     *
     * @param output Agent 输出
     * @param graphResult 图执行结果
     * @return Agent 执行结果
     */
    public static AgentResult success(String output, GraphResult graphResult) {
        return new Builder()
                .output(output)
                .graphResult(graphResult)
                .success(true)
                .build();
    }

    /**
     * 创建一个失败的执行结果
     *
     * @param error 错误
     * @return Agent 执行结果
     */
    public static AgentResult failure(Throwable error) {
        return new Builder()
                .error(error)
                .success(false)
                .build();
    }

    /**
     * 获取 Agent 输出
     *
     * @return Agent 输出
     */
    public String getOutput() {
        return output;
    }

    /**
     * 获取图执行结果
     *
     * @return 图执行结果
     */
    public GraphResult getGraphResult() {
        return graphResult;
    }

    /**
     * 获取执行是否成功
     *
     * @return 如果成功返回 true，否则返回 false
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取执行是否失败
     *
     * @return 如果失败返回 true，否则返回 false
     */
    public boolean isFailure() {
        return !success;
    }

    /**
     * 获取错误（仅当执行失败时）
     *
     * @return 错误对象，如果成功则返回 null
     */
    public Throwable getError() {
        return error;
    }

    @Override
    public String toString() {
        if (success) {
            return "AgentResult{" +
                    "success=true" +
                    ", output='" + output + '\'' +
                    '}';
        } else {
            return "AgentResult{" +
                    "success=false" +
                    ", error='" + error.getMessage() + '\'' +
                    '}';
        }
    }

    /**
     * Builder 模式
     */
    public static class Builder {
        private String output;
        private GraphResult graphResult;
        private boolean success;
        private Throwable error;

        public Builder output(String output) {
            this.output = output;
            return this;
        }

        public Builder graphResult(GraphResult graphResult) {
            this.graphResult = graphResult;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder error(Throwable error) {
            this.error = error;
            return this;
        }

        public AgentResult build() {
            return new AgentResult(this);
        }
    }
}
