package cn.ts.agent.interceptor;

import cn.ts.agent.model.ChatModelRequest;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelInterceptorChainTest {

    @Test
    void executesInOrderAndKeepsRegistrationOrderForSameOrder() throws Exception {
        State state = new MapState(Map.of("input", "hello"));
        ModelInvocationContext context = ModelInvocationContext.of(
                state,
                RunnableConfig.defaultConfig(),
                ChatModelRequest.builder(state).build(),
                false
        );

        List<String> steps = new ArrayList<>();
        ModelInterceptor first = new TestInterceptor("first", 1, steps);
        ModelInterceptor second = new TestInterceptor("second", 1, steps);
        ModelInterceptor third = new TestInterceptor("third", 2, steps);

        ModelInvoker terminal = ctx -> {
            steps.add("terminal");
            return CompletableFuture.completedFuture(ModelInvocationResult.of(Map.of("ok", true)));
        };

        ModelInvocationResult result = ModelInterceptorChain.create(List.of(third, second, first), terminal)
                .proceed(context)
                .get();

        assertEquals(Map.of("ok", true), result.updates());
        assertEquals(
                List.of(
                        "before-second", "before-first", "before-third", "terminal",
                        "after-third", "after-first", "after-second"
                ),
                steps
        );
    }

    @Test
    void supportsShortCircuit() throws Exception {
        State state = new MapState(Map.of("input", "hello"));
        ModelInvocationContext context = ModelInvocationContext.of(
                state,
                RunnableConfig.defaultConfig(),
                ChatModelRequest.builder(state).build(),
                false
        );

        AtomicBoolean terminalCalled = new AtomicBoolean(false);

        ModelInterceptor shortCircuit = new ModelInterceptor() {
            @Override
            public String getName() {
                return "short-circuit";
            }

            @Override
            public CompletableFuture<ModelInvocationResult> intercept(ModelInvocationContext context, ModelInvoker next) {
                return CompletableFuture.completedFuture(ModelInvocationResult.of(Map.of("short", true)));
            }
        };

        ModelInvoker terminal = ctx -> {
            terminalCalled.set(true);
            return CompletableFuture.completedFuture(ModelInvocationResult.of(Map.of()));
        };

        ModelInvocationResult result = ModelInterceptorChain.create(List.of(shortCircuit), terminal)
                .proceed(context)
                .get();

        assertEquals(Map.of("short", true), result.updates());
        assertTrue(!terminalCalled.get());
    }

    @Test
    void failsFastWhenInterceptorThrows() {
        State state = new MapState(Map.of("input", "hello"));
        ModelInvocationContext context = ModelInvocationContext.of(
                state,
                RunnableConfig.defaultConfig(),
                ChatModelRequest.builder(state).build(),
                false
        );

        ModelInterceptor bad = new ModelInterceptor() {
            @Override
            public String getName() {
                return "bad";
            }

            @Override
            public CompletableFuture<ModelInvocationResult> intercept(ModelInvocationContext context, ModelInvoker next) {
                throw new IllegalStateException("boom");
            }
        };

        assertThrows(
                IllegalStateException.class,
                () -> ModelInterceptorChain.create(List.of(bad), c -> CompletableFuture.completedFuture(ModelInvocationResult.of(Map.of())))
                        .proceed(context)
        );
    }

    private record TestInterceptor(String name, int order, List<String> steps) implements ModelInterceptor {
        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public CompletableFuture<ModelInvocationResult> intercept(ModelInvocationContext context, ModelInvoker next) {
            steps.add("before-" + name);
            return next.proceed(context).thenApply(result -> {
                steps.add("after-" + name);
                return result;
            });
        }
    }
}
