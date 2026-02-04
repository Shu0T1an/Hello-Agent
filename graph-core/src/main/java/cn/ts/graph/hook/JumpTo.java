package cn.ts.graph.hook;

/**
 * 跳转枚举
 * <p>
 * 定义 Hook 可以跳转到的位置
 * </p>
 *
 * @author tianshuo
 */
public enum JumpTo {
    /**
     * 跳转到结束节点
     */
    END,

    /**
     * 跳转到 LLM 节点
     */
    MODEL,

    /**
     * 跳转到工具节点
     */
    TOOL
}
