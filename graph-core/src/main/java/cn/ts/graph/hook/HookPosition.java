package cn.ts.graph.hook;

/**
 * Hook 位置枚举
 * <p>
 * 定义 Hook 可以插入的位置
 * </p>
 *
 * @author tianshuo
 */
public enum HookPosition {
    /**
     * 在 LLM 调用之前执行
     */
    BEFORE_MODEL,

    /**
     * 在 LLM 调用之后执行
     */
    AFTER_MODEL
}
