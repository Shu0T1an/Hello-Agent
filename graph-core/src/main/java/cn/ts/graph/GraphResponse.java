package cn.ts.graph;

import java.util.Map;
import java.util.Objects;

/**
 * 统一响应格式
 * <p>
 * 用于包装节点执行结果，支持普通结果和流式结果
 * 参考 Spring AI Alibaba 的 GraphResponse 设计
 * </p>
 *
 * @param <T> 响应数据的类型
 * @author tianshuo
 */
public class GraphResponse<T> {

    private final String nodeId;
    private final T data;
    private final boolean isStream;
    private final Throwable error;
    private final boolean isComplete;

    private GraphResponse(String nodeId, T data, boolean isStream, Throwable error, boolean isComplete) {
        this.nodeId = nodeId;
        this.data = data;
        this.isStream = isStream;
        this.error = error;
        this.isComplete = isComplete;
    }

    /**
     * 创建一个正常的响应
     *
     * @param nodeId 节点ID
     * @param data   响应数据
     * @param <T>    数据类型
     * @return GraphResponse 实例
     */
    public static <T> GraphResponse<T> of(String nodeId, T data) {
        return new GraphResponse<>(nodeId, data, false, null, true);
    }

    /**
     * 创建一个流式响应
     *
     * @param nodeId 节点ID
     * @param data   流数据块
     * @param <T>    数据类型
     * @return GraphResponse 实例
     */
    public static <T> GraphResponse<T> stream(String nodeId, T data) {
        return new GraphResponse<>(nodeId, data, true, null, false);
    }

    /**
     * 创建一个流完成响应
     *
     * @param nodeId 节点ID
     * @param <T>    数据类型
     * @return GraphResponse 实例
     */
    public static <T> GraphResponse<T> streamComplete(String nodeId) {
        return new GraphResponse<>(nodeId, null, true, null, true);
    }

    /**
     * 创建一个流完成响应（带数据）
     * <p>
     * 用于流式输出的最终完成事件，携带完整数据（如聚合后的完整文本）
     * </p>
     *
     * @param nodeId 节点ID
     * @param data   完整数据
     * @param <T>    数据类型
     * @return GraphResponse 实例
     */
    public static <T> GraphResponse<T> streamCompleteWithData(String nodeId, T data) {
        return new GraphResponse<>(nodeId, data, true, null, true);
    }

    /**
     * 创建一个图完成响应（整个流程完成）
     *
     * @param <T> 数据类型
     * @return GraphResponse 实例
     */
    public static <T> GraphResponse<T> complete() {
        return new GraphResponse<>(null, null, false, null, true);
    }

    /**
     * 创建一个错误响应
     *
     * @param error 错误信息
     * @param <T>   数据类型
     * @return GraphResponse 实例
     */
    public static <T> GraphResponse<T> error(Throwable error) {
        return new GraphResponse<>(null, null, false, error, true);
    }

    /**
     * 获取节点ID
     *
     * @return 节点ID
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * 获取响应数据
     *
     * @return 响应数据
     */
    public T getData() {
        return data;
    }

    /**
     * 是否为流式响应
     *
     * @return true 如果是流式响应
     */
    public boolean isStream() {
        return isStream;
    }

    /**
     * 是否已完成
     *
     * @return true 如果已完成
     */
    public boolean isComplete() {
        return isComplete;
    }

    /**
     * 是否为流式完成信号
     * <p>
     * 当为 true 时，表示节点流式输出已完成
     * </p>
     *
     * @return true 如果是流式完成信号
     */
    public boolean isStreamEnd() {
        return isStream && isComplete;
    }

    /**
     * 是否为正常完成
     * <p>
     * 当为 true 时，表示节点正常执行完成（非流式）
     * </p>
     *
     * @return true 如果是正常完成
     */
    public boolean isNormalComplete() {
        return !isStream && isComplete;
    }

    /**
     * 是否有错误
     *
     * @return true 如果有错误
     */
    public boolean hasError() {
        return error != null;
    }

    /**
     * 获取错误信息
     *
     * @return 错误信息，如果没有错误则返回 null
     */
    public Throwable getError() {
        return error;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GraphResponse<?> that = (GraphResponse<?>) o;
        return isStream == that.isStream && isComplete == that.isComplete
                && Objects.equals(nodeId, that.nodeId)
                && Objects.equals(data, that.data)
                && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, data, isStream, error, isComplete);
    }

    @Override
    public String toString() {
        return "GraphResponse{" +
                "nodeId='" + nodeId + '\'' +
                ", data=" + data +
                ", isStream=" + isStream +
                ", isComplete=" + isComplete +
                ", error=" + error +
                '}';
    }
}
