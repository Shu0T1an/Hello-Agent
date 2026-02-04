package cn.ts.graph;

import cn.ts.graph.checkpoint.InterruptionMetadata;

import java.util.Objects;

/**
 * 中断输出
 * <p>
 * 用于封装执行中断时的相关信息，包括中断元数据、检查点ID和线程ID
 * </p>
 *
 * @param metadata    中断元数据
 * @param checkpointId 检查点ID
 * @param threadId     线程ID（会话ID），用于恢复时查找检查点
 * @author tianshuo
 */
public record InterruptionOutput(
        InterruptionMetadata metadata,
        String checkpointId,
        String threadId
) {

    public InterruptionOutput {
        Objects.requireNonNull(metadata, "InterruptionMetadata cannot be null");
        Objects.requireNonNull(checkpointId, "CheckpointId cannot be null");
        // threadId 可以为 null（如果配置中没有设置）
    }

    /**
     * 创建中断输出（不包含 threadId）
     *
     * @param metadata    中断元数据
     * @param checkpointId 检查点ID
     * @return InterruptionOutput 实例
     * @deprecated 使用 {@link #of(InterruptionMetadata, String, String)} 代替
     */
    @Deprecated
    public static InterruptionOutput of(InterruptionMetadata metadata, String checkpointId) {
        return new InterruptionOutput(metadata, checkpointId, null);
    }

    /**
     * 创建中断输出（包含 threadId）
     *
     * @param metadata    中断元数据
     * @param checkpointId 检查点ID
     * @param threadId     线程ID（会话ID）
     * @return InterruptionOutput 实例
     */
    public static InterruptionOutput of(InterruptionMetadata metadata, String checkpointId, String threadId) {
        return new InterruptionOutput(metadata, checkpointId, threadId);
    }
}
