package cn.ts.agent.hook;

import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.hook.HookPosition;
import cn.ts.graph.hook.HookPositions;
import cn.ts.graph.hook.ModelHook;
import cn.ts.graph.state.State;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 只支持 AFTER_MODEL 的测试 Hook
 *
 * @author tianshuo
 */
@HookPositions(HookPosition.AFTER_MODEL)
public class AfterOnlyTestHook extends ModelHook {

    private final String prefix;

    public AfterOnlyTestHook(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getName() {
        return "AfterOnlyTestHook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(State state, RunnableConfig config) {
        return CompletableFuture.supplyAsync(() -> Map.of());
    }

    public static AfterOnlyTestHook create(String prefix) {
        return new AfterOnlyTestHook(prefix);
    }
}
