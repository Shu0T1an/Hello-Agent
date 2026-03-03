package cn.ts.web.cron.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class CronJobDTO {

    private Long id;
    private String jobName;
    private String cronExpression;
    private String zoneId;
    private String agentName;
    private String sessionId;
    private String inputText;
    private Boolean enabled;
    private Integer maxRetryCount;
    private Integer retryIntervalSeconds;
    private Instant lastRunAt;
    private Instant nextRunAt;
    private String lastStatus;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
}
