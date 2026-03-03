package cn.ts.web.cron.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class CronJobRunEntity {

    private Long id;
    private Long jobId;
    private String triggerType;
    private String status;
    private Instant startedAt;
    private Instant finishedAt;
    private String executionId;
    private String errorMessage;
}
