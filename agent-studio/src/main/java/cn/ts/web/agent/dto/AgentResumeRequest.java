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
     * 支持两种恢复模式：
     * 1) 工具审批：{ "mode": "tool_approval", "feedbacks": [...] }
     * 2) 澄清问答：{ "mode": "clarification_qa", "clarificationAnswers": [...] }
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
