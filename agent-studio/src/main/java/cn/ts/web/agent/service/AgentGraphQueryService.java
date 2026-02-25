package cn.ts.web.agent.service;

import cn.ts.agent.interceptor.ModelInterceptor;
import cn.ts.agent.node.LLMNode;
import cn.ts.agent.node.ToolNode;
import cn.ts.graph.CompiledGraph;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.edge.Edge;
import cn.ts.graph.node.Node;
import cn.ts.web.agent.dto.AgentConfigDTO;
import cn.ts.web.agent.dto.AgentGraphDTO;
import cn.ts.web.agent.dto.AgentGraphEdgeDTO;
import cn.ts.web.agent.dto.AgentGraphNodeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Exposes runtime compiled graph for frontend visualization.
 */
@Service
public class AgentGraphQueryService {

    private static final Logger logger = LoggerFactory.getLogger(AgentGraphQueryService.class);

    private final AgentConfigService agentConfigService;
    private final AgentRegistry agentRegistry;

    public AgentGraphQueryService(AgentConfigService agentConfigService, AgentRegistry agentRegistry) {
        this.agentConfigService = agentConfigService;
        this.agentRegistry = agentRegistry;
    }

    public AgentGraphDTO queryByAgentId(Long agentId) {
        if (agentId == null) {
            throw new IllegalArgumentException("agentId must not be null");
        }

        AgentConfigDTO agentConfig = agentConfigService.getAgentById(agentId);
        if (agentConfig == null) {
            throw new NoSuchElementException("Agent not found with id: " + agentId);
        }
        String agentName = agentConfig.getAgentName();
        CompiledGraph graph = agentRegistry.get(agentName);
        if (graph == null) {
            throw new IllegalStateException("Agent graph is not registered: " + agentName);
        }

        List<AgentGraphNodeDTO> nodes = buildNodes(graph.getNodes());
        List<AgentGraphEdgeDTO> edges = buildEdges(graph.getEdges());
        AgentGraphDTO.GraphStats stats = new AgentGraphDTO.GraphStats();
        stats.setNodeCount(nodes.size());
        stats.setEdgeCount(edges.size());

        AgentGraphDTO dto = new AgentGraphDTO();
        dto.setAgentId(agentId);
        dto.setAgentName(agentName);
        dto.setEntryPoint(graph.getEntryPoint());
        dto.setNodes(nodes);
        dto.setEdges(edges);
        dto.setStats(stats);
        dto.setGeneratedAt(Instant.now());
        return dto;
    }

    private List<AgentGraphNodeDTO> buildNodes(Map<String, Node> nodeMap) {
        List<AgentGraphNodeDTO> result = new ArrayList<>();
        if (nodeMap == null || nodeMap.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, Node> entry : nodeMap.entrySet()) {
            String id = entry.getKey();
            Node node = entry.getValue();
            String nodeType = classifyNode(id, node);
            AgentGraphNodeDTO dto = new AgentGraphNodeDTO();
            dto.setId(id);
            dto.setNodeType(nodeType);
            dto.setLabel(buildNodeLabel(id, nodeType));
            dto.setClassName(node != null && node.action() != null ? node.action().getClass().getName() : null);
            dto.setMetadata(extractNodeMetadata(id, nodeType, node));
            result.add(dto);
        }
        return result;
    }

    private List<AgentGraphEdgeDTO> buildEdges(List<Edge> edges) {
        List<AgentGraphEdgeDTO> result = new ArrayList<>();
        if (edges == null || edges.isEmpty()) {
            return result;
        }

        Set<String> dedupe = new LinkedHashSet<>();
        int seq = 1;
        for (Edge edge : edges) {
            if (edge == null) {
                continue;
            }
            if (edge.isNormal()) {
                String source = edge.from();
                String target = edge.to();
                String key = buildEdgeKey(source, target, "normal", null);
                if (!dedupe.add(key)) {
                    continue;
                }
                AgentGraphEdgeDTO dto = new AgentGraphEdgeDTO();
                dto.setId("e" + (seq++));
                dto.setSource(source);
                dto.setTarget(target);
                dto.setEdgeType("normal");
                dto.setLabel(null);
                result.add(dto);
                continue;
            }

            Map<String, String> routeMapping = edge.routeMapping();
            if (routeMapping == null || routeMapping.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, String> route : routeMapping.entrySet()) {
                String label = route.getKey();
                String target = route.getValue();
                String source = edge.from();
                String key = buildEdgeKey(source, target, "conditional", label);
                if (!dedupe.add(key)) {
                    continue;
                }
                AgentGraphEdgeDTO dto = new AgentGraphEdgeDTO();
                dto.setId("e" + (seq++));
                dto.setSource(source);
                dto.setTarget(target);
                dto.setEdgeType("conditional");
                dto.setLabel(label);
                result.add(dto);
            }
        }
        return result;
    }

    private String buildEdgeKey(String source, String target, String edgeType, String label) {
        return source + "|" + target + "|" + edgeType + "|" + (label == null ? "" : label);
    }

