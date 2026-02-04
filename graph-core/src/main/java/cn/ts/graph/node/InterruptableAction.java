package cn.ts.graph.node;

import cn.ts.graph.checkpoint.InterruptionMetadata;
import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.state.State;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 可中断动作接口
 * <p>
 * 独立的中断检测接口，支持在执行前检查是否需要中断
 * 用于实现人工审批、等待外部输入等场景
 * </p>
 * <p>
 * 注意：此接口不继承 AsyncNodeAction，是独立的中断检测接口
 * 可以与 NodeAction、AsyncNodeAction 或 AsyncNodeActionWithConfig 配合使用
 * </p>
 *
 * @author tianshuo
 */
public interface InterruptableAction {

    /**
     * 检查是否需要中断执行
     * <p>
     * 如果返回 Optional.empty()，则正常执行节点动作
     * 如果返回包含 InterruptionMetadata 的 Optional，则保存检查点并中断执行
     * </p>
     *
     * @param nodeId 节点ID
     * @param state  当前状态
     * @param config 运行配置
     * @return 如果需要中断则返回包含 InterruptionMetadata 的 Optional，否则返回空 Optional
     */
    Optional<InterruptionMetadata> interrupt(
            String nodeId,
            State state,
            RunnableConfig config);

    /**
     * 从中断恢复执行
     * <p>
     * 当用户提供反馈后，使用此方法继续执行
     * </p>
     *
     * @param state        当前状态
     * @param config       运行配置
     * @param feedbackData 反馈数据
     * @return CompletableFuture 包含状态更新
     */
    default CompletableFuture<Map<String, Object>> resume(
            State state,
            RunnableConfig config,
            Map<String, Object> feedbackData) {
        return CompletableFuture.completedFuture(Map.of());
    }
}
