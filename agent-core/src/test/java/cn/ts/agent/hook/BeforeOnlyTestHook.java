package cn.ts.agent.hook;

import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.hook.HookPosition;
import cn.ts.graph.hook.HookPositions;
import cn.ts.graph.hook.ModelHook;
import cn.ts.graph.state.State;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 只支持 BEFORE_MODEL 的测试 Hook
 *
 * @author tianshuo
 */
@HookPositions(HookPosition.BEFORE_MODEL)
public class BeforeOnlyTestHook extends ModelHook {

    private final String prefix;

    public BeforeOnlyTestHook(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getName() {
        return "BeforeOnlyTestHook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(State state, RunnableConfig config) {
        return CompletableFuture.supplyAsync(() -> Map.of());
    }

    public static BeforeOnlyTestHook create(String prefix) {
        return new BeforeOnlyTestHook(prefix);
    }
}
