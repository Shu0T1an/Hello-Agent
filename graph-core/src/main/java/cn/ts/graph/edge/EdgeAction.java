package cn.ts.graph.edge;

import cn.ts.graph.state.State;

/**
 * 边动作函数式接口
 * <p>
 * 定义条件边的路由逻辑，根据状态决定下一个节点
 * 参考 Spring AI Alibaba Graph 的 EdgeAction 设计
 * </p>
 *
 * @author tianshuo
 */
@FunctionalInterface
public interface EdgeAction {

    /**
     * 根据当前状态决定下一个节点
     *
     * @param state 当前状态
     * @return 下一个节点的标识
     */
    String route(State state);

    /**
     * 创建边动作的便捷静态方法
     *
     * @param action 边动作
     * @return 边动作
     */
    static EdgeAction of(EdgeAction action) {
        return action;
    }
}
