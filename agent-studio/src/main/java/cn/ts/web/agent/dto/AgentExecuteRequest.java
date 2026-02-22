package cn.ts.web.agent.dto;

import cn.ts.web.infra.tempfile.dto.TemporaryFileContent;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
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

    /**
     * 临时文件内容列表（包含分块信息）
     * <p>
     * 用于会话级别的临时文件上传，文件内容会被注入到 LLM 上下文中
     * </p>
     */
    private List<TemporaryFileContent> fileContents;
}
