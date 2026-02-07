package cn.ts.graph.record;

import cn.ts.graph.state.MapState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExecutionRecordManager 单元测试
 */
class ExecutionRecordManagerTest {

    private ExecutionRecordManager manager;
    private MapState state;

    @BeforeEach
    void setUp() {
        manager = new DefaultExecutionRecordManager(10);
        state = new MapState();
    }

    @Test
    void testSaveAndGetRecord() {
        // 创建并保存记录
        ExecutionRecord record = createTestRecord("node-1");
        manager.saveRecord(record, state);

        // 验证当前记录
        var current = manager.getCurrentRecord(state);
        assertTrue(current.isPresent());
        assertEquals("node-1", current.get().getNodeId());
    }

    @Test
    void testHistoryAccumulation() {
        // 保存三条记录
        manager.saveRecord(createTestRecord("node-1"), state);
        manager.saveRecord(createTestRecord("node-2"), state);
        manager.saveRecord(createTestRecord("node-3"), state);

        // 验证当前记录是最后一条
        var current = manager.getCurrentRecord(state);
        assertTrue(current.isPresent());
        assertEquals("node-3", current.get().getNodeId());

        // 验证历史有两条记录
        List<ExecutionRecord> history = manager.getHistory(state);
        assertEquals(2, history.size());
        assertEquals("node-1", history.get(0).getNodeId());
        assertEquals("node-2", history.get(1).getNodeId());
    }

    @Test
    void testMaxHistorySize() {
        DefaultExecutionRecordManager limitedManager = new DefaultExecutionRecordManager(3);

        // 保存5条记录
        for (int i = 1; i <= 5; i++) {
            limitedManager.saveRecord(createTestRecord("node-" + i), state);
        }

        // 验证历史只有3条（最后3条在当前记录之前的）
        // 保存顺序: node-1, node-2, node-3, node-4, node-5
        // 当前记录: node-5
        // 历史: [node-1, node-2, node-3, node-4] -> 限制后: [node-2, node-3, node-4]
        List<ExecutionRecord> history = limitedManager.getHistory(state);
        assertEquals(3, history.size());
        assertEquals("node-2", history.get(0).getNodeId());
        assertEquals("node-3", history.get(1).getNodeId());
        assertEquals("node-4", history.get(2).getNodeId());
    }

    @Test
    void testClearHistory() {
        // 保存一些记录
        manager.saveRecord(createTestRecord("node-1"), state);
        manager.saveRecord(createTestRecord("node-2"), state);

        // 清空历史
        manager.clearHistory(state);

        // 验证历史已清空
        List<ExecutionRecord> history = manager.getHistory(state);
        assertTrue(history.isEmpty());

        // 当前记录应该还在
        var current = manager.getCurrentRecord(state);
        assertTrue(current.isPresent());
        assertEquals("node-2", current.get().getNodeId());
    }

    @Test
    void testFromMap() {
        Map<String, Object> map = createTestRecord("node-1").toMap();

        var result = manager.fromMap(map);
        assertTrue(result.isPresent());
        assertEquals("node-1", result.get().getNodeId());
    }

    @Test
    void testGetCurrentRecordMap() {
        ExecutionRecord record = createTestRecord("node-1");
        manager.saveRecord(record, state);

        var mapOpt = manager.getCurrentRecordMap(state);
        assertTrue(mapOpt.isPresent());
        assertEquals("node-1", mapOpt.get().get("nodeId"));
    }

    @Test
    void testGetHistoryMaps() {
        manager.saveRecord(createTestRecord("node-1"), state);
        manager.saveRecord(createTestRecord("node-2"), state);

        var mapsOpt = manager.getHistoryMaps(state);
        assertTrue(mapsOpt.isPresent());
        assertEquals(1, mapsOpt.get().size());
    }

    @Test
    void testSaveNullRecord() {
        // 保存空记录不应该抛出异常
        manager.saveRecord(null, state);
        manager.saveRecord(createTestRecord("node-1"), null);

        var current = manager.getCurrentRecord(state);
        assertFalse(current.isPresent());
    }

    @Test
    void testLLMRecordInManager() {
        Instant start = Instant.parse("2025-01-15T10:00:00Z");
        Instant end = Instant.parse("2025-01-15T10:00:02Z");

        LLMExecutionRecord record = ExecutionRecords.llmSuccess(
                "llm-node", start, end,
                List.of(new InputMessage("user", "test")),
                "Response",
                new TokenUsage(10, 5, 15)
        );

        manager.saveRecord(record, state);

        var current = manager.getCurrentRecord(state);
        assertTrue(current.isPresent());
        assertTrue(current.get() instanceof LLMExecutionRecord);

        LLMExecutionRecord llmRecord = (LLMExecutionRecord) current.get();
        assertEquals("Response", llmRecord.getOutput());
        assertEquals(15, llmRecord.getUsage().totalTokens());
    }

    @Test
    void testToolRecordInManager() {
        Instant start = Instant.parse("2025-01-15T10:00:00Z");
        Instant end = Instant.parse("2025-01-15T10:00:01Z");

        List<ToolExecutionRecord.ToolExecution> executions = List.of(
                new ToolExecutionRecord.ToolExecution(
                        "call-1", "test", "{}", "result", true, 100
                )
        );

        ToolExecutionRecord record = ExecutionRecords.toolSuccess(
                "tool-node", start, end, executions
        );

        manager.saveRecord(record, state);

        var current = manager.getCurrentRecord(state);
        assertTrue(current.isPresent());
        assertTrue(current.get() instanceof ToolExecutionRecord);

        ToolExecutionRecord toolRecord = (ToolExecutionRecord) current.get();
        assertEquals(1, toolRecord.getExecutions().size());
    }

    /**
     * 创建测试记录
     */
    private ExecutionRecord createTestRecord(String nodeId) {
        Instant start = Instant.parse("2025-01-15T10:00:00Z");
        Instant end = Instant.parse("2025-01-15T10:00:01Z");

        return ExecutionRecords.success(
                ExecutionRecord.NodeType.CUSTOM,
                nodeId,
                start,
                end
        );
    }
}
