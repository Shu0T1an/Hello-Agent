package cn.ts.web.cron.controller;

import cn.ts.web.cron.dto.CronJobDTO;
import cn.ts.web.cron.dto.CronJobRunDTO;
import cn.ts.web.cron.service.CronJobService;
import cn.ts.web.shared.response.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cron/jobs")
public class CronManagementController {

    private final CronJobService cronJobService;

    public CronManagementController(CronJobService cronJobService) {
        this.cronJobService = cronJobService;
    }

    @GetMapping
    public Result<List<CronJobDTO>> list() {
        return Result.success(cronJobService.list());
    }

    @GetMapping("/{id}")
    public Result<CronJobDTO> getById(@PathVariable Long id) {
        return Result.success(cronJobService.getById(id));
    }

    @PostMapping
    public Result<CronJobDTO> create(@RequestBody CronJobDTO dto) {
        return Result.success(cronJobService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<CronJobDTO> update(@PathVariable Long id, @RequestBody CronJobDTO dto) {
        return Result.success(cronJobService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        cronJobService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/enable")
    public Result<CronJobDTO> setEnabled(@PathVariable Long id,
                                         @RequestParam(defaultValue = "true") boolean enabled) {
        return Result.success(cronJobService.setEnabled(id, enabled));
    }

    @PostMapping("/{id}/run")
    public Result<Map<String, Long>> runNow(@PathVariable Long id) {
        Long runId = cronJobService.runNow(id);
        return Result.success(Map.of("runId", runId));
    }

    @GetMapping("/{id}/runs")
    public Result<List<CronJobRunDTO>> listRuns(@PathVariable Long id,
                                                @RequestParam(defaultValue = "20") int limit) {
        return Result.success(cronJobService.listRuns(id, limit));
    }
}
