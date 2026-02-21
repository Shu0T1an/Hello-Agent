package cn.ts.graph.record;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExecutionRecord 单元测试
 */
class ExecutionRecordTest {

    @Test
    void testLLMExecutionRecordSuccess() {
        // 创建 LLM 执行记录
        Instant startTime = Instant.parse("2025-01-15T10:00:00Z");
        Instant endTime = Instant.parse("2025-01-15T10:00:02Z");

        LLMExecutionRecord record = ExecutionRecords.llmSuccess(
                "test-node",
                startTime,
                endTime,
                List.of(
                        new InputMessage("user", "Hello"),
                        new InputMessage("system", "You are a helpful assistant")
                ),
                "Hello, how can I help you?",
                new TokenUsage(100, 50, 150)
        );

        // 验证基础字段
        assertEquals(ExecutionRecord.NodeType.LLM, record.getNodeType());
        assertEquals("test-node", record.getNodeId());
        assertTrue(record.isSuccess());
        assertEquals(2000, record.getDuration());
        assertTrue(record.getErrorMessage().isEmpty());

        // 验证 LLM 特定字段
        assertEquals(2, record.getInputMessages().size());
        assertEquals("Hello, how can I help you?", record.getOutput());
        assertEquals(150, record.getUsage().totalTokens());
    }

    @Test
    void testLLMExecutionRecordFailure() {
        Instant startTime = Instant.parse("2025-01-15T10:00:00Z");
        Instant endTime = Instant.parse("2025-01-15T10:00:01Z");

        LLMExecutionRecord record = ExecutionRecords.llmFailure(
                "test-node",
                startTime,
                endTime,
                "API connection failed"
        );

        assertFalse(record.isSuccess());
        assertTrue(record.getErrorMessage().isPresent());
        assertEquals("API connection failed", record.getErrorMessage().get());
        assertTrue(record.getOutput().isEmpty());
        assertTrue(record.getToolCalls().isEmpty());
    }

    @Test
    void testLLMExecutionRecordToMap() {
        Instant startTime = Instant.parse("2025-01-15T10:00:00Z");
        Instant endTime = Instant.parse("2025-01-15T10:00:02Z");

        LLMExecutionRecord record = ExecutionRecords.llmSuccess(
                "test-node",
                startTime,
                endTime,
                List.of(new InputMessage("user", "Hello")),
                "Hi there!",
                new TokenUsage(10, 5, 15)
        );

        Map<String, Object> map = record.toMap();

        assertTrue(map.containsKey("nodeType"));
        assertTrue(map.containsKey("nodeId"));
        assertTrue(map.containsKey("startTime"));
        assertTrue(map.containsKey("endTime"));
        assertTrue(map.containsKey("duration"));
        assertTrue(map.containsKey("success"));
        assertTrue(map.containsKey("input"));
        assertTrue(map.containsKey("output"));
        assertTrue(map.containsKey("usage"));
        assertEquals("llm", map.get("nodeType"));
        assertEquals(2000L, map.get("duration"));
        assertEquals(true, map.get("success"));
    }

    @Test
    void testLLMExecutionRecordWithToolCalls() {
        Instant startTime = Instant.parse("2025-01-15T10:00:00Z");
        Instant endTime = Instant.parse("2025-01-15T10:00:02Z");

        LLMExecutionRecord record = ExecutionRecords.llmSuccess(
                "test-node",
                startTime,
                endTime,
                List.of(new InputMessage("user", "help me search weather")),
                "",
                List.of(
                        new ToolCallInfo("call-1", "weather_search", "{\"city\":\"Beijing\"}"),
                        new ToolCallInfo("call-2", "news_search", "{\"query\":\"weather alert\"}")
                ),
                new TokenUsage(20, 10, 30)
        );

        assertEquals(2, record.getToolCalls().size());
        assertEquals("weather_search", record.getToolCalls().get(0).name());

        Map<String, Object> map = record.toMap();
        assertTrue(map.containsKey("toolCalls"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) map.get("toolCalls");
        assertEquals(2, toolCalls.size());
        assertEquals("call-1", toolCalls.get(0).get("id"));
        assertEquals("weather_search", toolCalls.get(0).get("name"));
    }

    @Test
    void testLLMExecutionRecordFromMap() {
        Map<String, Object> map = Map.of(
                "nodeType", "llm",
                "nodeId", "test-node",
                "startTime", "2025-01-15T10:00:00Z",
                "endTime", "2025-01-15T10:00:02Z",
                "duration", 2000L,
                "success", true,
                "input", List.of(
                        Map.of("role", "user", "content", "Hello")
                ),
                "output", "Hi there!",
                "usage", Map.of(
                        "promptTokens", 10,
                        "completionTokens", 5,
                        "totalTokens", 15
                )
        );

        var result = ExecutionRecord.fromMap(map);

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof LLMExecutionRecord);

        LLMExecutionRecord record = (LLMExecutionRecord) result.get();
        assertEquals("test-node", record.getNodeId());
        assertEquals("Hi there!", record.getOutput());
        assertEquals(15, record.getUsage().totalTokens());
    }

