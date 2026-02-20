package cn.ts.agent.interceptor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Executes model interceptors in order.
 */
public final class ModelInterceptorChain implements ModelInvoker {

    private final List<ModelInterceptor> interceptors;
    private final ModelInvoker terminalInvoker;
    private final int index;

    private ModelInterceptorChain(
            List<ModelInterceptor> interceptors,
            ModelInvoker terminalInvoker,
            int index) {
        this.interceptors = interceptors;
        this.terminalInvoker = terminalInvoker;
        this.index = index;
    }

    public static ModelInterceptorChain create(
            List<ModelInterceptor> interceptors,
            ModelInvoker terminalInvoker) {
        Objects.requireNonNull(terminalInvoker, "terminalInvoker cannot be null");
        List<ModelInterceptor> sorted = sortInterceptors(interceptors);
        return new ModelInterceptorChain(sorted, terminalInvoker, 0);
    }

    @Override
    public CompletableFuture<ModelInvocationResult> proceed(ModelInvocationContext context) {
        if (index >= interceptors.size()) {
            return terminalInvoker.proceed(context);
        }
        ModelInterceptor current = interceptors.get(index);
        ModelInterceptorChain nextChain = new ModelInterceptorChain(interceptors, terminalInvoker, index + 1);
        return current.intercept(context, nextChain);
    }

    private static List<ModelInterceptor> sortInterceptors(List<ModelInterceptor> interceptors) {
        if (interceptors == null || interceptors.isEmpty()) {
            return List.of();
        }
        List<InterceptorRegistration> registrations = new ArrayList<>();
        for (int i = 0; i < interceptors.size(); i++) {
            ModelInterceptor interceptor = Objects.requireNonNull(interceptors.get(i), "interceptor cannot be null");
            registrations.add(new InterceptorRegistration(interceptor, i));
        }
        registrations.sort(
                Comparator.comparingInt((InterceptorRegistration r) -> r.interceptor().getOrder())
                        .thenComparingInt(InterceptorRegistration::registrationIndex)
        );

        List<ModelInterceptor> sorted = new ArrayList<>(registrations.size());
        for (InterceptorRegistration registration : registrations) {
            sorted.add(registration.interceptor());
        }
        return List.copyOf(sorted);
    }

    private record InterceptorRegistration(ModelInterceptor interceptor, int registrationIndex) {
    }
}
