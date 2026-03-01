package cn.ts.web.service;

import cn.ts.graph.CompiledGraph;
import cn.ts.web.agent.service.AgentRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * AgentRegistry 测试
 * <p>
 * 验证 Agent 注册表的正确性
 * </p>
 *
 * @author tianshuo
 */
class AgentRegistryTest {

    private AgentRegistry registry;
    private CompiledGraph mockGraph;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        mockGraph = mock(CompiledGraph.class);
    }

    @Test
    void testRegisterAndGet() {
        registry.register("testAgent", mockGraph);

        assertTrue(registry.isRegistered("testAgent"));
        assertEquals(mockGraph, registry.get("testAgent"));
    }

    @Test
    void testRegisterWithNullName() {
        assertThrows(IllegalArgumentException.class, () -> registry.register(null, mockGraph));
    }

    @Test
    void testRegisterWithEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> registry.register("", mockGraph));
    }

    @Test
    void testRegisterWithNullGraph() {
        assertThrows(IllegalArgumentException.class, () -> registry.register("testAgent", null));
    }

    @Test
    void testIsRegistered() {
        assertFalse(registry.isRegistered("nonExistent"));

        registry.register("testAgent", mockGraph);
        assertTrue(registry.isRegistered("testAgent"));
    }

    @Test
    void testGetNonExistent() {
        assertNull(registry.get("nonExistent"));
    }

    @Test
    void testGetRegisteredAgentNames() {
        registry.register("agent1", mockGraph);
        registry.register("agent2", mockGraph);

        var names = registry.getRegisteredAgentNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("agent1"));
        assertTrue(names.contains("agent2"));
    }

    @Test
    void testSize() {
        assertEquals(0, registry.size());

        registry.register("agent1", mockGraph);
        assertEquals(1, registry.size());

        registry.register("agent2", mockGraph);
        assertEquals(2, registry.size());
    }

    @Test
    void testUnregister() {
        registry.register("testAgent", mockGraph);
        assertTrue(registry.isRegistered("testAgent"));

        boolean removed = registry.unregister("testAgent");
        assertTrue(removed);
        assertFalse(registry.isRegistered("testAgent"));
    }

    @Test
    void testUnregisterNonExistent() {
        boolean removed = registry.unregister("nonExistent");
        assertFalse(removed);
    }

    @Test
    void testClear() {
        registry.register("agent1", mockGraph);
        registry.register("agent2", mockGraph);
        assertEquals(2, registry.size());

        registry.clear();
        assertEquals(0, registry.size());
        assertTrue(registry.isEmpty());
    }

    @Test
    void testIsEmpty() {
        assertTrue(registry.isEmpty());

        registry.register("agent1", mockGraph);
        assertFalse(registry.isEmpty());
    }

    @Test
    void testOverwriteRegistration() {
        CompiledGraph graph1 = mock(CompiledGraph.class);
        CompiledGraph graph2 = mock(CompiledGraph.class);

        registry.register("testAgent", graph1);
        assertEquals(graph1, registry.get("testAgent"));

        registry.register("testAgent", graph2);
        assertEquals(graph2, registry.get("testAgent"));
        assertEquals(1, registry.size());
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        int threadCount = 10;
        int agentsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < agentsPerThread; j++) {
                    String agentName = "agent_" + threadId + "_" + j;
                    registry.register(agentName, mockGraph);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(threadCount * agentsPerThread, registry.size());
    }
}
