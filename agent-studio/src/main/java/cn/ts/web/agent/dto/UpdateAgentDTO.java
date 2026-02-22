package cn.ts.web.agent.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Update Agent request DTO.
 */
@Data
public class UpdateAgentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Size(min = 2, max = 200, message = "Display name length must be between 2 and 200")
    private String displayName;

    @Size(max = 1000, message = "Description length cannot exceed 1000")
    private String description;

    @Positive(message = "Model ID must be positive")
    private Long modelId;

    @Size(max = 10000, message = "System prompt length cannot exceed 10000")
    private String systemPrompt;

    @Min(value = 1, message = "Max iterations must be >= 1")
    @Max(value = 100, message = "Max iterations must be <= 100")
    private Integer maxIterations;

    @DecimalMin(value = "0.0", message = "Temperature must be >= 0")
    @DecimalMax(value = "2.0", message = "Temperature must be <= 2")
    private BigDecimal temperature;

    private Boolean enableStreaming;

    private Boolean isActive;

    private List<@Positive(message = "Tool ID must be positive") Long> toolIds;

    private Boolean enableSubAgentInterceptor;

    private Boolean includeGeneralPurpose;

    private SubAgentToolsPolicy subAgentToolsPolicy;

    private List<SubAgentMappingDTO> subAgents;
}
