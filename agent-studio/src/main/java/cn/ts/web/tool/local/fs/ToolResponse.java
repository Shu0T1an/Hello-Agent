package cn.ts.web.tool.local.fs;

public record ToolResponse(
        String status,
        String errorCode,
        String message,
        Object data
) {
    public static ToolResponse ok(String message, Object data) {
        return new ToolResponse("ok", null, message, data);
    }

    public static ToolResponse error(String errorCode, String message) {
        return new ToolResponse("error", errorCode, message, null);
    }
}

