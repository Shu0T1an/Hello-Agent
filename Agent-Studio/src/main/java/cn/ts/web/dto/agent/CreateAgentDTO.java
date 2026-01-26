package cn.ts.web.dto.agent;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 创建 Agent 请求 DTO
 */
@Data
public class CreateAgentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Agent名称（唯一标识）
     */
    @NotBlank(message = "Agent名称不能为空")
    @Size(min = 2, max = 100, message = "Agent名称长度必须在2-100之间")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Agent名称只能包含字母、数字、下划线和连字符")
    private String agentName;

    /**
     * 显示名称
     */
    @NotBlank(message = "显示名称不能为空")
    @Size(min = 2, max = 200, message = "显示名称长度必须在2-200之间")
    private String displayName;

    /**
     * Agent描述
     */
    @Size(max = 1000, message = "描述长度不能超过1000字符")
    private String description;

    /**
     * 关联模型ID
     */
    @NotNull(message = "模型ID不能为空")
    @Positive(message = "模型ID必须是正数")
    private Long modelId;

    /**
     * 系统提示词
     */
    @Size(max = 10000, message = "系统提示词长度不能超过10000字符")
    private String systemPrompt;

    /**
     * 最大迭代次数
     */
    @Min(value = 1, message = "最大迭代次数必须大于0")
    @Max(value = 100, message = "最大迭代次数不能超过100")
    private Integer maxIterations;

    /**
     * 温度参数
     */
    @DecimalMin(value = "0.0", message = "温度参数必须大于等于0")
    @DecimalMax(value = "2.0", message = "温度参数必须小于等于2")
    private BigDecimal temperature;

    /**
     * 是否启用流式输出
     */
    private Boolean enableStreaming;

    /**
     * 关联的工具定义ID列表
     */
    private List<@NotNull(message = "工具ID不能为空") @Positive(message = "工具ID必须是正数") Long> toolIds;
}
