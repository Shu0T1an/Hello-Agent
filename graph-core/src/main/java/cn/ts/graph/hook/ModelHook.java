package cn.ts.graph.hook;

import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.state.State;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Model 级 Hook 抽象类
 * <p>
 * 提供 LLM 节点前后的 Hook 基础实现
 * </p>
 *
 * @author tianshuo
 */
public abstract class ModelHook implements Hook {

    private String agentName;

    @Override
    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    @Override
    public String getAgentName() {
        return agentName;
    }

    /**
     * 在 LLM 调用之前执行
     *
     * @param state  当前状态
     * @param config 运行配置
     * @return CompletableFuture 包含状态更新
     */
    public CompletableFuture<Map<String, Object>> beforeModel(
            State state, RunnableConfig config) {
        return CompletableFuture.completedFuture(Map.of());
    }

    /**
     * 在 LLM 调用之后执行
     *
     * @param state  当前状态
     * @param config 运行配置
     * @return CompletableFuture 包含状态更新
     */
    public CompletableFuture<Map<String, Object>> afterModel(
            State state, RunnableConfig config) {
        return CompletableFuture.completedFuture(Map.of());
    }
}
