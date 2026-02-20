package cn.ts.agent.interceptor;

import java.util.concurrent.CompletableFuture;

/**
 * Invoker in the model interceptor chain.
 */
@FunctionalInterface
public interface ModelInvoker {

    CompletableFuture<ModelInvocationResult> proceed(ModelInvocationContext context);
}
