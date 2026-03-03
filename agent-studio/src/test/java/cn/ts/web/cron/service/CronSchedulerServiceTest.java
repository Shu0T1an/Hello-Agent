package cn.ts.web.cron.service;

import cn.ts.agent.constant.StateKeys;
import cn.ts.web.agent.dto.AgentResponse;
import cn.ts.web.agent.service.AgentExecutionService;
import cn.ts.web.cron.entity.CronJobEntity;
import cn.ts.web.cron.entity.CronJobRunEntity;
import cn.ts.web.cron.mapper.CronJobMapper;
import cn.ts.web.cron.mapper.CronJobRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CronSchedulerServiceTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private CronJobMapper cronJobMapper;

    @Mock
    private CronJobRunMapper cronJobRunMapper;

    @Mock
    private AgentExecutionService agentExecutionService;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    private CronSchedulerService cronSchedulerService;

    @BeforeEach
    void setUp() {
        cronSchedulerService = new CronSchedulerService(
                taskScheduler,
                cronJobMapper,
                cronJobRunMapper,
                agentExecutionService
        );
    }

    @Test
    void refreshEnabledJob_ShouldScheduleAndPersistNextRun() {
        CronJobEntity job = buildJob(10L, true);
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenAnswer(invocation -> scheduledFuture);

        cronSchedulerService.refresh(job);

        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        verify(cronJobMapper).updateRunMetadata(eq(10L), any(), any(), any(), any());
    }

    @Test
    void refreshDisabledJob_ShouldCancelExistingSchedule() {
        CronJobEntity enabledJob = buildJob(11L, true);
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenAnswer(invocation -> scheduledFuture);
        cronSchedulerService.refresh(enabledJob);

        CronJobEntity disabledJob = buildJob(11L, false);
        cronSchedulerService.refresh(disabledJob);

        verify(scheduledFuture).cancel(false);
    }

    @Test
    void runNow_ShouldPersistRunRecordAndFinishSuccess() {
        CronJobEntity job = buildJob(12L, true);
        when(agentExecutionService.executeAgentStreamWithSession(
                eq("general-purpose"),
                any(Map.class),
                eq("session-1"),
                eq(Duration.ofSeconds(120))
        )).thenReturn(Flux.just(successResponse()));
        when(cronJobRunMapper.insert(any(CronJobRunEntity.class))).thenAnswer(invocation -> {
            CronJobRunEntity run = invocation.getArgument(0);
            run.setId(100L);
            return 1;
        });

        Long runId = cronSchedulerService.runNow(job);

        assertEquals(100L, runId);
        ArgumentCaptor<CronJobRunEntity> runCaptor = ArgumentCaptor.forClass(CronJobRunEntity.class);
        verify(cronJobRunMapper).insert(runCaptor.capture());
        assertEquals("manual", runCaptor.getValue().getTriggerType());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> stateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(agentExecutionService).executeAgentStreamWithSession(
                eq("general-purpose"),
                stateCaptor.capture(),
                eq("session-1"),
                eq(Duration.ofSeconds(120))
        );
        assertEquals("ping", stateCaptor.getValue().get(StateKeys.INPUT));

        verify(cronJobRunMapper).finish(eq(100L), eq("success"), any(Instant.class), eq(null));
        verify(cronJobMapper).updateRunMetadata(eq(12L), eq("success"), eq(null), any(Instant.class), any(Instant.class));
    }

    @Test
    void calculateNextRun_ShouldReturnInstant() {
        Instant next = cronSchedulerService.calculateNextRun("0 */5 * * * *", "Asia/Shanghai");
        assertNotNull(next);
    }

    private CronJobEntity buildJob(Long id, boolean enabled) {
        CronJobEntity job = new CronJobEntity();
        job.setId(id);
        job.setJobName("job-" + id);
        job.setCronExpression("0 */5 * * * *");
        job.setZoneId("Asia/Shanghai");
        job.setAgentName("general-purpose");
        job.setSessionId("session-1");
        job.setInputText("ping");
        job.setEnabled(enabled);
        job.setLastStatus("idle");
        return job;
    }

    private AgentResponse successResponse() {
        return AgentResponse.builder()
                .eventType("completed")
                .message("done")
                .build();
    }
}
