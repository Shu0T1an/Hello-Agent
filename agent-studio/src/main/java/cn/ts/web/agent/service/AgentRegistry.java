package cn.ts.web.agent.service;

import cn.ts.graph.CompiledGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 注册表
 * <p>
 * 负责管理已注册的 Agent（CompiledGraph）。
 * 提供线程安全的注册、注销、查询功能。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * AgentRegistry registry = new AgentRegistry();
 * registry.register("myAgent", graph);
 * boolean exists = registry.isRegistered("myAgent");
 * registry.unregister("myAgent");
 * }</pre>
 * </p>
 *
 * @author tianshuo
 */
@Component
public class AgentRegistry {

    private static final Logger logger = LoggerFactory.getLogger(AgentRegistry.class);

    private final ConcurrentHashMap<String, CompiledGraph> graphRegistry;

    /**
     * 创建 Agent 注册表
     */
    public AgentRegistry() {
        this.graphRegistry = new ConcurrentHashMap<>();
    }

    /**
     * 注册一个 Agent（编译后的图）
     *
     * @param agentName Agent 名称
     * @param graph     编译后的图
     * @throws IllegalArgumentException 如果 agentName 或 graph 为 null
     */
    public void register(String agentName, CompiledGraph graph) {
        if (agentName == null || agentName.isEmpty()) {
            throw new IllegalArgumentException("Agent name cannot be null or empty");
        }
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }

        graphRegistry.put(agentName, graph);
        logger.debug("Registered agent: {}", agentName);
    }

    /**
     * 获取已注册的 Agent
     *
     * @param agentName Agent 名称
     * @return 编译后的图，如果不存在返回 null
     */
    public CompiledGraph get(String agentName) {
        return graphRegistry.get(agentName);
    }

    /**
     * 检查 Agent 是否已注册
     *
     * @param agentName Agent 名称
     * @return 如果已注册返回 true，否则返回 false
     */
    public boolean isRegistered(String agentName) {
        return graphRegistry.containsKey(agentName);
    }

    /**
     * 获取所有已注册的 Agent 名称
     *
     * @return Agent 名称集合
     */
    public Set<String> getRegisteredAgentNames() {
        return Set.copyOf(graphRegistry.keySet());
    }

    /**
     * 获取已注册 Agent 的数量
     *
     * @return 已注册数量
     */
    public int size() {
        return graphRegistry.size();
    }

    /**
     * 注销 Agent
     *
     * @param agentName Agent 名称
     * @return 如果被注销的 Agent 存在返回 true，否则返回 false
     */
    public boolean unregister(String agentName) {
        CompiledGraph removed = graphRegistry.remove(agentName);
        if (removed != null) {
            logger.debug("Unregistered agent: {}", agentName);
            return true;
        }
        return false;
    }

    /**
     * 清空所有已注册的 Agent
     */
    public void clear() {
        graphRegistry.clear();
        logger.debug("Cleared all registered agents");
    }

    /**
     * 检查注册表是否为空
     *
     * @return 如果为空返回 true，否则返回 false
     */
    public boolean isEmpty() {
        return graphRegistry.isEmpty();
    }
}
