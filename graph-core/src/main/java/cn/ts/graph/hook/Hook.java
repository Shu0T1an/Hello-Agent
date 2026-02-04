package cn.ts.graph.hook;

import java.util.Arrays;
import java.util.List;

/**
 * Hook 基础接口
 * <p>
 * 定义 Hook 的基础行为，所有 Hook 实现都需要实现此接口
 * </p>
 *
 * @author tianshuo
 */
public interface Hook {

    /**
     * 获取 Hook 名称
     *
     * @return Hook 名称
     */
    String getName();

    /**
     * 设置 Agent 名称
     *
     * @param agentName Agent 名称
     */
    void setAgentName(String agentName);

    /**
     * 获取 Agent 名称
     *
     * @return Agent 名称
     */
    String getAgentName();

    /**
     * 获取 Hook 的位置
     * <p>
     * 默认从注解中获取，如果是 ModelHook 则返回 BEFORE_MODEL 和 AFTER_MODEL
     * </p>
     *
     * @return Hook 位置数组
     */
    default HookPosition[] getHookPositions() {
        HookPositions annotation = this.getClass()
                .getAnnotation(HookPositions.class);
        if (annotation != null) {
            return annotation.value();
        }
        if (this instanceof ModelHook) {
            return new HookPosition[]{
                    HookPosition.BEFORE_MODEL,
                    HookPosition.AFTER_MODEL
            };
        }
        return new HookPosition[0];
    }

    /**
     * 获取可以跳转到的位置
     * <p>
     * 默认不跳转，子类可以覆盖以支持跳转
     * </p>
     *
     * @return 可跳转到的位置列表
     */
    default List<JumpTo> canJumpTo() {
        return List.of();
    }

    /**
     * 获取完整的 Hook 节点名称
     * <p>
     * 用于在图中标识 Hook 节点
     * </p>
     *
     * @param hook Hook 实例
     * @return 完整的 Hook 节点名称
     */
    static String getFullHookName(Hook hook) {
        return "__hook_" + hook.getName();
    }

    /**
     * 获取 before 位置的 Hook 节点名称
     *
     * @param hook Hook 实例
     * @return before 位置的节点名称
     */
    static String getBeforeHookName(Hook hook) {
        return "__hook_" + hook.getName() + "_before";
    }

    /**
     * 获取 after 位置的 Hook 节点名称
     *
     * @param hook Hook 实例
     * @return after 位置的节点名称
     */
    static String getAfterHookName(Hook hook) {
        return "__hook_" + hook.getName() + "_after";
    }

    /**
     * 检查 Hook 是否支持指定位置
     *
     * @param position 位置
     * @return 如果支持则返回 true
     */
    default boolean supportsPosition(HookPosition position) {
        return Arrays.asList(getHookPositions()).contains(position);
    }
}
