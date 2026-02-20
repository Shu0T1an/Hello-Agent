package cn.ts.agent.interceptor;

import java.util.concurrent.CompletableFuture;

/**
 * Around interceptor for model invocation.
 */
public interface ModelInterceptor {

    String getName();

    default int getOrder() {
        return 0;
    }

    CompletableFuture<ModelInvocationResult> intercept(
            ModelInvocationContext context,
            ModelInvoker next);
}
