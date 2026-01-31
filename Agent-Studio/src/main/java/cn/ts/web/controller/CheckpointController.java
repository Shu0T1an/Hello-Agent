package cn.ts.web.controller;

import cn.ts.graph.GraphRunnerContext;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.checkpoint.StateSnapshot;
import cn.ts.web.controller.response.Result;
import cn.ts.web.controller.response.ResultCode;
import cn.ts.web.dto.CheckpointDTO;
import cn.ts.web.dto.CheckpointDetailDTO;
import cn.ts.web.mapper.CheckpointMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Checkpoint 管理控制器（高级 API）
 * <p>
 * 提供调试和状态管理相关的 API
 * 用于查看和恢复 Checkpoint
 * </p>
 *
 * @author tianshuo
 */
@RestController
@RequestMapping("/api/checkpoints")
@CrossOrigin(origins = "*")
public class CheckpointController {

    private final CheckpointManager checkpointManager;
    private final CheckpointMapper checkpointMapper;

    public CheckpointController(CheckpointManager checkpointManager, CheckpointMapper checkpointMapper) {
        this.checkpointManager = checkpointManager;
        this.checkpointMapper = checkpointMapper;
    }

    /**
     * 获取会话的所有 Checkpoint
     *
     * @param threadId 会话ID（threadId）
     * @return Checkpoint 列表
     */
    @GetMapping("/threads/{threadId}")
    public Result<List<CheckpointDTO>> getCheckpoints(@PathVariable String threadId) {
        List<StateSnapshot> snapshots = checkpointManager.getStateHistory(threadId);
        List<CheckpointDTO> dtoList = snapshots.stream()
                .map(this::toCheckpointDTO)
                .collect(Collectors.toList());
        return Result.success(dtoList);
    }

    /**
     * 获取指定 Checkpoint 的详情
     *
     * @param checkpointId Checkpoint ID
     * @return Checkpoint 详情
     */
    @GetMapping("/{checkpointId}")
    public Result<CheckpointDetailDTO> getCheckpoint(@PathVariable String checkpointId) {
        return checkpointMapper.selectByCheckpointId(checkpointId)
                .map(this::toCheckpointDetailDTO)
                .map(Result::success)
                .orElse(Result.error(ResultCode.NOT_FOUND, "Checkpoint not found"));
    }

    /**
     * 恢复到指定 Checkpoint
     *
     * @param checkpointId Checkpoint ID
     * @param body          请求体，可选包含恢复后的新状态数据
     * @return 恢复结果
     */
    @PostMapping("/{checkpointId}/restore")
    public Result<Map<String, Object>> restoreCheckpoint(
            @PathVariable String checkpointId,
            @RequestBody(required = false) Map<String, Object> body) {

        // 查找 Checkpoint
        var entityOpt = checkpointMapper.selectByCheckpointId(checkpointId);
        if (entityOpt.isEmpty()) {
            return Result.error(ResultCode.NOT_FOUND, "Checkpoint not found");
        }

        var entity = entityOpt.get();
        String threadId = entity.getThreadId();

        // 获取恢复上下文
        GraphRunnerContext context = checkpointManager.restoreContext(threadId, checkpointId);
        if (context == null) {
            return Result.error(ResultCode.OPERATION_FAILED, "Failed to restore checkpoint");
        }

        // 如果提供了额外的状态数据，合并到恢复的状态中
        Map<String, Object> restoredState = new java.util.HashMap<>(context.getOverallState().data());
        if (body != null && !body.isEmpty()) {
            restoredState.putAll(body);
        }

        return Result.success(Map.of(
                "threadId", threadId,
                "checkpointId", checkpointId,
                "restoredState", restoredState,
                "iteration", entity.getIteration()
        ));
    }

    /**
     * 删除指定 Checkpoint
     *
     * @param checkpointId Checkpoint ID
     * @return 删除结果
     */
    @DeleteMapping("/{checkpointId}")
    public Result<String> deleteCheckpoint(@PathVariable String checkpointId) {
        var entityOpt = checkpointMapper.selectByCheckpointId(checkpointId);
        if (entityOpt.isEmpty()) {
            return Result.error(ResultCode.NOT_FOUND, "Checkpoint not found");
        }

        checkpointManager.deleteCheckpoint(entityOpt.get().getThreadId(), checkpointId);
        return Result.success("Checkpoint deleted");
    }

    /**
     * 删除会话的所有 Checkpoint
     *
     * @param threadId 会话ID
     * @return 删除结果
     */
    @DeleteMapping("/threads/{threadId}")
    public Result<String> deleteThread(@PathVariable String threadId) {
        checkpointManager.deleteThread(threadId);
        return Result.success("All checkpoints deleted for thread: " + threadId);
    }

    /**
     * 获取所有会话列表
     *
     * @return 会话 ID 列表
     */
    @GetMapping("/threads")
    public Result<List<String>> getAllThreads() {
        return Result.success(checkpointMapper.selectAllThreadIds());
    }

    /**
     * 转换为 CheckpointDTO
     */
    private CheckpointDTO toCheckpointDTO(StateSnapshot snapshot) {
        CheckpointDTO dto = new CheckpointDTO();
        dto.setCheckpointId(snapshot.getCheckpointId());
        dto.setThreadId(snapshot.getThreadId());
        dto.setNodeId(snapshot.getNodeId());
        dto.setSource(snapshot.getMetadata().getSource());
        dto.setParentId(snapshot.getMetadata().getParentId());
        dto.setIteration(snapshot.getIteration());
        dto.setCreatedAt(snapshot.getTimestamp());
        return dto;
    }

    /**
     * 将 CheckpointEntity 转换为 CheckpointDetailDTO
     */
    private CheckpointDetailDTO toCheckpointDetailDTO(cn.ts.web.entity.CheckpointEntity entity) {
        CheckpointDetailDTO dto = new CheckpointDetailDTO();
        dto.setCheckpointId(entity.getCheckpointId());
        dto.setThreadId(entity.getThreadId());
        dto.setNodeId(entity.getNodeId());
        dto.setSource(entity.getSource());
        dto.setParentId(entity.getParentId());
        dto.setStepInfo(entity.getMetadataMap());
        dto.setIteration(entity.getIteration());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setState(entity.getStateMap());
        return dto;
    }
}
