package cn.ts.agent.hook;

import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.hook.HookPosition;
import cn.ts.graph.hook.HookPositions;
import cn.ts.graph.hook.ModelHook;
import cn.ts.graph.state.State;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 第二个测试 Hook，用于多 Hook 测试
 *
 * @author tianshuo
 */
@HookPositions({HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL})
public class SecondTestHook extends ModelHook {

    private final String prefix;

    public SecondTestHook(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getName() {
        return "SecondTestHook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(State state, RunnableConfig config) {
        return CompletableFuture.supplyAsync(() -> Map.of());
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(State state, RunnableConfig config) {
        return CompletableFuture.supplyAsync(() -> Map.of());
    }

    public static SecondTestHook create(String prefix) {
        return new SecondTestHook(prefix);
    }
}
