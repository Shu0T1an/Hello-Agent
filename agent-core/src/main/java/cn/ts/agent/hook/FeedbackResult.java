package cn.ts.agent.hook;

/**
 * 反馈结果枚举
 * <p>
 * 定义用户对工具调用的反馈类型
 * </p>
 *
 * @author tianshuo
 */
public enum FeedbackResult {
    /**
     * 待审批（默认状态）
     */
    PENDING,

    /**
     * 批准执行
     */
    APPROVED,

    /**
     * 拒绝执行
     */
    REJECTED,

    /**
     * 修改后执行
     */
    MODIFIED
}
