package cn.ts.graph;

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
        NodeAction action = node.action();
        RunnableConfig config = context.getConfig();

        if (action instanceof AsyncNodeActionWithConfig actionWithConfig) {
            return actionWithConfig.applyAsync(context.getOverallState(), config);
        }

        if (action instanceof AsyncNodeAction asyncAction) {
            AsyncNodeActionWithConfig wrapped = AsyncNodeActionWithConfig.from(asyncAction);
            return wrapped.applyAsync(context.getOverallState(), config);
        }

        NodeActionWithConfig wrapped = NodeActionWithConfig.from(action);
        return wrapped.applyAsync(context.getOverallState(), config);
    }
}
