package cn.ts.graph.node;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * 节点动作异步工具类
 * <p>
 * 提供同步转异步的工具方法，支持自定义线程池和超时控制
 * </p>
 *
 * @author tianshuo
 */
public final class NodeActionAsyncUtils {

    private NodeActionAsyncUtils() {
        // 工具类不允许实例化
    }

    /**
     * 默认线程池
     */
    private static final ForkJoinPool DEFAULT_POOL = ForkJoinPool.commonPool();

    /**
     * 将同步节点动作包装为异步执行（使用默认线程池）
     *
     * @param action 同步节点动作
     * @return 异步节点动作
     */
    public static AsyncNodeAction async(NodeAction action) {
        return AsyncNodeAction.fromSync(action, DEFAULT_POOL);
    }

    /**
     * 将同步节点动作包装为异步执行（使用自定义线程池）
     *
     * @param action   同步节点动作
     * @param executor 自定义线程池
     * @return 异步节点动作
     */
    public static AsyncNodeAction async(NodeAction action, Executor executor) {
        return AsyncNodeAction.fromSync(action, executor);
    }

    /**
     * 将同步节点动作包装为异步执行（带超时控制）
     * <p>
     * 如果执行超过指定超时时间，将抛出 TimeoutException
     * </p>
     *
     * @param action  同步节点动作
     * @param timeout 超时时间
     * @return 异步节点动作
     */
    public static AsyncNodeAction asyncWithTimeout(NodeAction action, Duration timeout) {
        return state -> CompletableFuture.supplyAsync(() -> {
            try {
                return action.apply(state);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, DEFAULT_POOL).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 将同步节点动作包装为异步执行（带超时和自定义线程池）
     *
     * @param action   同步节点动作
     * @param timeout  超时时间
     * @param executor 自定义线程池
     * @return 异步节点动作
     */
    public static AsyncNodeAction asyncWithTimeout(NodeAction action, Duration timeout, Executor executor) {
        return state -> CompletableFuture.supplyAsync(() -> {
            try {
                return action.apply(state);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 创建带重试机制的异步节点动作
     * <p>
     * 当执行失败时，会自动重试指定次数，使用非阻塞延迟
     * </p>
     *
     * @param action      异步节点动作
     * @param maxRetries  最大重试次数
     * @param delayBetweenRetries 重试之间的延迟
     * @return 带重试的异步节点动作
     */
    public static AsyncNodeAction withRetry(AsyncNodeAction action, int maxRetries, Duration delayBetweenRetries) {
        return state -> {
            CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
            retryWithBackoff(action, state, maxRetries, delayBetweenRetries, 0, result);
            return result;
        };
    }

    /**
     * 使用 CompletableFuture.delayedExecutor 进行非阻塞延迟重试
     *
     * @param action  异步节点动作
     * @param state   当前状态
     * @param maxRetries 最大重试次数
     * @param delay   重试延迟
     * @param attempt 当前尝试次数
     * @param result  结果 Future
     */
    private static void retryWithBackoff(AsyncNodeAction action, cn.ts.graph.state.State state,
                                         int maxRetries, Duration delay, int attempt,
                                         CompletableFuture<Map<String, Object>> result) {
        action.applyAsync(state).whenComplete((r, e) -> {
            if (e == null) {
                result.complete(r);
            } else if (attempt < maxRetries) {
                // 使用 delayedExecutor 进行非阻塞延迟
                CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS)
                        .execute(() -> retryWithBackoff(action, state, maxRetries, delay, attempt + 1, result));
            } else {
                result.completeExceptionally(e);
            }
        });
    }

    /**
     * 创建带回退机制的异步节点动作
     * <p>
     * 当主动作执行失败时，执行回退动作
     * </p>
     *
     * @param primary    主异步节点动作
     * @param fallback   回退异步节点动作
     * @return 带回退的异步节点动作
     */
    public static AsyncNodeAction withFallback(AsyncNodeAction primary, AsyncNodeAction fallback) {
        return state -> primary.applyAsync(state)
                .exceptionallyCompose(e -> fallback.applyAsync(state));
    }

    /**
     * 创建一个延迟执行的异步节点动作
     *
     * @param action 异步节点动作
     * @param delay  延迟时间
     * @return 延迟执行的异步节点动作
     */
    public static AsyncNodeAction delayed(AsyncNodeAction action, Duration delay) {
        return state -> CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
            return null;
        }, DEFAULT_POOL).thenCompose(r -> action.applyAsync(state));
    }

    /**
     * 创建并行执行的异步节点动作组合
     * <p>
     * 多个动作并行执行，结果合并返回
     * </p>
     *
     * @param actions 多个异步节点动作
     * @return 组合的异步节点动作
     */
    public static AsyncNodeAction parallel(AsyncNodeAction... actions) {
        return state -> {
            CompletableFuture<?>[] futures = new CompletableFuture[actions.length];
            for (int i = 0; i < actions.length; i++) {
                futures[i] = actions[i].applyAsync(state);
            }
            return CompletableFuture.allOf(futures)
                    .thenApply(v -> {
                        Map<String, Object> result = new java.util.HashMap<>();
                        for (CompletableFuture<?> future : futures) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> partial = (Map<String, Object>) future.join();
                            if (partial != null) {
                                result.putAll(partial);
                            }
                        }
                        return result;
                    });
        };
    }

    /**
     * 创建一个总是返回固定值的异步节点动作
     *
     * @param value 固定返回值
     * @return 异步节点动作
     */
    public static AsyncNodeAction constant(Map<String, Object> value) {
        return state -> CompletableFuture.completedFuture(value);
    }

    /**
     * 创建一个异步节点动作，将执行结果应用转换函数
     *
     * @param action    原始异步节点动作
     * @param transformer 结果转换函数
     * @return 转换后的异步节点动作
     */
    public static AsyncNodeAction mapResult(AsyncNodeAction action,
                                           Function<Map<String, Object>, Map<String, Object>> transformer) {
        return state -> action.applyAsync(state).thenApply(transformer);
    }

    /**
     * 创建一个异步节点动作，过滤执行结果
     *
     * @param action   原始异步节点动作
     * @param predicate 过滤条件，返回 true 保留该键值对
     * @return 过滤后的异步节点动作
     */
    public static AsyncNodeAction filterResult(AsyncNodeAction action,
                                              java.util.function.BiPredicate<String, Object> predicate) {
        return mapResult(action, result -> {
            Map<String, Object> filtered = new java.util.HashMap<>();
            result.forEach((k, v) -> {
                if (predicate.test(k, v)) {
                    filtered.put(k, v);
                }
            });
            return filtered;
        });
    }

    /**
     * 批量创建异步节点动作
     *
     * @param actions 同步节点动作数组
     * @return 异步节点动作数组
     */
    public static AsyncNodeAction[] batchAsync(NodeAction... actions) {
        AsyncNodeAction[] asyncActions = new AsyncNodeAction[actions.length];
        for (int i = 0; i < actions.length; i++) {
            asyncActions[i] = async(actions[i]);
        }
        return asyncActions;
    }
}
