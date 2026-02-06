package cn.ts.graph.observation;

import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.observation.graph.GraphObservationContext;
import cn.ts.graph.observation.metric.ObservationMetricAttributes;
import cn.ts.graph.observation.metric.ObservationMetricNames;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可观测性生命周期监听器
 * <p>
 * 基于 Micrometer Observation API 实现图执行过程的可观测性。
 * 支持 ReAct 循环场景中同一节点多次执行的情况。
 * 参考 Spring AI Alibaba 的 GraphObservationLifecycleListener 设计。
 * </p>
 *
 * @author tianshuo
 */
public class GraphObservationLifecycleListener implements GraphLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(GraphObservationLifecycleListener.class);

    private final ObservationRegistry observationRegistry;

    // 存储 executionId -> GraphObservation 的映射
    private final Map<String, Observation> graphObservations = new ConcurrentHashMap<>();

    // 存储 executionId -> GraphObservationContext 的映射
    private final Map<String, GraphObservationContext> graphContexts = new ConcurrentHashMap<>();

    // 存储 executionId -> 节点执行次数的映射
    private final Map<String, Map<String, AtomicLong>> nodeExecutionCounts = new ConcurrentHashMap<>();

    // 存储 executionId -> (nodeId_executionNumber -> Observation) 的映射
    private final Map<String, Map<String, Observation>> nodeObservations = new ConcurrentHashMap<>();

    /**
     * 创建可观测性监听器
     *
     * @param observationRegistry 观测注册表
     */
    public GraphObservationLifecycleListener(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public void onStart(String nodeId, Map<String, Object> state, RunnableConfig config) {
        String executionId = getExecutionId(state);


        if (executionId == null) {
            log.warn("No executionId in state, skipping observation. State keys: {}", state != null ? state.keySet() : "null");
            return;
        }


        // 创建图级别 Observation
        Observation graphObs = Observation.createNotStarted(
                ObservationMetricNames.GRAPH_EXECUTION,
                () -> new GraphObservationContext("graph-execution", state),
                observationRegistry
        );

        // 添加低基数标签
        graphObs.lowCardinalityKeyValue(ObservationMetricAttributes.KIND.value(), "graph");
        graphObs.lowCardinalityKeyValue(ObservationMetricAttributes.EXECUTION_ID.value(), executionId);
        graphObs.lowCardinalityKeyValue(ObservationMetricAttributes.GRAPH_NAME.value(), "graph-execution");

        // 添加输入状态（高基数，截断）
        String inputState = dumpState(state);
        graphObs.highCardinalityKeyValue(ObservationMetricAttributes.INPUT_STATE.value(), inputState);

        // 启动观测
        graphObs.start();

        // 保存到缓存
        graphObservations.put(executionId, graphObs);
        graphContexts.put(executionId, (GraphObservationContext) graphObs.getContext());

        // 初始化节点执行计数器和 Observation 存储
        nodeExecutionCounts.put(executionId, new ConcurrentHashMap<>());
        nodeObservations.put(executionId, new ConcurrentHashMap<>());
    }

    @Override
    public void before(String nodeId, Map<String, Object> state, RunnableConfig config, long startTime) {
        String executionId = getExecutionId(state);
        if (executionId == null) {
            return;
        }

        Observation graphObs = graphObservations.get(executionId);
        if (graphObs == null) {
            log.debug("No graph observation found for node: {}", nodeId);
            return;
        }

        // 递增节点执行次数
        Map<String, AtomicLong> counters = nodeExecutionCounts.get(executionId);
        if (counters == null) {
            return;
        }
        counters.computeIfAbsent(nodeId, k -> new AtomicLong(0)).incrementAndGet();
        long executionNumber = counters.get(nodeId).get();



        // 创建节点级别 Observation，建立父子关系
        Observation nodeObs = Observation.createNotStarted(
                ObservationMetricNames.GRAPH_NODE + "." + sanitizeMetricName(nodeId),
                observationRegistry
        ).parentObservation(graphObs);

        // 添加节点属性
        nodeObs.lowCardinalityKeyValue(ObservationMetricAttributes.KIND.value(), "node");
        nodeObs.lowCardinalityKeyValue(ObservationMetricAttributes.NODE_NAME.value(), nodeId);
        // 添加执行次数标签（用于区分循环执行）
        nodeObs.lowCardinalityKeyValue("hello_agent.graph.node.execution", String.valueOf(executionNumber));

        // 记录输入状态
        String inputState = dumpState(state);
        nodeObs.highCardinalityKeyValue(ObservationMetricAttributes.INPUT_STATE.value(), inputState);

        // 启动观测（开始计时）
        nodeObs.start();


        // 保存到 map，以便在 after() 中停止
        String key = nodeId + "_" + executionNumber;
        nodeObservations.get(executionId).put(key, nodeObs);
    }

    @Override
    public void after(String nodeId, Map<String, Object> state, RunnableConfig config, long endTime) {
        String executionId = getExecutionId(state);
        if (executionId == null) {
            return;
        }

        Map<String, Observation> observations = nodeObservations.get(executionId);
        if (observations == null) {
            return;
        }

        // 获取节点执行次数
        Map<String, AtomicLong> counters = nodeExecutionCounts.get(executionId);
        long executionNumber = counters != null && counters.get(nodeId) != null ? counters.get(nodeId).get() : 0;


        // 从 map 中获取并移除 Observation
        String key = nodeId + "_" + executionNumber;
        Observation nodeObs = observations.remove(key);
        if (nodeObs == null) {
            log.warn("No observation found for node: {} (execution #{})", nodeId, executionNumber);
            return;
        }

        // 记录输出状态
        String outputState = dumpState(state);
        nodeObs.highCardinalityKeyValue(ObservationMetricAttributes.OUTPUT_STATE.value(), outputState);
        nodeObs.lowCardinalityKeyValue(ObservationMetricAttributes.NODE_SUCCESS.value(), "true");

        // 停止观测（结束计时，记录真实执行时间）
        nodeObs.stop();

    }

    @Override
    public void onComplete(String nodeId, Map<String, Object> state, RunnableConfig config) {
        String executionId = getExecutionId(state);
        log.info("GraphObservationLifecycleListener.onComplete called - executionId: {}", executionId);

        Observation graphObs = graphObservations.remove(executionId);
        if (graphObs != null) {
            log.debug("Stopping graph observation for execution: {}", executionId);

            // 记录最终状态
            String outputState = dumpState(state);
            graphObs.highCardinalityKeyValue(ObservationMetricAttributes.OUTPUT_STATE.value(), outputState);
            graphObs.lowCardinalityKeyValue(ObservationMetricAttributes.GRAPH_SUCCESS.value(), "true");

            graphObs.stop();
            log.info("Graph observation stopped for execution: {}", executionId);
        } else {
            log.warn("No graph observation found for executionId: {}", executionId);
        }

        // 清理
        graphContexts.remove(executionId);
        nodeExecutionCounts.remove(executionId);
        nodeObservations.remove(executionId);
    }

    @Override
    public void onError(String nodeId, Map<String, Object> state, Throwable error, RunnableConfig config) {
        String executionId = getExecutionId(state);
        if (executionId == null) {
            return;
        }

        log.error("Error in graph execution: {}", executionId, error);

        // 停止当前正在执行的节点观测
        Map<String, Observation> observations = nodeObservations.get(executionId);
        if (observations != null && nodeId != null) {
            Map<String, AtomicLong> counters = nodeExecutionCounts.get(executionId);
            long executionNumber = counters != null && counters.get(nodeId) != null ? counters.get(nodeId).get() : 0;
            String key = nodeId + "_" + executionNumber;
            Observation nodeObs = observations.remove(key);
            if (nodeObs != null) {
                nodeObs.error(error);
                nodeObs.lowCardinalityKeyValue(ObservationMetricAttributes.NODE_SUCCESS.value(), "false");
                nodeObs.stop();
            }
        }

        // 停止图观测
        Observation graphObs = graphObservations.remove(executionId);
        if (graphObs != null) {
            graphObs.error(error);
            graphObs.lowCardinalityKeyValue(ObservationMetricAttributes.GRAPH_SUCCESS.value(), "false");
            graphObs.stop();
        }

        // 清理
        graphContexts.remove(executionId);
        nodeExecutionCounts.remove(executionId);
        nodeObservations.remove(executionId);
    }

    /**
     * 获取 executionId
     *
     * @param state 状态
     * @return executionId，如果不存在返回 null
     */
    private String getExecutionId(Map<String, Object> state) {
        if (state == null) {
            return null;
        }
        return (String) state.get(EXECUTION_ID_KEY);
    }

    /**
     * 序列化状态为字符串（截断过长内容）
     *
     * @param state 状态
     * @return 序列化后的字符串
     */
    private String dumpState(Map<String, Object> state) {
        if (state == null || state.isEmpty()) {
            return "empty";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            String key = entry.getKey();
            // 跳过内部字段和日志
            if (key.startsWith("_") || "logs".equals(key)) {
                continue;
            }
            Object value = entry.getValue();
            String valStr = String.valueOf(value);
            // 截断
            if (valStr.length() > 1000) {
                valStr = valStr.substring(0, 1000) + "... (truncated)";
            }
            sb.append(key).append("=").append(valStr).append("; ");
        }
        return sb.length() > 0 ? sb.toString() : "empty";
    }

    /**
     * 清理节点名称用于 Prometheus 指标
     * Prometheus 指标名称只能包含字母、数字、下划线和冒号
     *
     * @param nodeId 节点ID
     * @return 清理后的节点ID
     */
    private String sanitizeMetricName(String nodeId) {
        // 将非字母数字字符替换为下划线
        return nodeId.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
