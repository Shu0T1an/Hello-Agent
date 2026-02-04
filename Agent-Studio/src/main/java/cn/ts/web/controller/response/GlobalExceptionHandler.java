package cn.ts.web.controller.response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.debug("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数验证异常（@RequestBody）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.debug("参数验证失败: {}", message);
        return Result.error(ResultCode.VALIDATION_ERROR.getCode(), message);
    }

    /**
     * 参数验证异常（@ModelAttribute）
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.debug("参数绑定失败: {}", message);
        return Result.error(ResultCode.VALIDATION_ERROR.getCode(), message);
    }

    /**
     * 参数验证异常（@RequestParam/@PathVariable）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.debug("参数约束验证失败: {}", message);
        return Result.error(ResultCode.VALIDATION_ERROR.getCode(), message);
    }

    /**
     * 非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.debug("非法参数: {}", e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    /**
     * 非法状态异常
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleIllegalStateException(IllegalStateException e) {
        log.debug("非法状态: {}", e.getMessage());
        return Result.error(ResultCode.STATE_INVALID.getCode(), e.getMessage());
    }

    /**
     * 运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        return Result.error(ResultCode.ERROR.getCode(), "系统内部错误: " + e.getMessage());
    }

    /**
     * 其他异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("未知异常", e);
        return Result.error(ResultCode.ERROR.getCode(), "系统错误，请联系管理员");
    }

    /**
     * 429 Too Many Requests 错误处理
     */
    @ExceptionHandler(WebClientResponseException.TooManyRequests.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Result<Void> handleTooManyRequests(WebClientResponseException.TooManyRequests e) {
        log.warn("API 速率限制: {}", e.getMessage());
        String retryAfter = e.getHeaders().getFirst("Retry-After");
        String message = "请求过于频繁，请稍后再试";
        if (retryAfter != null) {
            message += "（建议 " + retryAfter + " 秒后重试）";
        }
        return Result.error(ResultCode.RATE_LIMIT_EXCEEDED.getCode(), message);
    }

    /**
     * 通用的 WebClientResponseException 处理
     */
    @ExceptionHandler(WebClientResponseException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Result<Void> handleWebClientResponseException(WebClientResponseException e) {
        log.error("外部 API 调用失败: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());

        String message = switch (e.getStatusCode().value()) {
            case 429 -> "请求过于频繁，请稍后再试";
            case 500, 502, 503, 504 -> "外部服务暂时不可用，请稍后再试";
            case 401 -> "API 认证失败，请检查密钥配置";
            default -> "外部 API 调用失败";
        };

        return Result.error(ResultCode.API_SERVICE_UNAVAILABLE.getCode(), message);
    }
}
