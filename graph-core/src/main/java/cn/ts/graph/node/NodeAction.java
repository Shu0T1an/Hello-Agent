package cn.ts.graph.node;

import cn.ts.graph.state.State;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 节点动作函数式接口
 * <p>
 * 定义图节点的处理逻辑，接受状态并返回状态更新
 * 参考 Spring AI Alibaba Graph 的 AsyncNodeAction 设计
 * </p>
 *
 * @author tianshuo
 */
@FunctionalInterface
public interface NodeAction {

    /**
     * 执行节点动作
     *
     * @param state 当前状态
     * @return 状态更新 Map，包含要更新的键值对
     * @throws Exception 执行过程中可能抛出的异常
     */
    Map<String, Object> apply(State state) throws Exception;

    /**
     * 创建节点动作的便捷静态方法
     *
     * @param action 节点动作
     * @return 节点动作
     */
    static NodeAction of(NodeAction action) {
        return action;
    }

    /**
     * 将同步节点动作转换为异步执行
     *
     * @param state 当前状态
     * @return CompletableFuture 包含状态更新
     */
    default CompletableFuture<Map<String, Object>> applyAsync(State state) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apply(state);
            } catch (Exception e) {
                throw new RuntimeException("Node execution failed", e);
            }
        });
    }
}
