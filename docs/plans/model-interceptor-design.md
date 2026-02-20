# Model Interceptor Design

## Goal
Add an independent model interceptor chain for `LLMNode` without changing existing Hook semantics.

## Scope
- Interception scope: model invocation only.
- Supports around-style interception: before / after / short-circuit.
- Default error policy: fail-fast.
- Streaming support: invocation-level only (no chunk-level interception).

## Public API
Package: `agent-core/src/main/java/cn/ts/agent/interceptor/`

- `ModelInterceptor`
  - `String getName()`
  - `default int getOrder()`
  - `CompletableFuture<ModelInvocationResult> intercept(ModelInvocationContext context, ModelInvoker next)`
- `ModelInvoker`
  - `CompletableFuture<ModelInvocationResult> proceed(ModelInvocationContext context)`
- `ModelInvocationContext`
  - fields: `State state`, `RunnableConfig config`, `ChatModelRequest request`, `boolean streaming`, `Map<String,Object> attributes`
  - immutable methods: `withRequest(...)`, `withAttribute(...)`
- `ModelInvocationResult`
  - field: `Map<String,Object> updates`
  - static factory: `of(updates)`
- `ModelInterceptorChain`
  - sorts and executes interceptors, then calls terminal model invoker.

## Integration
- `LLMNode` now supports:
  - `apply(State state)` (backward compatible)
  - `apply(State state, RunnableConfig config)` (config-aware path)
  - terminal invoker reuses existing `applyNonStreaming` / `applyStreaming`.
- `ReactAgent.Builder` now supports:
  - `modelInterceptors(List<ModelInterceptor>)`
  - injection into `LLMNode`.
- `NodeExecutor` now recognizes `NodeActionWithConfig` in stream execution path.

## Sequence
1. Build `ChatModelRequest` from state.
2. Create `ModelInvocationContext`.
3. Run interceptor chain.
4. Terminal invoker executes model call:
  - non-streaming -> `messages` + `chat_response`
  - streaming -> `llm_stream`
5. Return merged updates.

## Execution Semantics
- Interceptor order: `getOrder()` ascending.
- Same order: keep registration order.
- Short-circuit: interceptor may return result without calling `next`.
- Exception: immediate failure (fail-fast).

## Example: Logging Interceptor
```java
public class LoggingInterceptor implements ModelInterceptor {
    @Override
    public String getName() { return "logging"; }

    @Override
    public CompletableFuture<ModelInvocationResult> intercept(
            ModelInvocationContext context, ModelInvoker next) {
        long start = System.currentTimeMillis();
        return next.proceed(context).thenApply(result -> {
            long cost = System.currentTimeMillis() - start;
            System.out.println("Model call cost: " + cost + "ms");
            return result;
        });
    }
}
```

## Example: Prompt Enrichment Interceptor
```java
public class PromptEnrichmentInterceptor implements ModelInterceptor {
    @Override
    public String getName() { return "prompt-enrichment"; }

    @Override
    public CompletableFuture<ModelInvocationResult> intercept(
            ModelInvocationContext context, ModelInvoker next) {
        State enriched = new MapState(context.state().data());
        enriched.merge(Map.of("input", "[enriched] " + context.state().value("input").orElse("")));
        ChatModelRequest rewritten = ChatModelRequest.builder(enriched).build();
        return next.proceed(context.withRequest(rewritten));
    }
}
```
