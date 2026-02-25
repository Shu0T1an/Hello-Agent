package cn.ts.agent.extension.progress;

import cn.ts.graph.GraphResponse;
import cn.ts.graph.NodeOutput;
import cn.ts.graph.node.Node;
import cn.ts.graph.node.NodeAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubAgentStreamMapperTest {

    private final SubAgentStreamMapper mapper = new SubAgentStreamMapper();

    @Test
    void mapFailedNodeOutputContainsNodeContext() {
        Node failedNode = Node.of("fetch_docs", NodeAction.of(state -> Map.of()), "Fetch docs");
        NodeOutput output = NodeOutput.failed("fetch_docs", failedNode, "tool exploded", Instant.now());
        GraphResponse<NodeOutput> response = GraphResponse.of("fetch_docs", output);

        SubAgentStreamMapper.MappedProgress mapped = mapper.map(response, 7L);

        assertNotNull(mapped);
        assertEquals("failed", mapped.phase());
        assertEquals("tool exploded", mapped.errorMessage());
        assertEquals("fetch_docs", mapped.nodeId());
        assertEquals("custom", mapped.nodeType());
    }

    @Test
    void mapErrorResponseContainsStackTrace() {
        RuntimeException boom = new RuntimeException("boom");
        GraphResponse<NodeOutput> response = GraphResponse.error(boom);

        SubAgentStreamMapper.MappedProgress mapped = mapper.map(response, 8L);

        assertNotNull(mapped);
        assertEquals("failed", mapped.phase());
        assertEquals("boom", mapped.errorMessage());
        assertNotNull(mapped.stackTrace());
        assertTrue(mapped.stackTrace().contains("RuntimeException"));
    }
}
