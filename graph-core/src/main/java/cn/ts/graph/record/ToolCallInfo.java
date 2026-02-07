package cn.ts.graph.record;

import java.util.Map;

/**
 * 工具调用信息记录
 *
 * @param id        工具调用ID
 * @param name      工具名称
 * @param arguments 工具参数（JSON字符串）
 * @author tianshuo
 */
public record ToolCallInfo(
        String id,
        String name,
        String arguments
) {
    /**
     * 从Map创建
     */
    public static ToolCallInfo fromMap(Map<String, Object> map) {
        return new ToolCallInfo(
                (String) map.get("id"),
                (String) map.get("name"),
                (String) map.get("arguments")
        );
    }

    /**
     * 转换为Map
     */
    public Map<String, Object> toMap() {
        return Map.of(
                "id", id,
                "name", name,
                "arguments", arguments
        );
    }
}
