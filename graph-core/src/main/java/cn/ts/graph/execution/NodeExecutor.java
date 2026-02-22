package cn.ts.graph.execution;

import cn.ts.graph.GraphResponse;
import cn.ts.graph.GraphRunnerContext;
import cn.ts.graph.NodeOutput;
import cn.ts.graph.record.DefaultExecutionRecordManager;
import cn.ts.graph.record.ExecutionRecordService;
import cn.ts.graph.record.ExecutionRecordManager;
import cn.ts.graph.node.Node;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 节点执行器：负责节点执行编排。
 */
public class NodeExecutor {

    private final ExecutionRecordManager recordManager;
    private final NodeInterruptionService interruptionService;
    private final NodeActionInvoker actionInvoker;
    private final NodeResultAssembler resultAssembler;

    public NodeExecutor() {
        this(new DefaultExecutionRecordManager());
    }

    public NodeExecutor(ExecutionRecordManager recordManager) {
        this.recordManager = recordManager != null ? recordManager : new DefaultExecutionRecordManager();
        ExecutionRecordService executionRecordService = new ExecutionRecordService(this.recordManager);
        this.interruptionService = new NodeInterruptionService();
        this.actionInvoker = new NodeActionInvoker();
        this.resultAssembler = new NodeResultAssembler(executionRecordService);
    }

    public ExecutionRecordManager getRecordManager() {
        return recordManager;
    }

    public Flux<GraphResponse<NodeOutput>> execute(Node node, GraphRunnerContext context) {
        var interruption = interruptionService.detect(node, context);
        if (interruption.isPresent()) {
            return Flux.just(interruption.get());
        }

        CompletableFuture<Map<String, Object>> future = actionInvoker.invoke(node, context);
        return Mono.fromFuture(future)
                .flatMapMany(updates -> resultAssembler.assemble(node, context, updates))
                .onErrorResume(error -> Flux.just(GraphResponse.error(error)));
    }

    public static NodeExecutor create() {
        return new NodeExecutor();
    }

    public static NodeExecutor create(ExecutionRecordManager recordManager) {
        return new NodeExecutor(recordManager);
    }
}
