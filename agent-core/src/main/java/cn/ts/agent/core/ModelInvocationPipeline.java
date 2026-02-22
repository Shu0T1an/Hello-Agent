package cn.ts.agent.core;

import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.hook.HookPosition;
import cn.ts.graph.hook.JumpTo;
import cn.ts.graph.hook.ModelHook;
import cn.ts.graph.node.NodeAction;
import cn.ts.graph.state.State;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

class ModelInvocationPipeline {

    NodeAction toNodeAction(ModelHook hook, HookPosition position) {
        return state -> execute(hook, position, state);
    }

    private Map<String, Object> execute(ModelHook hook, HookPosition position, State state) {
        try {
            RunnableConfig config = RunnableConfig.defaultConfig();
            CompletableFuture<Map<String, Object>> future = position == HookPosition.BEFORE_MODEL
                    ? hook.beforeModel(state, config)
                    : hook.afterModel(state, config);

            Map<String, Object> result = future.get();
            if (result.containsKey("jump_to") && result.get("jump_to") instanceof JumpTo) {
                return result;
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Hook execution failed: " + hook.getName(), e);
        }
    }
}