    private String classifyNode(String nodeId, Node node) {
        if (GraphConstants.AGENT_MODEL.equals(nodeId)) {
            return "llm";
        }
        if (GraphConstants.AGENT_TOOL.equals(nodeId)) {
            return "tool";
        }
        if (GraphConstants.AGENT_END.equals(nodeId)) {
            return "end";
        }
        if (nodeId != null && nodeId.startsWith("__hook_")) {
            return "hook";
        }
        if (node == null || node.action() == null) {
            return "custom";
        }
        if (node.action() instanceof LLMNode) {
            return "llm";
        }
        if (node.action() instanceof ToolNode) {
            return "tool";
        }
        return "custom";
    }

    private String buildNodeLabel(String nodeId, String nodeType) {
        if ("llm".equals(nodeType)) {
            return "LLM Node";
        }
        if ("tool".equals(nodeType)) {
            return "Tool Node";
        }
        if ("hook".equals(nodeType)) {
            return parseHookName(nodeId);
        }
        if ("end".equals(nodeType)) {
            return "End Node";
        }
        return nodeId != null ? nodeId : "Custom Node";
    }

    private Map<String, Object> extractNodeMetadata(String nodeId, String nodeType, Node node) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (node == null || node.action() == null) {
            return metadata;
        }

        Object action = node.action();
        if ("llm".equals(nodeType) && action instanceof LLMNode llmNode) {
            metadata.put("streaming", readFieldValue(llmNode, "streaming", Boolean.class, false));

            List<?> toolCallbacks = readFieldValue(llmNode, "toolCallbacks", List.class, List.of());
            metadata.put("toolCount", toolCallbacks != null ? toolCallbacks.size() : 0);
            metadata.put("hasTools", toolCallbacks != null && !toolCallbacks.isEmpty());

            List<ModelInterceptor> interceptors = castInterceptorList(
                    readFieldValue(llmNode, "interceptors", List.class, List.of())
            );
            List<String> names = new ArrayList<>();
            List<String> subagentTypes = new ArrayList<>();
            for (ModelInterceptor interceptor : interceptors) {
                if (interceptor == null) {
                    continue;
                }
                names.add(interceptor.getName());
                if ("SubAgent".equals(interceptor.getName())) {
                    Object subAgentsValue = readFieldValue(interceptor, "subAgents", Map.class, Map.of());
                    if (subAgentsValue instanceof Map<?, ?> subAgentMap) {
                        for (Object key : subAgentMap.keySet()) {
                            if (key != null) {
                                subagentTypes.add(String.valueOf(key));
                            }
                        }
                    }
                }
            }
            metadata.put("interceptors", names);
            metadata.put("interceptorCount", names.size());
            if (!subagentTypes.isEmpty()) {
                metadata.put("subagentTypes", subagentTypes);
            }
            return metadata;
        }

        if ("tool".equals(nodeType) && action instanceof ToolNode toolNode) {
            var callbacks = toolNode.getToolCallbacks();
            metadata.put("toolCount", callbacks.size());
            List<String> toolNames = new ArrayList<>();
            for (var callback : callbacks) {
                if (callback != null && callback.getToolDefinition() != null) {
                    toolNames.add(callback.getToolDefinition().name());
                }
            }
            metadata.put("toolNames", toolNames);
            return metadata;
        }

        if ("hook".equals(nodeType)) {
            metadata.put("hookName", parseHookName(nodeId));
            metadata.put("hookPhase", parseHookPhase(nodeId));
            return metadata;
        }

        if ("end".equals(nodeType)) {
            metadata.put("terminal", true);
            return metadata;
        }

        return metadata;
    }

    @SuppressWarnings("unchecked")
    private List<ModelInterceptor> castInterceptorList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<ModelInterceptor> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof ModelInterceptor interceptor) {
                result.add(interceptor);
            }
        }
        return result;
    }

    private String parseHookName(String nodeId) {
        if (nodeId == null) {
            return "Hook";
        }
        if (!nodeId.startsWith("__hook_")) {
            return nodeId;
        }
        String body = nodeId.substring("__hook_".length());
        int idx = body.lastIndexOf('_');
        if (idx <= 0) {
            return body;
        }
        return body.substring(0, idx);
    }

    private String parseHookPhase(String nodeId) {
        if (nodeId == null || !nodeId.startsWith("__hook_")) {
            return "unknown";
        }
        if (nodeId.endsWith("_before")) {
            return "before_model";
        }
        if (nodeId.endsWith("_after")) {
            return "after_model";
        }
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    private <T> T readFieldValue(Object target, String fieldName, Class<?> expectedType, T defaultValue) {
        if (target == null) {
            return defaultValue;
        }
        Class<?> current = target.getClass();
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                if (value == null) {
                    return defaultValue;
                }
                if (expectedType.isAssignableFrom(value.getClass())) {
                    return (T) value;
                }
                return defaultValue;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (Exception e) {
                logger.debug("Failed to read field '{}' from {}", fieldName, target.getClass().getName(), e);
                return defaultValue;
            }
        }
        return defaultValue;
    }
}

