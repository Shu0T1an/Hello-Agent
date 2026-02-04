package cn.ts.agent.retry;

import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * 重试工具类
 * 提供针对 429 Too Many Requests 错误的重试策略
 */
public class RetryUtils {

    /**
     * 创建针对 429 错误的重试策略
     * 使用指数退避算法
     *
     * @param config 重试配置
     * @return Retry 实例
     */
    public static Retry retryFor429(RetryConfig config) {
        return Retry.backoff(config.getMaxRetries(), config.getInitialBackoff())
                .filter(is429Error())
                .maxBackoff(config.getMaxBackoff());
    }

    /**
     * 判断异常是否为 429 错误
     * 支持 WebClientResponseException 及其包装异常
     */
    private static Predicate<Throwable> is429Error() {
        return throwable -> {
            // 直接检查
            if (throwable instanceof WebClientResponseException webEx) {
                return webEx.getStatusCode().value() == 429;
            }
            // 检查原因链
            Throwable cause = throwable.getCause();
            while (cause != null) {
                if (cause instanceof WebClientResponseException webEx) {
                    return webEx.getStatusCode().value() == 429;
                }
                cause = cause.getCause();
            }
            return false;
        };
    }

    /**
     * 判断异常是否为可重试的错误
     * 目前包括 429, 503, 502 等临时性错误
     */
    public static Predicate<Throwable> isRetryableError() {
        return throwable -> {
            if (throwable instanceof WebClientResponseException webEx) {
                int status = webEx.getStatusCode().value();
                return status == 429 || status == 503 || status == 502 || status == 504;
            }
            return is429Error().test(throwable);
        };
    }

    /**
     * 创建通用的可重试错误策略
     */
    public static Retry retryForErrors(RetryConfig config) {
        return Retry.backoff(config.getMaxRetries(), config.getInitialBackoff())
                .filter(isRetryableError())
                .maxBackoff(config.getMaxBackoff());
    }

    /**
     * 创建简单的固定间隔重试策略
     *
     * @param maxRetries 最大重试次数
     * @param fixedDelay 固定延迟时间
     * @return Retry 实例
     */
    public static Retry fixedRetry(int maxRetries, Duration fixedDelay) {
        return Retry.fixedDelay(maxRetries, fixedDelay);
    }
}
