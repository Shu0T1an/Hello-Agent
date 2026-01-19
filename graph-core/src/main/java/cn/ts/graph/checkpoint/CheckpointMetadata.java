package cn.ts.graph.checkpoint;

import java.util.Map;
import java.util.Objects;

/**
 * 检查点元数据
 * <p>
 * 包含检查点的来源、父检查点ID和步骤信息
 * 参考 Spring AI Alibaba 的 CheckpointMetadata 设计
 * </p>
 *
 * @author tianshuo
 */
public class CheckpointMetadata {

    private final String source;
    private final String parentId;
    private final Map<String, Object> stepInfo;

    private CheckpointMetadata(Builder builder) {
        this.source = builder.source;
        this.parentId = builder.parentId;
        this.stepInfo = builder.stepInfo != null ? Map.copyOf(builder.stepInfo) : Map.of();
    }

    /**
     * 创建一个新的构建器
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取来源
     *
     * @return 来源: auto/manual/error/restore
     */
    public String getSource() {
        return source;
    }

    /**
     * 获取父检查点ID
     *
     * @return 父检查点ID，可能为 null
     */
    public String getParentId() {
        return parentId;
    }

    /**
     * 获取步骤信息
     *
     * @return 步骤信息
     */
    public Map<String, Object> getStepInfo() {
        return stepInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckpointMetadata that = (CheckpointMetadata) o;
        return Objects.equals(source, that.source)
                && Objects.equals(parentId, that.parentId)
                && Objects.equals(stepInfo, that.stepInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, parentId, stepInfo);
    }

    @Override
    public String toString() {
        return "CheckpointMetadata{" +
                "source='" + source + '\'' +
                ", parentId='" + parentId + '\'' +
                ", stepInfo=" + stepInfo +
                '}';
    }

    /**
     * 构建器
     */
    public static class Builder {
        private String source = "manual";
        private String parentId = null;
        private Map<String, Object> stepInfo = Map.of();

        /**
         * 设置来源
         *
         * @param source 来源: auto/manual/error/restore
         * @return this
         */
        public Builder source(String source) {
            this.source = source;
            return this;
        }

        /**
         * 设置父检查点ID
         *
         * @param parentId 父检查点ID
         * @return this
         */
        public Builder parentId(String parentId) {
            this.parentId = parentId;
            return this;
        }

        /**
         * 设置步骤信息
         *
         * @param stepInfo 步骤信息
         * @return this
         */
        public Builder stepInfo(Map<String, Object> stepInfo) {
            this.stepInfo = stepInfo;
            return this;
        }

        /**
         * 构建 CheckpointMetadata
         *
         * @return CheckpointMetadata 实例
         */
        public CheckpointMetadata build() {
            return new CheckpointMetadata(this);
        }
    }
}
