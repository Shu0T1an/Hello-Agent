package cn.ts.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent 执行请求 DTO
 * <p>
 * 封装 Agent 执行所需的所有参数
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecuteRequest {

    /**
     * 用户输入内容
     */
    private String input;

    /**
     * 会话 ID（可选，用于保持连续对话）
     */
    private String sessionId;

    /**
     * 超时时间（秒，可选，默认使用配置值）
     */
    private Integer timeout;

    /**
     * 初始状态（可选，用于自定义 Agent 执行的初始状态）
     */
    private Map<String, Object> initialState;
}
