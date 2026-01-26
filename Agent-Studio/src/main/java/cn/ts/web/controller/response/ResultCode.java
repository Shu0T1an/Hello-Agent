package cn.ts.web.controller.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应状态码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ========== 通用 ==========
    SUCCESS(200, "操作成功"),
    ERROR(500, "操作失败"),

    // ========== 客户端错误 4xx ==========
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    CONFLICT(409, "资源冲突"),
    VALIDATION_ERROR(422, "数据验证失败"),

    // ========== Agent 相关 ==========
    AGENT_NOT_FOUND(1001, "Agent 不存在"),
    AGENT_ALREADY_EXISTS(1002, "Agent 已存在"),
    AGENT_NAME_DUPLICATE(1003, "Agent 名称重复"),
    AGENT_INACTIVE(1004, "Agent 未激活"),
    AGENT_LOAD_FAILED(1005, "Agent 加载失败"),
    AGENT_EXECUTION_FAILED(1006, "Agent 执行失败"),

    // ========== 模型相关 ==========
    MODEL_NOT_FOUND(2001, "模型配置不存在"),
    MODEL_ALREADY_EXISTS(2002, "模型配置已存在"),
    MODEL_NAME_DUPLICATE(2003, "模型名称重复"),
    MODEL_INACTIVE(2004, "模型未激活"),
    MODEL_DECRYPTION_FAILED(2005, "API 密钥解密失败"),

    // ========== 工具相关 ==========
    TOOL_NOT_FOUND(3001, "工具不存在"),
    TOOL_ALREADY_EXISTS(3002, "工具已存在"),
    TOOL_NAME_DUPLICATE(3003, "工具名称重复"),
    TOOL_INACTIVE(3004, "工具未激活"),
    TOOL_LOAD_FAILED(3005, "工具加载失败"),
    TOOL_TYPE_INVALID(3006, "工具类型无效"),

    // ========== MCP 相关 ==========
    MCP_CONNECTION_NOT_FOUND(4001, "MCP 连接不存在"),
    MCP_CONNECTION_ALREADY_EXISTS(4002, "MCP 连接已存在"),
    MCP_CONNECTION_NAME_DUPLICATE(4003, "MCP 连接名称重复"),
    MCP_CONNECTION_FAILED(4004, "MCP 连接失败"),
    MCP_DISCONNECT_FAILED(4005, "MCP 断开连接失败"),
    MCP_HEALTH_CHECK_FAILED(4006, "MCP 健康检查失败"),
    MCP_TOOL_SYNC_FAILED(4007, "MCP 工具同步失败"),
    MCP_TYPE_INVALID(4008, "MCP 类型无效"),

    // ========== 业务错误 ==========
    OPERATION_FAILED(5001, "操作失败"),
    OPERATION_NOT_SUPPORTED(5002, "不支持的操作"),
    RESOURCE_LIMIT_EXCEEDED(5003, "资源超出限制"),
    STATE_INVALID(5004, "状态无效");

    /**
     * 状态码
     */
    private final Integer code;

    /**
     * 消息
     */
    private final String message;
}
