package cn.ts.web.cron.service;

import cn.ts.web.cron.dto.CronJobDTO;
import cn.ts.web.cron.entity.CronJobEntity;
import cn.ts.web.cron.mapper.CronJobMapper;
import cn.ts.web.cron.mapper.CronJobRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CronJobServiceTest {

    @Mock
    private CronJobMapper cronJobMapper;

    @Mock
    private CronJobRunMapper cronJobRunMapper;

    @Mock
    private CronSchedulerService cronSchedulerService;

    private CronJobService cronJobService;

    @BeforeEach
    void setUp() {
        cronJobService = new CronJobService(cronJobMapper, cronJobRunMapper, cronSchedulerService);
    }

    @Test
    void createJob_WithBlankZone_ShouldUseDefaultZone() {
        CronJobDTO dto = buildDto();
        dto.setZoneId(" ");
        when(cronJobMapper.countByName("daily-summary")).thenReturn(0);

        cronJobService.create(dto);

        ArgumentCaptor<CronJobEntity> captor = ArgumentCaptor.forClass(CronJobEntity.class);
        verify(cronJobMapper).insert(captor.capture());
        assertEquals("Asia/Shanghai", captor.getValue().getZoneId());
        verify(cronSchedulerService).refresh(any(CronJobEntity.class));
    }

    @Test
    void updateJob_WithBlankZone_ShouldUseDefaultZone() {
        CronJobDTO dto = buildDto();
        dto.setZoneId("");
        CronJobEntity existing = new CronJobEntity();
        existing.setId(1L);
        existing.setJobName("daily-summary");
        existing.setZoneId("Asia/Shanghai");
        existing.setEnabled(true);
        when(cronJobMapper.selectById(1L)).thenReturn(existing);
        when(cronJobMapper.countByNameExcludeId("daily-summary", 1L)).thenReturn(0);

        cronJobService.update(1L, dto);

        ArgumentCaptor<CronJobEntity> captor = ArgumentCaptor.forClass(CronJobEntity.class);
        verify(cronJobMapper).updateById(captor.capture());
        assertEquals("Asia/Shanghai", captor.getValue().getZoneId());
        verify(cronSchedulerService).refresh(any(CronJobEntity.class));
    }

    @Test
    void runNow_ShouldDelegateToSchedulerAndReturnRunId() {
        CronJobEntity existing = new CronJobEntity();
        existing.setId(2L);
        existing.setJobName("run-now-job");
        existing.setCronExpression("0 */5 * * * *");
        existing.setZoneId("Asia/Shanghai");
        existing.setAgentName("general-purpose");
        when(cronJobMapper.selectById(2L)).thenReturn(existing);
        when(cronSchedulerService.runNow(existing)).thenReturn(99L);

        Long runId = cronJobService.runNow(2L);

        assertEquals(99L, runId);
        verify(cronSchedulerService).runNow(existing);
    }

    private CronJobDTO buildDto() {
        CronJobDTO dto = new CronJobDTO();
        dto.setJobName("daily-summary");
        dto.setCronExpression("0 */5 * * * *");
        dto.setAgentName("general-purpose");
        dto.setEnabled(true);
        return dto;
    }
}
