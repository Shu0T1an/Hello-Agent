package cn.ts.graph.execution;

import cn.ts.graph.GraphResponse;
import cn.ts.graph.GraphRunnerContext;
import cn.ts.graph.InterruptionOutput;
import cn.ts.graph.NodeOutput;
import cn.ts.graph.checkpoint.InterruptionMetadata;
import cn.ts.graph.node.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

class NodeInterruptionService {

    private static final Logger logger = LoggerFactory.getLogger(NodeInterruptionService.class);

    Optional<GraphResponse<NodeOutput>> detect(Node node, GraphRunnerContext context) {
        if (!node.isInterruptable() || node.interruptableAction() == null) {
            return Optional.empty();
        }

        Optional<InterruptionMetadata> interruption = node.interruptableAction().interrupt(
                node.id(),
                context.getOverallState(),
                context.getConfig()
        );
        if (interruption.isEmpty()) {
            return Optional.empty();
        }

        logger.info("节点 {} 执行被中断", node.id());

        String threadId = context.getConfig().threadId();
        String checkpointId = createCheckpointOnInterruption(context, node.id());

        InterruptionOutput interruptionOutput = InterruptionOutput.of(
                interruption.get(),
                checkpointId != null ? checkpointId : "",
                threadId
        );
        Map<String, Object> interruptData = Map.of(
                "interruption", interruptionOutput,
                "interrupted", true
        );
        NodeOutput interruptOutput = NodeOutput.of(
                node.id(),
                interruptData,
                context.getOverallState()
        );
        return Optional.of(GraphResponse.interruption(interruptOutput));
    }

    private String createCheckpointOnInterruption(GraphRunnerContext context, String nodeId) {
        return context.getCheckpointManager()
                .map(manager -> {
                    try {
                        String checkpointId = manager.createCheckpoint(context, "interruption");
                        logger.info("中断时自动创建检查点: nodeId={}, checkpointId={}", nodeId, checkpointId);
                        return checkpointId;
                    } catch (Exception e) {
                        logger.warn("创建中断检查点失败: nodeId={}, error={}", nodeId, e.getMessage());
                        return null;
                    }
                })
                .orElse(null);
    }
}
