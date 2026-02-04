package cn.ts.agent.hook;

import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.hook.HookPosition;
import cn.ts.graph.hook.HookPositions;
import cn.ts.graph.hook.ModelHook;
import cn.ts.graph.state.State;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 简单的测试 Hook
 * <p>
 * 用于测试 Hook 机制的基本功能
 * </p>
 *
 * @author tianshuo
 */
@HookPositions({HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL})
public class SimpleTestHook extends ModelHook {

    private final String prefix;
    private int beforeCallCount = 0;
    private int afterCallCount = 0;

    public SimpleTestHook(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getName() {
        return "SimpleTestHook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(State state, RunnableConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            beforeCallCount++;
            return Map.of();
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(State state, RunnableConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            afterCallCount++;
            return Map.of();
        });
    }

    public int getBeforeCallCount() {
        return beforeCallCount;
    }

    public int getAfterCallCount() {
        return afterCallCount;
    }

    public static SimpleTestHook create(String prefix) {
        return new SimpleTestHook(prefix);
    }
}
