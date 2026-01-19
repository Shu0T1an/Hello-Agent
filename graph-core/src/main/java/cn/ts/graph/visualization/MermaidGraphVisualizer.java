package cn.ts.graph.visualization;

import cn.ts.graph.CompiledGraph;
import cn.ts.graph.StateGraph;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.edge.Edge;
import cn.ts.graph.node.Node;

import java.util.Map;

/**
 * Mermaid 格式图可视化器
 * <p>
 * 将图结构转换为 Mermaid 流程图格式
 * </p>
 *
 * @author tianshuo
 */
public class MermaidGraphVisualizer implements GraphVisualizer {

    @Override
    public String visualize(StateGraph graph) {
        return visualize(graph, VisualizationConfig.createDefault());
    }

    @Override
    public String visualize(StateGraph graph, VisualizationConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("graph ").append(config.getDirection().getCode()).append("\n");

        // 生成节点定义
        for (Node node : graph.getNodes().values()) {
            sb.append(formatNode(node, config)).append("\n");
        }

        // 添加 START 和 END 节点
        sb.append(formatSpecialNode(GraphConstants.START, "开始", config)).append("\n");
        sb.append(formatSpecialNode(GraphConstants.END, "结束", config)).append("\n");

        // 生成边定义
        for (Edge edge : graph.getEdges()) {
            sb.append(formatEdge(edge, config)).append("\n");
        }

        // 如果需要样式，添加样式定义
        if (config.isIncludeStyles()) {
            sb.append("\n");
            sb.append(getStyleDefinitions());
        }

        return sb.toString();
    }

    @Override
    public String visualize(CompiledGraph graph) {
        return visualize(graph, VisualizationConfig.createDefault());
    }

    @Override
    public String visualize(CompiledGraph graph, VisualizationConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("graph ").append(config.getDirection().getCode()).append("\n");

        // 生成节点定义
        for (Node node : graph.getNodes().values()) {
            sb.append(formatNode(node, config)).append("\n");
        }

        // 添加 START 和 END 节点
        sb.append(formatSpecialNode(GraphConstants.START, "开始", config)).append("\n");
        sb.append(formatSpecialNode(GraphConstants.END, "结束", config)).append("\n");

        // 生成边定义
        for (Edge edge : graph.getEdges()) {
            sb.append(formatEdge(edge, config)).append("\n");
        }

        // 如果需要样式，添加样式定义
        if (config.isIncludeStyles()) {
            sb.append("\n");
            sb.append(getStyleDefinitions());
        }

        return sb.toString();
    }

    /**
     * 格式化节点
     *
     * @param node   节点
     * @param config 配置
     * @return 格式化后的节点定义
     */
    private String formatNode(Node node, VisualizationConfig config) {
        String nodeId = sanitizeId(node.id());
        if (config.isShowDescriptions() && node.hasDescription()) {
            return String.format("    %s[\"%s: %s\"]:::node",
                    nodeId, nodeId, escapeText(node.description()));
        }
        return String.format("    %s[\"%s\"]:::node", nodeId, nodeId);
    }

    /**
     * 格式化特殊节点（START/END）
     *
     * @param nodeId  节点ID
     * @param label   显示标签
     * @param config  配置
     * @return 格式化后的节点定义
     */
    private String formatSpecialNode(String nodeId, String label, VisualizationConfig config) {
        String styleClass = GraphConstants.START.equals(nodeId) ? "startNode" : "endNode";
        return String.format("    %s((\"%s\")):::%s", nodeId, label, styleClass);
    }

    /**
     * 格式化边
     *
     * @param edge   边
     * @param config 配置
     * @return 格式化后的边定义
     */
    private String formatEdge(Edge edge, VisualizationConfig config) {
        if (edge.isNormal()) {
            return String.format("    %s --> %s", edge.from(), edge.to());
        } else {
            // 条件边，显示所有路由
            StringBuilder sb = new StringBuilder();
            Map<String, String> routeMapping = edge.routeMapping();
            if (routeMapping != null) {
                for (Map.Entry<String, String> entry : routeMapping.entrySet()) {
                    sb.append(String.format("    %s -->|\"%s\"| %s\n",
                            edge.from(), escapeText(entry.getKey()), entry.getValue()));
                }
            }
            return sb.toString().trim();
        }
    }

    /**
     * 获取样式定义
     *
     * @return 样式定义字符串
     */
    private String getStyleDefinitions() {
        return """
                classDef startNode fill:#90EE90,stroke:#333,stroke-width:2px
                classDef endNode fill:#FFB6C1,stroke:#333,stroke-width:2px
                classDef node fill:#87CEEB,stroke:#333,stroke-width:1px
                """;
    }

    /**
     * 清理节点ID，确保符合 Mermaid 语法
     *
     * @param id 原始ID
     * @return 清理后的ID
     */
    private String sanitizeId(String id) {
        // 替换特殊字符为下划线
        return id.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * 转义文本中的特殊字符
     *
     * @param text 原始文本
     * @return 转义后的文本
     */
    private String escapeText(String text) {
        if (text == null) {
            return "";
        }
        // 转义 Mermaid 特殊字符
        return text.replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
