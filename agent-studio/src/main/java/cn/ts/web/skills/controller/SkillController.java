package cn.ts.web.skills.controller;

import cn.ts.web.shared.response.Result;
import cn.ts.web.skills.dto.SkillLifecycleRequest;
import cn.ts.web.skills.dto.SkillImportRequest;
import cn.ts.web.skills.model.SkillDetail;
import cn.ts.web.skills.model.SkillReferenceContent;
import cn.ts.web.skills.model.SkillSummary;
import cn.ts.web.skills.service.SkillHubImportService;
import cn.ts.web.skills.service.SkillLifecycleService;
import cn.ts.web.skills.service.SkillRegistryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillRegistryService registryService;
    private final SkillLifecycleService lifecycleService;
    private final SkillHubImportService skillHubImportService;

    public SkillController(SkillRegistryService registryService,
                           SkillLifecycleService lifecycleService,
                           SkillHubImportService skillHubImportService) {
        this.registryService = registryService;
        this.lifecycleService = lifecycleService;
        this.skillHubImportService = skillHubImportService;
    }

    @GetMapping
    public Result<List<SkillSummary>> listSkills(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit) {
        return Result.success(registryService.listSkills(q, limit));
    }

    @GetMapping("/{skillId}")
    public Result<SkillDetail> getSkillDetail(@PathVariable String skillId) {
        try {
            return Result.success(registryService.getSkillDetail(skillId));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{skillId}/references/{refId}")
    public Result<SkillReferenceContent> getReferenceContent(
            @PathVariable String skillId,
            @PathVariable String refId) {
        try {
            return Result.success(registryService.getReferenceContent(skillId, refId));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    public Result<Map<String, String>> createSkill(@RequestBody SkillLifecycleRequest request) {
        String skillId = lifecycleService.create(request.getName(), request.getContent(), request.getEnable());
        return Result.success(Map.of("skillId", skillId));
    }

    @PutMapping("/{skillId}")
    public Result<Void> updateSkill(@PathVariable String skillId,
                                    @RequestBody SkillLifecycleRequest request) {
        lifecycleService.update(skillId, request.getContent());
        return Result.success();
    }

    @DeleteMapping("/{skillId}")
    public Result<Void> deleteSkill(@PathVariable String skillId) {
        lifecycleService.delete(skillId);
        return Result.success();
    }

    @PostMapping("/{skillId}/enable")
    public Result<Void> enableSkill(@PathVariable String skillId) {
        lifecycleService.enable(skillId);
        return Result.success();
    }

    @PostMapping("/{skillId}/disable")
    public Result<Void> disableSkill(@PathVariable String skillId) {
        lifecycleService.disable(skillId);
        return Result.success();
    }

    @PostMapping("/import/github")
    public Result<Map<String, Integer>> importFromGithub(@RequestBody SkillImportRequest request) {
        return Result.success(skillHubImportService.importFromGithub(request));
    }

    @PostMapping("/reindex")
    public Result<Map<String, Integer>> reindex() {
        SkillRegistryService.ReindexResult result = registryService.reindex();
        Map<String, Integer> payload = new HashMap<>();
        payload.put("count", result.count());
        payload.put("roots", result.roots());
        return Result.success(payload);
    }
}
