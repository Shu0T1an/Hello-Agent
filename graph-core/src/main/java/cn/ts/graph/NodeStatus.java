package cn.ts.graph;

/**
 * 节点执行状态枚举
 * <p>
 * 用于标记节点的执行状态，支持前端 Timeline UI 显示
 * </p>
 *
 * @author tianshuo
 */
public enum NodeStatus {
    /**
     * 等待执行
     */
    PENDING("等待执行", "pending"),

    /**
     * 开始执行
     */
    STARTING("开始执行", "starting"),

    /**
     * 执行中
     */
    RUNNING("执行中", "running"),

    /**
     * 已完成
     */
    COMPLETED("已完成", "completed"),

    /**
     * 执行失败
     */
    FAILED("执行失败", "failed"),

    UNKNOWN("未知状态", "unknown");

    private final String label;
    private final String code;

    NodeStatus(String label, String code) {
        this.label = label;
        this.code = code;
    }

    /**
     * 获取状态标签（用于显示）
     *
     * @return 状态标签
     */
    public String getLabel() {
        return label;
    }

    /**
     * 获取状态代码（用于前端识别）
     *
     * @return 状态代码
     */
    public String getCode() {
        return code;
    }
}
