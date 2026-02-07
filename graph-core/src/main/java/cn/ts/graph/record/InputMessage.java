package cn.ts.graph.record;

import java.util.Map;

/**
 * 输入消息记录
 * <p>
 * 简化的消息表示，用于执行记录
 * </p>
 *
 * @param role    消息角色（user/assistant/system）
 * @param content 消息内容
 * @author tianshuo
 */
public record InputMessage(
        String role,
        String content
) {
    /**
     * 从Map创建
     */
    public static InputMessage fromMap(Map<String, Object> map) {
        return new InputMessage(
                (String) map.get("role"),
                (String) map.get("content")
        );
    }

    /**
     * 转换为Map
     */
    public Map<String, Object> toMap() {
        return Map.of(
                "role", role,
                "content", content
        );
    }
}
