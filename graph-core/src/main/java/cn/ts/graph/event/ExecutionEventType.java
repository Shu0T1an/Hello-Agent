package cn.ts.graph.event;

/**
 * 图执行事件类型枚举
 * <p>
 * 定义图执行过程中的各种事件类型
 * </p>
 *
 * @author tianshuo
 */
public enum ExecutionEventType {

    /**
     * 图开始执行
     */
    GRAPH_STARTED,

    /**
     * 节点开始执行
     */
    NODE_STARTING,

    /**
     * 节点执行完成
     */
    NODE_COMPLETED,

    /**
     * 节点执行失败
     */
    NODE_FAILED,

    /**
     * 状态更新
     */
    STATE_UPDATED,

    /**
     * 边转移
     */
    EDGE_TRANSITION,

    /**
     * 图执行完成
     */
    GRAPH_COMPLETED,

    /**
     * 图执行失败
     */
    GRAPH_FAILED,

    /**
     * 图执行超时
     */
    GRAPH_TIMEOUT
}
