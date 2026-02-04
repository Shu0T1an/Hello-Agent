package cn.ts.agent.hook;

import java.util.Objects;

/**
 * 工具配置记录类
 * <p>
 * 记录需要审批的工具及其描述
 * </p>
 *
 * @author tianshuo
 */
public record ToolConfig(
        /**
         * 工具名称
         */
        String name,

        /**
         * 工具描述
         */
        String description) {

    public ToolConfig {
        Objects.requireNonNull(name, "Tool name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Tool name cannot be blank");
        }
    }

    /**
     * 创建工具配置
     *
     * @param name        工具名称
     * @param description 工具描述
     * @return ToolConfig 实例
     */
    public static ToolConfig of(String name, String description) {
        return new ToolConfig(name, description);
    }

    /**
     * 创建只有名称的工具配置
     *
     * @param name 工具名称
     * @return ToolConfig 实例
     */
    public static ToolConfig of(String name) {
        return new ToolConfig(name, "");
    }
}