    @Test
    void testToolExecutionRecordSuccess() {
        Instant startTime = Instant.parse("2025-01-15T10:00:00Z");
        Instant endTime = Instant.parse("2025-01-15T10:00:01Z");

        List<ToolExecutionRecord.ToolExecution> executions = List.of(
                new ToolExecutionRecord.ToolExecution(
                        "call-1", "search", "{\"query\":\"test\"}",
                        "Search results", true, 500
                ),
                new ToolExecutionRecord.ToolExecution(
                        "call-2", "calculate", "{\"expr\":\"1+1\"}",
                        "2", true, 100
                )
        );

        ToolExecutionRecord record = ExecutionRecords.toolSuccess(
                "tool-node",
                startTime,
                endTime,
                executions
        );

        assertEquals(ExecutionRecord.NodeType.TOOL, record.getNodeType());
        assertEquals("tool-node", record.getNodeId());
        assertTrue(record.isSuccess());
        assertEquals(2, record.getExecutions().size());
        assertEquals(1000, record.getDuration());
    }

    @Test
    void testToolExecutionRecordFailure() {
        Instant startTime = Instant.parse("2025-01-15T10:00:00Z");
        Instant endTime = Instant.parse("2025-01-15T10:00:01Z");

        ToolExecutionRecord record = ExecutionRecords.toolFailure(
                "tool-node",
                startTime,
                endTime,
                "Tool timeout"
        );

        assertFalse(record.isSuccess());
        assertTrue(record.getErrorMessage().isPresent());
        assertEquals("Tool timeout", record.getErrorMessage().get());
        assertTrue(record.getExecutions().isEmpty());
    }

    @Test
    void testToolExecutionRecordToMap() {
        Instant startTime = Instant.parse("2025-01-15T10:00:00Z");
        Instant endTime = Instant.parse("2025-01-15T10:00:01Z");

        List<ToolExecutionRecord.ToolExecution> executions = List.of(
                new ToolExecutionRecord.ToolExecution(
                        "call-1", "test", "{}", "result", true, 100
                )
        );

        ToolExecutionRecord record = ExecutionRecords.toolSuccess(
                "tool-node", startTime, endTime, executions
        );

        Map<String, Object> map = record.toMap();

        assertTrue(map.containsKey("nodeType"));
        assertTrue(map.containsKey("nodeId"));
        assertTrue(map.containsKey("executions"));
        assertEquals("tool", map.get("nodeType"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> execList = (List<Map<String, Object>>) map.get("executions");
        assertEquals(1, execList.size());
        assertEquals("test", execList.get(0).get("name"));
    }

    @Test
    void testToolExecutionFromMap() {
        Map<String, Object> map = Map.of(
                "nodeType", "tool",
                "nodeId", "tool-node",
                "startTime", "2025-01-15T10:00:00Z",
                "endTime", "2025-01-15T10:00:01Z",
                "duration", 1000L,
                "success", true,
                "executions", List.of(
                        Map.of(
                                "id", "call-1",
                                "name", "search",
                                "arguments", "{}",
                                "result", "ok",
                                "success", true,
                                "duration", 100
                        )
                )
        );

        var result = ExecutionRecord.fromMap(map);

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof ToolExecutionRecord);

        ToolExecutionRecord record = (ToolExecutionRecord) result.get();
        assertEquals(1, record.getExecutions().size());
        assertEquals("search", record.getExecutions().get(0).name());
    }

    @Test
    void testBaseExecutionRecord() {
        ExecutionRecord record = ExecutionRecords.success(
                ExecutionRecord.NodeType.CUSTOM,
                "custom-node",
                Instant.parse("2025-01-15T10:00:00Z"),
                Instant.parse("2025-01-15T10:00:01Z")
        );

        assertEquals(ExecutionRecord.NodeType.CUSTOM, record.getNodeType());
        assertTrue(record.isSuccess());
        assertEquals(1000, record.getDuration());

        Map<String, Object> map = record.toMap();
        assertEquals("custom", map.get("nodeType"));
    }

    @Test
    void testInputMessage() {
        InputMessage msg = new InputMessage("user", "Hello");

        assertEquals("user", msg.role());
        assertEquals("Hello", msg.content());

        Map<String, Object> map = msg.toMap();
        assertEquals("user", map.get("role"));
        assertEquals("Hello", map.get("content"));

        InputMessage parsed = InputMessage.fromMap(map);
        assertEquals("user", parsed.role());
        assertEquals("Hello", parsed.content());
    }

    @Test
    void testToolCallInfo() {
        ToolCallInfo info = new ToolCallInfo("call-1", "search", "{\"q\":\"test\"}");

        assertEquals("call-1", info.id());
        assertEquals("search", info.name());
        assertEquals("{\"q\":\"test\"}", info.arguments());

        Map<String, Object> map = info.toMap();
        ToolCallInfo parsed = ToolCallInfo.fromMap(map);
        assertEquals("call-1", parsed.id());
        assertEquals("search", parsed.name());
    }

    @Test
    void testTokenUsage() {
        TokenUsage usage = new TokenUsage(100, 50, 150);

        assertEquals(100, usage.promptTokens());
        assertEquals(50, usage.completionTokens());
        assertEquals(150, usage.totalTokens());

        Map<String, Object> map = usage.toMap();
        assertEquals(100L, map.get("promptTokens"));
        assertEquals(50L, map.get("completionTokens"));
        assertEquals(150L, map.get("totalTokens"));

        TokenUsage parsed = TokenUsage.fromMap(map);
        assertEquals(150, parsed.totalTokens());

        assertEquals(0, TokenUsage.empty().totalTokens());
    }
}
