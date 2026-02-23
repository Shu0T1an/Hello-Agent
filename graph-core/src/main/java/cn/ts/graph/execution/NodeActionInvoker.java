package cn.ts.graph.execution;

import cn.ts.graph.GraphRunnerContext;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.node.AsyncNodeAction;
import cn.ts.graph.node.AsyncNodeActionWithConfig;
import cn.ts.graph.node.Node;
import cn.ts.graph.node.NodeAction;
import cn.ts.graph.node.NodeActionWithConfig;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

class NodeActionInvoker {

    CompletableFuture<Map<String, Object>> invoke(Node node, GraphRunnerContext context) {
        RunnableConfig config = context.getConfig();
        if (node.interruptableAction() instanceof AsyncNodeActionWithConfig interruptableWithConfig) {
            return interruptableWithConfig.applyAsync(context.getOverallState(), config);
        }

        NodeAction action = node.action();

        if (action instanceof AsyncNodeActionWithConfig actionWithConfig) {
            return actionWithConfig.applyAsync(context.getOverallState(), config);
        }

        if (action instanceof AsyncNodeAction asyncAction) {
            AsyncNodeActionWithConfig wrapped = AsyncNodeActionWithConfig.from(asyncAction);
            return wrapped.applyAsync(context.getOverallState(), config);
        }

        if (action instanceof NodeActionWithConfig actionWithConfig) {
            return actionWithConfig.applyAsync(context.getOverallState(), config);
        }

        NodeActionWithConfig wrapped = NodeActionWithConfig.from(action);
        return wrapped.applyAsync(context.getOverallState(), config);
    }
}
