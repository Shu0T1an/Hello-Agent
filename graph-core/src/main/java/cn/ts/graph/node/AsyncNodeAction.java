package cn.ts.graph.node;

import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.state.State;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 异步节点动作函数式接口
 * <p>
 * 定义图节点的异步处理逻辑，接受状态并返回异步的状态更新
 * 专门用于需要异步执行的节点操作，如远程调用、长时间计算等
 * </p>
 *
 * @author tianshuo
 */
@FunctionalInterface
public interface AsyncNodeAction {

    /**
     * 异步执行节点动作
     *
     * @param state 当前状态
     * @return CompletableFuture 包含状态更新 Map
     */
    CompletableFuture<Map<String, Object>> applyAsync(State state);




    /**
     * 将异步节点动作转换为同步动作（阻塞等待）
     * <p>
     * 注意：此方法会阻塞当前线程直到异步操作完成
     * </p>
     *
     * @return 同步的 NodeAction
     */
    default NodeAction toSync() {
        return state -> {
            try {
                return applyAsync(state).get();
            } catch (Exception e) {
                throw new RuntimeException("Async node execution failed", e);
            }
        };
    }

    /**
     * 从同步 NodeAction 创建异步版本
     * <p>
     * 使用 ForkJoinPool.commonPool() 执行同步操作
     * </p>
     *
     * @param action 同步节点动作
     * @return 异步节点动作
     */
    static AsyncNodeAction fromSync(NodeAction action) {
        return state -> CompletableFuture.supplyAsync(() -> {
            try {
                return action.apply(state);
            } catch (Exception e) {
                throw new RuntimeException("Node execution failed", e);
            }
        });
    }

    /**
     * 从同步 NodeAction 创建异步版本（使用自定义执行器）
     *
     * @param action   同步节点动作
     * @param executor 自定义执行器
     * @return 异步节点动作
     */
    static AsyncNodeAction fromSync(NodeAction action, java.util.concurrent.Executor executor) {
        return state -> CompletableFuture.supplyAsync(() -> {
            try {
                return action.apply(state);
            } catch (Exception e) {
                throw new RuntimeException("Node execution failed", e);
            }
        }, executor);
    }

    /**
     * 创建异步节点动作的便捷静态方法
     *
     * @param action 异步节点动作
     * @return 异步节点动作
     */
    static AsyncNodeAction of(AsyncNodeAction action) {
        return action;
    }

    /**
     * 组合多个异步节点动作顺序执行
     * <p>
     * 后一个动作接收前一个动作的结果状态
     * </p>
     *
     * @param then 下一个异步节点动作
     * @return 组合后的异步节点动作
     */
    default AsyncNodeAction andThen(Function<State, AsyncNodeAction> then) {
        return state -> applyAsync(state).thenCompose(result -> {
            State newState = state.copy().apply(result);
            return then.apply(newState).applyAsync(newState);
        });
    }
}
