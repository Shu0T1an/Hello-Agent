package cn.ts.web.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent 恢复执行请求 DTO
 * <p>
 * 用于从中断处恢复执行时传递反馈数据
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResumeRequest {

    /**
     * 检查点ID
     */
    @NotBlank(message = "检查点ID不能为空")
    private String checkpointId;

    /**
     * 反馈数据
     * <p>
     * 包含用户对工具调用的审批结果
     * </p>
     */
    @NotNull(message = "反馈数据不能为空")
    private Map<String, Object> feedbackData;

    /**
     * 会话ID（可选，用于保持连续对话）
     */
    private String sessionId;

    /**
     * 超时时间（秒，可选，默认使用配置值）
     */
    private Integer timeout;
}
