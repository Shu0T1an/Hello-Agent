package cn.ts.agent.hook;

import java.util.Map;
import java.util.Objects;

/**
 * 工具反馈类
 * <p>
 * 记录用户对工具调用的反馈
 * </p>
 *
 * @author tianshuo
 */
public class ToolFeedback {

    private final String id;
    private final String name;
    private final Map<String, Object> arguments;
    private final String description;
    private FeedbackResult result;
    private Map<String, Object> modifiedArguments;

    private ToolFeedback(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "Tool feedback id cannot be null");
        this.name = Objects.requireNonNull(builder.name, "Tool name cannot be null");
        this.arguments = builder.arguments != null ? Map.copyOf(builder.arguments) : Map.of();
        this.description = builder.description != null ? builder.description : "";
        this.result = builder.result != null ? builder.result : FeedbackResult.APPROVED;
        this.modifiedArguments = builder.modifiedArguments != null ? builder.modifiedArguments : Map.of();
    }

    /**
     * 创建构建器
     *
     * @param id   工具调用ID
     * @param name 工具名称
     * @return Builder 实例
     */
    public static Builder builder(String id, String name) {
        return new Builder(id, name);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public String getDescription() {
        return description;
    }

    public FeedbackResult getResult() {
        return result;
    }

    public Map<String, Object> getModifiedArguments() {
        return modifiedArguments;
    }

    public void setResult(FeedbackResult result) {
        this.result = Objects.requireNonNull(result, "Feedback result cannot be null");
    }

    public void setModifiedArguments(Map<String, Object> modifiedArguments) {
        this.modifiedArguments = modifiedArguments;
    }

    /**
     * 获取最终要执行的参数
     * <p>
     * 如果结果是 MODIFIED，返回修改后的参数；否则返回原始参数
     * </p>
     *
     * @return 最终参数
     */
    public Map<String, Object> getFinalArguments() {
        return result == FeedbackResult.MODIFIED ? modifiedArguments : arguments;
    }

    /**
     * 检查是否应该执行工具
     *
     * @return 如果应该执行返回 true
     */
    public boolean shouldExecute() {
        return result == FeedbackResult.APPROVED || result == FeedbackResult.MODIFIED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolFeedback that = (ToolFeedback) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ToolFeedback{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", result=" + result +
                '}';
    }

    /**
     * 构建器
     */
    public static class Builder {
        private final String id;
        private final String name;
        private Map<String, Object> arguments;
        private String description;
        private FeedbackResult result;
        private Map<String, Object> modifiedArguments;

        private Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder result(FeedbackResult result) {
            this.result = result;
            return this;
        }

        public Builder modifiedArguments(Map<String, Object> modifiedArguments) {
            this.modifiedArguments = modifiedArguments;
            return this;
        }

        public ToolFeedback build() {
            return new ToolFeedback(this);
        }
    }
}
