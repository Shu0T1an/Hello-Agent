package cn.ts.web.service;

import cn.ts.graph.record.ExecutionRecord;
import cn.ts.graph.record.LLMExecutionRecord;
import cn.ts.graph.record.ToolExecutionRecord;
import cn.ts.web.dto.SessionSummaryDTO;
import cn.ts.web.entity.CheckpointEntity;
import cn.ts.web.entity.SessionEntity;
import cn.ts.web.mapper.CheckpointMapper;
import cn.ts.web.mapper.SessionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 会话摘要服务
 * <p>
 * 计算和提供会话级别的统计数据，包括 Token 使用情况、工具调用统计和 LLM 调用详情
 * </p>
 *
 * @author tianshuo
 */
@Service
public class SessionSummaryService {

    private static final Logger log = LoggerFactory.getLogger(SessionSummaryService.class);

    private final CheckpointMapper checkpointMapper;
    private final SessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 状态键常量
     */
    private static final String EXECUTION_RECORD_KEY = "execution_record";
    private static final String EXECUTION_HISTORY_KEY = "execution_history";

    public SessionSummaryService(
            CheckpointMapper checkpointMapper,
            SessionMapper sessionMapper,
            ObjectMapper objectMapper) {
        this.checkpointMapper = checkpointMapper;
        this.sessionMapper = sessionMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 计算会话摘要
     *
     * @param sessionId 会话ID
     * @return 会话摘要
     * @throws IllegalArgumentException 如果会话不存在
     */
    public SessionSummaryDTO calculateSummary(String sessionId) {
        // 1. 验证会话是否存在
        SessionEntity session = sessionMapper.selectBySessionId(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        // 2. 查询所有 Checkpoint（按时间倒序）
        List<CheckpointEntity> checkpoints = checkpointMapper.selectHistoryBySessionId(sessionId);

        // 3. 提取所有执行记录
        List<ExecutionRecord> records = extractAllExecutionRecords(checkpoints);

        // 4. 计算基础统计
        SessionSummaryDTO.BasicStats basicStats = calculateBasicStats(checkpoints, records);

        // 5. 计算工具统计
        List<SessionSummaryDTO.ToolStats> toolStats = calculateToolStats(records);

        // 6. 计算 LLM 调用详情
        List<SessionSummaryDTO.LLMCallStats> llmCallStats = calculateLLMCallStats(checkpoints, records);

        // 7. 构建 DTO
        return new SessionSummaryDTO(
                sessionId,
                session.getTitle(),
                basicStats,
                toolStats,
                llmCallStats
        );
    }

    /**
     * 从 Checkpoint 列表中提取所有执行记录
     * <p>
     * 注意：只需要从最新的 Checkpoint 提取，因为 execution_history 已经累积了所有历史记录。
     * 遍历所有 Checkpoint 会导致重复计数。
     * </p>
     *
     * @param checkpoints Checkpoint 列表（按时间倒序，第一个是最新的）
     * @return 执行记录列表
     */
    private List<ExecutionRecord> extractAllExecutionRecords(List<CheckpointEntity> checkpoints) {
        if (checkpoints.isEmpty()) {
            return List.of();
        }

        // 只需要从最新的 Checkpoint 提取
        // execution_history 已经累积了所有历史执行记录
        CheckpointEntity latestCheckpoint = checkpoints.get(0);
        Map<String, Object> stateMap = latestCheckpoint.getStateMap();

        List<ExecutionRecord> records = new ArrayList<>();

        // 1. 提取当前执行记录
        @SuppressWarnings("unchecked")
        Map<String, Object> currentRecordMap = (Map<String, Object>) stateMap.get(EXECUTION_RECORD_KEY);
        if (currentRecordMap != null) {
            ExecutionRecord.fromMap(currentRecordMap).ifPresent(records::add);
        }

        // 2. 提取执行历史（包含所有历史记录）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> historyList = (List<Map<String, Object>>) stateMap.get(EXECUTION_HISTORY_KEY);
        if (historyList != null) {
            for (Map<String, Object> historyRecordMap : historyList) {
                ExecutionRecord.fromMap(historyRecordMap).ifPresent(records::add);
            }
        }

        log.debug("Extracted {} execution records from latest checkpoint", records.size());
        return records;
    }

    /**
     * 计算基础统计信息
     *
     * @param checkpoints Checkpoint 列表
     * @param records     执行记录列表
     * @return 基础统计信息
     */
    private SessionSummaryDTO.BasicStats calculateBasicStats(List<CheckpointEntity> checkpoints, List<ExecutionRecord> records) {
        // Token 统计
        long totalTokens = records.stream()
                .filter(r -> r.getNodeType() == ExecutionRecord.NodeType.LLM)
                .map(r -> (LLMExecutionRecord) r)
                .mapToLong(r -> r.getUsage().totalTokens())
                .sum();

        // 工具调用统计
        int totalToolCalls = records.stream()
                .filter(r -> r.getNodeType() == ExecutionRecord.NodeType.TOOL)
                .map(r -> (ToolExecutionRecord) r)
                .mapToInt(r -> r.getExecutions().size())
                .sum();

        // 总时长
        long totalDuration = records.stream()
                .mapToLong(ExecutionRecord::getDuration)
                .sum();

        // 迭代次数（取最大值）
        int totalIterations = checkpoints.stream()
                .mapToInt(c -> c.getIteration() != null ? c.getIteration() : 0)
                .max()
                .orElse(0);

        // LLM 调用次数
        int llmCallCount = (int) records.stream()
                .filter(r -> r.getNodeType() == ExecutionRecord.NodeType.LLM)
                .count();

        // 时间范围
        Instant startTime = checkpoints.stream()
                .map(CheckpointEntity::getCreatedAt)
                .min(Instant::compareTo)
                .orElse(null);

        Instant endTime = checkpoints.stream()
                .map(CheckpointEntity::getCreatedAt)
                .max(Instant::compareTo)
                .orElse(null);

        return new SessionSummaryDTO.BasicStats(
                totalTokens > 0 ? totalTokens : null,
                totalToolCalls > 0 ? totalToolCalls : null,
                totalDuration > 0 ? totalDuration : null,
                totalIterations > 0 ? totalIterations : null,
                llmCallCount > 0 ? llmCallCount : null,
                startTime,
                endTime
        );
    }

    /**
     * 按工具名称分组统计
     *
     * @param records 执行记录列表
     * @return 工具统计列表（按调用次数降序）
     */
    private List<SessionSummaryDTO.ToolStats> calculateToolStats(List<ExecutionRecord> records) {
        // 收集所有工具执行记录
        Map<String, List<ToolExecutionRecord.ToolExecution>> toolExecutionsMap = new HashMap<>();

        for (ExecutionRecord record : records) {
            if (record.getNodeType() == ExecutionRecord.NodeType.TOOL) {
                ToolExecutionRecord toolRecord = (ToolExecutionRecord) record;
                for (ToolExecutionRecord.ToolExecution execution : toolRecord.getExecutions()) {
                    String toolName = execution.name();
                    toolExecutionsMap.computeIfAbsent(toolName, k -> new ArrayList<>()).add(execution);
                }
            }
        }

        // 计算每个工具的统计数据
        List<SessionSummaryDTO.ToolStats> toolStatsList = new ArrayList<>();

        for (Map.Entry<String, List<ToolExecutionRecord.ToolExecution>> entry : toolExecutionsMap.entrySet()) {
            String toolName = entry.getKey();
            List<ToolExecutionRecord.ToolExecution> executions = entry.getValue();

            int callCount = executions.size();
            int successCount = (int) executions.stream().filter(ToolExecutionRecord.ToolExecution::success).count();
            int failureCount = callCount - successCount;
            double successRate = callCount > 0 ? (successCount * 100.0 / callCount) : 0.0;
            long totalDuration = executions.stream().mapToLong(ToolExecutionRecord.ToolExecution::duration).sum();
            double avgDuration = callCount > 0 ? (totalDuration * 1.0 / callCount) : 0.0;

            toolStatsList.add(new SessionSummaryDTO.ToolStats(
                    toolName,
                    callCount,
                    successCount,
                    failureCount,
                    Math.round(successRate * 100.0) / 100.0, // 保留两位小数
                    totalDuration,
                    Math.round(avgDuration * 100.0) / 100.0  // 保留两位小数
            ));
        }

        // 按调用次数降序排序
        return toolStatsList.stream()
                .sorted(Comparator.comparing(SessionSummaryDTO.ToolStats::getCallCount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 提取 LLM 调用详情
     *
     * @param checkpoints Checkpoint 列表（用于获取迭代次数和时间戳）
     * @param records     执行记录列表
     * @return LLM 调用详情列表（按时间顺序）
     */
    private List<SessionSummaryDTO.LLMCallStats> calculateLLMCallStats(List<CheckpointEntity> checkpoints, List<ExecutionRecord> records) {
        // 创建 Checkpoint 时间戳映射
        Map<String, Instant> checkpointTimestampMap = new HashMap<>();
        Map<String, Integer> checkpointIterationMap = new HashMap<>();

        for (CheckpointEntity checkpoint : checkpoints) {
            checkpointTimestampMap.put(checkpoint.getCheckpointId(), checkpoint.getCreatedAt());
            checkpointIterationMap.put(checkpoint.getCheckpointId(), checkpoint.getIteration());
        }

        List<SessionSummaryDTO.LLMCallStats> llmCallStatsList = new ArrayList<>();

        for (ExecutionRecord record : records) {
            if (record.getNodeType() == ExecutionRecord.NodeType.LLM) {
                LLMExecutionRecord llmRecord = (LLMExecutionRecord) record;

                // 提取工具调用列表
                List<String> toolCalls = llmRecord.getToolCalls().stream()
                        .map(tc -> tc.name())
                        .collect(Collectors.toList());

                // 获取时间戳（从 start time 解析）
                Instant timestamp;
                try {
                    timestamp = Instant.parse(llmRecord.getStartTime());
                } catch (Exception e) {
                    log.warn("Failed to parse timestamp: {}", llmRecord.getStartTime());
                    timestamp = null;
                }

                llmCallStatsList.add(new SessionSummaryDTO.LLMCallStats(
                        llmRecord.getNodeId(),
                        null, // 迭代次数需要从关联的 Checkpoint 获取
                        llmRecord.getUsage().promptTokens(),
                        llmRecord.getUsage().completionTokens(),
                        llmRecord.getUsage().totalTokens(),
                        llmRecord.getDuration(),
                        timestamp,
                        toolCalls.isEmpty() ? null : toolCalls
                ));
            }
        }

        // 按时间排序
        return llmCallStatsList.stream()
                .sorted(Comparator.comparing(SessionSummaryDTO.LLMCallStats::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }
}
