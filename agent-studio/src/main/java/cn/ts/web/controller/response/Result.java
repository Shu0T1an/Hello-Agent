package cn.ts.web.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * 统一响应结果封装类
 *
 * @param <T> 数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 状态码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null, Instant.now().toEpochMilli());
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, Instant.now().toEpochMilli());
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data, Instant.now().toEpochMilli());
    }

    /**
     * 失败响应（默认错误）
     */
    public static <T> Result<T> error() {
        return new Result<>(ResultCode.ERROR.getCode(), ResultCode.ERROR.getMessage(), null, Instant.now().toEpochMilli());
    }

    /**
     * 失败响应（自定义消息）
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(ResultCode.ERROR.getCode(), message, null, Instant.now().toEpochMilli());
    }

    /**
     * 失败响应（指定状态码和消息）
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null, Instant.now().toEpochMilli());
    }

    /**
     * 失败响应（指定状态码）
     */
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null, Instant.now().toEpochMilli());
    }

    /**
     * 失败响应（指定状态码和消息）
     */
    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null, Instant.now().toEpochMilli());
    }

    /**
     * 失败响应（指定状态码、消息和数据）
     */
    public static <T> Result<T> error(ResultCode resultCode, String message, T data) {
        return new Result<>(resultCode.getCode(), message, data, Instant.now().toEpochMilli());
    }

    /**
     * 根据布尔值返回成功或失败
     */
    public static <T> Result<T> from(boolean success) {
        return success ? success() : error();
    }

    /**
     * 根据布尔值返回成功或失败（带消息）
     */
    public static <T> Result<T> from(boolean success, String successMsg, String errorMsg) {
        return success ? success(successMsg, null) : error(errorMsg);
    }
}
