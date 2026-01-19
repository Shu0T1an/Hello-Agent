package cn.ts.graph.visualization;

import cn.ts.graph.CompiledGraph;
import cn.ts.graph.StateGraph;
import cn.ts.graph.constant.GraphConstants;
import cn.ts.graph.edge.EdgeAction;
import cn.ts.graph.node.NodeAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MermaidGraphVisualizer 测试类
 *
 * @author tianshuo
 */
class MermaidGraphVisualizerTest {

    @Test
    void testSimpleGraph() {
        StateGraph graph = new StateGraph()
                .addNode("process", NodeAction.of(state -> null))
                .addNode("validate", NodeAction.of(state -> null))
                .addEdge(GraphConstants.START, "process")
                .addEdge("process", "validate")
                .addEdge("validate", GraphConstants.END);

        String mermaid = graph.toMermaidDiagram();

        assertNotNull(mermaid);
        assertTrue(mermaid.contains("graph TD"));
        assertTrue(mermaid.contains("process"));
        assertTrue(mermaid.contains("validate"));
        assertTrue(mermaid.contains("__start__"));
        assertTrue(mermaid.contains("__end__"));
        System.out.println("=== Simple Graph Mermaid ===");
        System.out.println(mermaid);
    }

    @Test
    void testGraphWithDescriptions() {
        StateGraph graph = new StateGraph()
                .addNode("readEmail", NodeAction.of(state -> null), "读取邮件")
                .addNode("classify", NodeAction.of(state -> null), "分类意图")
                .addNode("handleUrgent", NodeAction.of(state -> null), "紧急处理")
                .addNode("handleNormal", NodeAction.of(state -> null), "常规处理")
                .addEdge(GraphConstants.START, "readEmail")
                .addEdge("readEmail", "classify")
                .addEdge("handleUrgent", GraphConstants.END)
                .addEdge("handleNormal", GraphConstants.END);

        String mermaid = graph.toMermaidDiagram();

        assertNotNull(mermaid);
        assertTrue(mermaid.contains("读取邮件"));
        assertTrue(mermaid.contains("分类意图"));
        assertTrue(mermaid.contains("紧急处理"));
        assertTrue(mermaid.contains("常规处理"));
        System.out.println("=== Graph With Descriptions Mermaid ===");
        System.out.println(mermaid);
    }

    @Test
    void testConditionalEdges() {
        StateGraph graph = new StateGraph()
                .addNode("classify", NodeAction.of(state -> null), "分类意图")
                .addNode("handleUrgent", NodeAction.of(state -> null), "紧急处理")
                .addNode("handleNormal", NodeAction.of(state -> null), "常规处理")
                .addConditionalEdge(GraphConstants.START,
                        EdgeAction.of(state -> "urgent"),
                        Map.of("urgent", "handleUrgent", "normal", "handleNormal"))
                .addEdge("handleUrgent", GraphConstants.END)
                .addEdge("handleNormal", GraphConstants.END);

        String mermaid = graph.toMermaidDiagram();

        assertNotNull(mermaid);
        assertTrue(mermaid.contains("urgent"));
        assertTrue(mermaid.contains("normal"));
        System.out.println("=== Conditional Edges Mermaid ===");
        System.out.println(mermaid);
    }

    @Test
    void testCompiledGraph() {
        CompiledGraph graph = new StateGraph()
                .addNode("step1", NodeAction.of(state -> null), "步骤一")
                .addNode("step2", NodeAction.of(state -> null), "步骤二")
                .addEdge(GraphConstants.START, "step1")
                .addEdge("step1", "step2")
                .addEdge("step2", GraphConstants.END)
                .compile();

        String mermaid = graph.toMermaidDiagram();

        assertNotNull(mermaid);
        assertTrue(mermaid.contains("graph TD"));
        System.out.println("=== Compiled Graph Mermaid ===");
        System.out.println(mermaid);
    }

    @Test
    void testLeftToRightDirection() {
        StateGraph graph = new StateGraph()
                .addNode("step1", NodeAction.of(state -> null))
                .addNode("step2", NodeAction.of(state -> null))
                .addEdge(GraphConstants.START, "step1")
                .addEdge("step1", "step2")
                .addEdge("step2", GraphConstants.END);

        VisualizationConfig config = VisualizationConfig.builder()
                .direction(VisualizationConfig.Direction.LEFT_RIGHT)
                .build();

        String mermaid = graph.toMermaidDiagram(config);

        assertNotNull(mermaid);
        assertTrue(mermaid.contains("graph LR"));
        System.out.println("=== Left to Right Direction Mermaid ===");
        System.out.println(mermaid);
    }

    @Test
    void testNoStyles() {
        StateGraph graph = new StateGraph()
                .addNode("step1", NodeAction.of(state -> null))
                .addNode("step2", NodeAction.of(state -> null))
                .addEdge(GraphConstants.START, "step1")
                .addEdge("step1", "step2")
                .addEdge("step2", GraphConstants.END);

        VisualizationConfig config = VisualizationConfig.builder()
                .includeStyles(false)
                .build();

        String mermaid = graph.toMermaidDiagram(config);

        assertNotNull(mermaid);
        assertFalse(mermaid.contains("classDef"));
        System.out.println("=== No Styles Mermaid ===");
        System.out.println(mermaid);
    }

    @Test
    void testNoDescriptions() {
        StateGraph graph = new StateGraph()
                .addNode("step1", NodeAction.of(state -> null), "步骤一")
                .addNode("step2", NodeAction.of(state -> null), "步骤二")
                .addEdge(GraphConstants.START, "step1")
                .addEdge("step1", "step2")
                .addEdge("step2", GraphConstants.END);

        VisualizationConfig config = VisualizationConfig.builder()
                .showDescriptions(false)
                .build();

        String mermaid = graph.toMermaidDiagram(config);

        assertNotNull(mermaid);
        assertFalse(mermaid.contains("步骤一"));
        assertFalse(mermaid.contains("步骤二"));
        System.out.println("=== No Descriptions Mermaid ===");
        System.out.println(mermaid);
    }
}
