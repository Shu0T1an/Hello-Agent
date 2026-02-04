package cn.ts.graph.node;

import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.state.State;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 带配置的异步节点动作接口
 * <p>
 * 扩展 AsyncNodeAction，增加 RunnableConfig 参数支持
 * </p>
 */
@FunctionalInterface
public interface AsyncNodeActionWithConfig {

    /**
     * 异步执行节点动作（带配置）
     *
     * @param state  当前状态
     * @param config 运行配置
     * @return CompletableFuture 包含状态更新
     */
    CompletableFuture<Map<String, Object>> applyAsync(State state, RunnableConfig config);

    /**
     * 将旧的 AsyncNodeAction 转换为 AsyncNodeActionWithConfig
     *
     * @param oldAction 旧的异步节点动作
     * @return 带配置的异步节点动作
     */
    static AsyncNodeActionWithConfig from(AsyncNodeAction oldAction) {
        return (state, config) -> oldAction.applyAsync(state);
    }

    /**
     * 转换为同步动作
     *
     * @return NodeActionWithConfig
     */
    default NodeActionWithConfig toSync() {
        return (state, config) -> {
            try {
                return applyAsync(state, config).get();
            } catch (Exception e) {
                throw new RuntimeException("Async node execution failed", e);
            }
        };
    }
}
