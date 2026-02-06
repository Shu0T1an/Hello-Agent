package cn.ts.graph.observation;

import cn.ts.graph.config.RunnableConfig;

import java.util.Map;

/**
 * 图生命周期监听器接口
 * <p>
 * 用于监听图执行的各个阶段，实现可观测性、日志记录、性能监控等功能。
 * 参考 Spring AI Alibaba 的 GraphLifecycleListener 设计。
 * </p>
 *
 * @author tianshuo
 */
public interface GraphLifecycleListener {

    String EXECUTION_ID_KEY = "executionId";

    /**
     * 图开始执行时触发（START 节点）
     *
     * @param nodeId 节点ID
     * @param state  当前状态
     * @param config 运行配置
     */
    default void onStart(String nodeId, Map<String, Object> state, RunnableConfig config) {
    }

    /**
     * 节点执行前触发
     *
     * @param nodeId    节点ID
     * @param state     当前状态
     * @param config    运行配置
     * @param startTime 开始时间戳
     */
    default void before(String nodeId, Map<String, Object> state, RunnableConfig config, long startTime) {
    }

    /**
     * 节点执行后触发
     *
     * @param nodeId  节点ID
     * @param state   当前状态
     * @param config  运行配置
     * @param endTime 结束时间戳
     */
    default void after(String nodeId, Map<String, Object> state, RunnableConfig config, long endTime) {
    }

    /**
     * 图完成时触发（END 节点）
     *
     * @param nodeId 节点ID
     * @param state  当前状态
     * @param config 运行配置
     */
    default void onComplete(String nodeId, Map<String, Object> state, RunnableConfig config) {
    }

    /**
     * 发生错误时触发
     *
     * @param nodeId 节点ID
     * @param state  当前状态
     * @param error  错误信息
     * @param config 运行配置
     */
    default void onError(String nodeId, Map<String, Object> state, Throwable error, RunnableConfig config) {
    }
}
