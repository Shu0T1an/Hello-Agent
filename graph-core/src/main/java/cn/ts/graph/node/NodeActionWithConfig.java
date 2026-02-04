package cn.ts.graph.node;

import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.state.State;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 带配置的节点动作接口
 * <p>
 * 扩展 NodeAction，增加 RunnableConfig 参数支持
 * 在 NodeExecutor 执行时，旧的 NodeAction 会被包装成此接口
 * </p>
 */
@FunctionalInterface
public interface NodeActionWithConfig {

    /**
     * 执行节点动作（带配置）
     *
     * @param state  当前状态
     * @param config 运行配置
     * @return 状态更新 Map
     * @throws Exception 执行过程中可能抛出的异常
     */
    Map<String, Object> apply(State state, RunnableConfig config) throws Exception;

    /**
     * 异步执行节点动作（带配置）
     *
     * @param state  当前状态
     * @param config 运行配置
     * @return CompletableFuture 包含状态更新
     */
    default CompletableFuture<Map<String, Object>> applyAsync(State state, RunnableConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apply(state, config);
            } catch (Exception e) {
                throw new RuntimeException("Node execution failed", e);
            }
        });
    }

    /**
     * 将旧的 NodeAction 转换为 NodeActionWithConfig
     * <p>
     * 包装后的实现会忽略 config 参数，调用原始的 apply(state)
     * </p>
     *
     * @param oldAction 旧的节点动作
     * @return 带配置的节点动作
     */
    static NodeActionWithConfig from(NodeAction oldAction) {
        return (state, config) -> oldAction.apply(state);
    }

    /**
     * 将带配置的动作转换为旧接口（丢失 config 信息）
     *
     * @param config 默认配置
     * @return 旧接口
     */
    default NodeAction toSync(RunnableConfig config) {
        return state -> apply(state, config);
    }
}
