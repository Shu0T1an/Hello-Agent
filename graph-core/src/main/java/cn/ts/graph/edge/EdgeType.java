package cn.ts.graph.edge;

/**
 * 边类型枚举
 * <p>
 * 定义图中边的类型：普通边和条件边
 * 参考 Spring AI Alibaba Graph 的边类型设计
 * </p>
 *
 * @author tianshuo
 */
public enum EdgeType {

    /**
     * 普通边
     * <p>
     * 从一个节点直接连接到另一个节点，无条件判断
     * </p>
     */
    NORMAL,

    /**
     * 条件边
     * <p>
     * 根据状态中的值动态决定下一个节点
     * </p>
     */
    CONDITIONAL
}
