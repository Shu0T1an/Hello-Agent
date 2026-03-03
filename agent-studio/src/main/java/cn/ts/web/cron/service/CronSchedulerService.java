package cn.ts.web.cron.service;

import cn.ts.agent.constant.StateKeys;
import cn.ts.web.agent.service.AgentExecutionService;
import cn.ts.web.cron.entity.CronJobEntity;
import cn.ts.web.cron.entity.CronJobRunEntity;
import cn.ts.web.cron.mapper.CronJobMapper;
import cn.ts.web.cron.mapper.CronJobRunMapper;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class CronSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(CronSchedulerService.class);

    private final TaskScheduler taskScheduler;
    private final CronJobMapper cronJobMapper;
    private final CronJobRunMapper cronJobRunMapper;
    private final AgentExecutionService agentExecutionService;

    private final Map<Long, ScheduledFuture<?>> scheduledJobs = new ConcurrentHashMap<>();

    public CronSchedulerService(TaskScheduler taskScheduler,
                                CronJobMapper cronJobMapper,
                                CronJobRunMapper cronJobRunMapper,
                                AgentExecutionService agentExecutionService) {
        this.taskScheduler = taskScheduler;
        this.cronJobMapper = cronJobMapper;
        this.cronJobRunMapper = cronJobRunMapper;
        this.agentExecutionService = agentExecutionService;
    }

    @PostConstruct
    public void bootstrap() {
        try {
            cronJobMapper.selectEnabled().forEach(this::schedule);
        } catch (RuntimeException e) {
            // Keep startup compatible with test contexts where cron schema is not initialized.
            logger.warn("Skip cron scheduler bootstrap: {}", e.getMessage());
        }
    }

    public void refresh(CronJobEntity job) {
        try {
            unschedule(job.getId());
            if (Boolean.TRUE.equals(job.getEnabled())) {
                schedule(job);
            }
        } catch (RuntimeException e) {
            logger.warn("Skip cron refresh for {}: {}", job.getId(), e.getMessage());
        }
    }

    public void unschedule(Long jobId) {
        ScheduledFuture<?> scheduledFuture = scheduledJobs.remove(jobId);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

    public Long runNow(CronJobEntity job) {
        return execute(job, "manual");
    }

    private void schedule(CronJobEntity job) {
        CronTrigger trigger = new CronTrigger(job.getCronExpression(), ZoneId.of(job.getZoneId()));
        ScheduledFuture<?> future = taskScheduler.schedule(() -> execute(job, "auto"), trigger);
        if (future != null) {
            scheduledJobs.put(job.getId(), future);
        }
        updateNextRun(job);
    }

    private Long execute(CronJobEntity job, String triggerType) {
        CronJobRunEntity run = new CronJobRunEntity();
        run.setJobId(job.getId());
        run.setTriggerType(triggerType);
        run.setStatus("running");
        run.setStartedAt(Instant.now());
        cronJobRunMapper.insert(run);

        Map<String, Object> state = new HashMap<>();
        state.put(StateKeys.INPUT, job.getInputText());

        agentExecutionService.executeAgentStreamWithSession(
                        job.getAgentName(),
                        state,
                        job.getSessionId(),
                        Duration.ofSeconds(120))
                .takeUntil(resp -> "completed".equals(resp.getEventType()) || "error".equals(resp.getEventType()))
                .last()
                .subscribe(resp -> {
                    String status = "completed".equals(resp.getEventType()) ? "success" : "failed";
                    String error = "success".equals(status) ? null : resp.getMessage();
                    finishRun(job, run.getId(), status, error);
                }, ex -> finishRun(job, run.getId(), "failed", ex.getMessage()));

        return run.getId();
    }

    private void finishRun(CronJobEntity job, Long runId, String status, String errorMessage) {
        Instant now = Instant.now();
        cronJobRunMapper.finish(runId, status, now, errorMessage);

        Instant nextRun = calculateNextRun(job.getCronExpression(), job.getZoneId());
        cronJobMapper.updateRunMetadata(job.getId(), status, errorMessage, now, nextRun);
    }

    public Instant calculateNextRun(String cronExpression, String zoneId) {
        CronExpression expression = CronExpression.parse(cronExpression);
        ZonedDateTime next = expression.next(ZonedDateTime.now(ZoneId.of(zoneId)));
        return next == null ? null : next.toInstant();
    }

    private void updateNextRun(CronJobEntity job) {
        Instant nextRun = calculateNextRun(job.getCronExpression(), job.getZoneId());
        cronJobMapper.updateRunMetadata(job.getId(), job.getLastStatus(), job.getLastError(), job.getLastRunAt(), nextRun);
    }
}
