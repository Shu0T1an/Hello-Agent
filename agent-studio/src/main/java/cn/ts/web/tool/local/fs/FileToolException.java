package cn.ts.web.tool.local.fs;

public class FileToolException extends RuntimeException {

    private final String errorCode;

    public FileToolException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public FileToolException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

