package cn.ts.agent.tool.shell;

public class ShellToolException extends RuntimeException {

    private final String errorCode;

    public ShellToolException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ShellToolException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

