package cn.ts.graph.node;

import cn.ts.graph.config.RunnableConfig;
import cn.ts.graph.state.MapState;
import cn.ts.graph.state.State;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeActionWithConfigAdapterTest {

    @Test
    void fromShouldPreserveConfigAwareImplementationWhenPresent() throws Exception {
        class DualAction implements NodeAction, NodeActionWithConfig {
            @Override
            public Map<String, Object> apply(State state) {
                return Map.of("route", "legacy");
            }

            @Override
            public Map<String, Object> apply(State state, RunnableConfig config) {
                return Map.of(
                        "route", "config",
                        "thread", config.threadId() == null ? "null" : config.threadId()
                );
            }
        }

        NodeAction action = new DualAction();
        RunnableConfig config = RunnableConfig.builder()
                .threadId("thread-3")
                .build();

        Map<String, Object> result = NodeActionWithConfig.from(action).apply(new MapState(), config);
        assertEquals("config", result.get("route"));
        assertEquals("thread-3", result.get("thread"));
    }

    @Test
    void fromShouldWrapLegacyActionWhenConfigAwareImplementationMissing() throws Exception {
        NodeAction legacy = state -> Map.of("route", "legacy");
        RunnableConfig config = RunnableConfig.builder()
                .threadId("thread-legacy")
                .build();

        Map<String, Object> result = NodeActionWithConfig.from(legacy).apply(new MapState(), config);
        assertEquals("legacy", result.get("route"));
    }
}
