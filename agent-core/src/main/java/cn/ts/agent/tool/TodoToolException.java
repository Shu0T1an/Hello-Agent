package cn.ts.agent.tool;

/**
 * Todo 工具业务异常，包含稳定错误码用于观测和执行记录。
 */
public class TodoToolException extends RuntimeException {

    private final String errorCode;

    public TodoToolException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TodoToolException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
