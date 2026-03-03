package cn.ts.web.cron.service;

import cn.ts.web.cron.dto.CronJobDTO;
import cn.ts.web.cron.dto.CronJobRunDTO;
import cn.ts.web.cron.entity.CronJobEntity;
import cn.ts.web.cron.entity.CronJobRunEntity;
import cn.ts.web.cron.mapper.CronJobMapper;
import cn.ts.web.cron.mapper.CronJobRunMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;

@Service
public class CronJobService {

    private final CronJobMapper cronJobMapper;
    private final CronJobRunMapper cronJobRunMapper;
    private final CronSchedulerService cronSchedulerService;

    public CronJobService(CronJobMapper cronJobMapper,
                          CronJobRunMapper cronJobRunMapper,
                          CronSchedulerService cronSchedulerService) {
        this.cronJobMapper = cronJobMapper;
        this.cronJobRunMapper = cronJobRunMapper;
        this.cronSchedulerService = cronSchedulerService;
    }

    public List<CronJobDTO> list() {
        return cronJobMapper.selectAll().stream().map(this::toDTO).toList();
    }

    public CronJobDTO getById(Long id) {
        CronJobEntity entity = requireJob(id);
        return toDTO(entity);
    }

    @Transactional
    public CronJobDTO create(CronJobDTO dto) {
        validate(dto, null);
        CronJobEntity entity = toEntity(dto);
        entity.setZoneId(normalizeZoneId(entity.getZoneId()));
        if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }
        if (entity.getLastStatus() == null || entity.getLastStatus().isBlank()) {
            entity.setLastStatus("idle");
        }
        cronJobMapper.insert(entity);
        cronSchedulerService.refresh(entity);
        return toDTO(entity);
    }

    @Transactional
    public CronJobDTO update(Long id, CronJobDTO dto) {
        CronJobEntity existing = requireJob(id);
        validate(dto, id);

        existing.setJobName(dto.getJobName());
        existing.setCronExpression(dto.getCronExpression());
        existing.setZoneId(normalizeZoneId(dto.getZoneId()));
        existing.setAgentName(dto.getAgentName());
        existing.setSessionId(dto.getSessionId());
        existing.setInputText(dto.getInputText());
        existing.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
        existing.setMaxRetryCount(dto.getMaxRetryCount());
        existing.setRetryIntervalSeconds(dto.getRetryIntervalSeconds());
        cronJobMapper.updateById(existing);
        cronSchedulerService.refresh(existing);
        return toDTO(existing);
    }

    @Transactional
    public void delete(Long id) {
        cronSchedulerService.unschedule(id);
        cronJobMapper.deleteById(id);
    }

    @Transactional
    public CronJobDTO setEnabled(Long id, boolean enabled) {
        CronJobEntity existing = requireJob(id);
        existing.setEnabled(enabled);
        cronJobMapper.updateById(existing);
        cronSchedulerService.refresh(existing);
        return toDTO(existing);
    }

    @Transactional
    public Long runNow(Long id) {
        CronJobEntity existing = requireJob(id);
        return cronSchedulerService.runNow(existing);
    }

    public List<CronJobRunDTO> listRuns(Long id, int limit) {
        requireJob(id);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return cronJobRunMapper.selectByJobId(id, safeLimit).stream().map(this::toRunDTO).toList();
    }

    private void validate(CronJobDTO dto, Long id) {
        if (dto.getJobName() == null || dto.getJobName().isBlank()) {
            throw new IllegalArgumentException("jobName is required");
        }
        if (dto.getCronExpression() == null || dto.getCronExpression().isBlank()) {
            throw new IllegalArgumentException("cronExpression is required");
        }
        if (dto.getAgentName() == null || dto.getAgentName().isBlank()) {
            throw new IllegalArgumentException("agentName is required");
        }
        if (id == null) {
            if (cronJobMapper.countByName(dto.getJobName()) > 0) {
                throw new IllegalArgumentException("Job name already exists: " + dto.getJobName());
            }
        } else {
            if (cronJobMapper.countByNameExcludeId(dto.getJobName(), id) > 0) {
                throw new IllegalArgumentException("Job name already exists: " + dto.getJobName());
            }
        }
        String zone = normalizeZoneId(dto.getZoneId());
        ZoneId.of(zone);
    }

    private String normalizeZoneId(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return "Asia/Shanghai";
        }
        return zoneId;
    }

    private CronJobEntity requireJob(Long id) {
        CronJobEntity entity = cronJobMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Cron job not found: " + id);
        }
        return entity;
    }

    private CronJobDTO toDTO(CronJobEntity entity) {
        CronJobDTO dto = new CronJobDTO();
        dto.setId(entity.getId());
        dto.setJobName(entity.getJobName());
        dto.setCronExpression(entity.getCronExpression());
        dto.setZoneId(entity.getZoneId());
        dto.setAgentName(entity.getAgentName());
        dto.setSessionId(entity.getSessionId());
        dto.setInputText(entity.getInputText());
        dto.setEnabled(entity.getEnabled());
        dto.setMaxRetryCount(entity.getMaxRetryCount());
        dto.setRetryIntervalSeconds(entity.getRetryIntervalSeconds());
        dto.setLastRunAt(entity.getLastRunAt());
        dto.setNextRunAt(entity.getNextRunAt());
        dto.setLastStatus(entity.getLastStatus());
        dto.setLastError(entity.getLastError());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private CronJobRunDTO toRunDTO(CronJobRunEntity entity) {
        CronJobRunDTO dto = new CronJobRunDTO();
        dto.setId(entity.getId());
        dto.setJobId(entity.getJobId());
        dto.setTriggerType(entity.getTriggerType());
        dto.setStatus(entity.getStatus());
        dto.setStartedAt(entity.getStartedAt());
        dto.setFinishedAt(entity.getFinishedAt());
        dto.setExecutionId(entity.getExecutionId());
        dto.setErrorMessage(entity.getErrorMessage());
        return dto;
    }

    private CronJobEntity toEntity(CronJobDTO dto) {
        CronJobEntity entity = new CronJobEntity();
        entity.setId(dto.getId());
        entity.setJobName(dto.getJobName());
        entity.setCronExpression(dto.getCronExpression());
        entity.setZoneId(dto.getZoneId());
        entity.setAgentName(dto.getAgentName());
        entity.setSessionId(dto.getSessionId());
        entity.setInputText(dto.getInputText());
        entity.setEnabled(dto.getEnabled());
        entity.setMaxRetryCount(dto.getMaxRetryCount() == null ? 1 : dto.getMaxRetryCount());
        entity.setRetryIntervalSeconds(dto.getRetryIntervalSeconds() == null ? 10 : dto.getRetryIntervalSeconds());
        entity.setLastStatus(dto.getLastStatus());
        entity.setLastError(dto.getLastError());
        return entity;
    }
}
