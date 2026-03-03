package cn.ts.web.tool.local;

import cn.ts.web.skills.config.SkillsProperties;
import cn.ts.web.skills.model.SkillDetail;
import cn.ts.web.skills.model.SkillReferenceContent;
import cn.ts.web.skills.model.SkillSummary;
import cn.ts.web.skills.service.SkillRegistryService;
import cn.ts.web.tool.local.fs.ToolResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SkillReadTool {

    private static final Logger logger = LoggerFactory.getLogger(SkillReadTool.class);

    private static final String SKILLS_DISABLED = "SKILLS_DISABLED";
    private static final String SKILL_NOT_FOUND = "SKILL_NOT_FOUND";
    private static final String SKILL_REFERENCE_NOT_FOUND = "SKILL_REFERENCE_NOT_FOUND";
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final SkillsProperties properties;
    private final SkillRegistryService registryService;
    private final ObjectMapper objectMapper;

    public SkillReadTool(SkillsProperties properties, SkillRegistryService registryService, ObjectMapper objectMapper) {
        this.properties = properties;
        this.registryService = registryService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "list_skills", description = "List available skills with optional keyword query.")
    public String listSkills(@ToolParam(description = "List request") ListSkillsRequest request) {
        if (!isEnabled()) {
            return toJson(ToolResponse.error(SKILLS_DISABLED, "Skills feature is disabled"));
        }
        try {
            String query = request == null ? null : request.query();
            Integer limit = request == null ? null : request.limit();
            List<SkillSummary> skills = registryService.listSkills(query, limit);

            Map<String, Object> data = new HashMap<>();
            data.put("count", skills.size());
            data.put("skills", skills);
            return toJson(ToolResponse.ok("Skills listed", data));
        } catch (Exception e) {
            logger.warn("list_skills failed: {}", e.getMessage());
            return toJson(ToolResponse.error(INTERNAL_ERROR, e.getMessage()));
        }
    }

    @Tool(name = "get_skill_detail", description = "Get one skill detail by skill id.")
    public String getSkillDetail(@ToolParam(description = "Detail request") SkillDetailRequest request) {
        if (!isEnabled()) {
            return toJson(ToolResponse.error(SKILLS_DISABLED, "Skills feature is disabled"));
        }
        if (request == null || request.skill_id() == null || request.skill_id().isBlank()) {
            return toJson(ToolResponse.error(SKILL_NOT_FOUND, "skill_id is required"));
        }
        try {
            SkillDetail detail = registryService.getSkillDetail(request.skill_id());
            return toJson(ToolResponse.ok("Skill detail loaded", detail));
        } catch (IllegalArgumentException e) {
            return toJson(ToolResponse.error(SKILL_NOT_FOUND, e.getMessage()));
        } catch (Exception e) {
            logger.warn("get_skill_detail failed: {}", e.getMessage());
            return toJson(ToolResponse.error(INTERNAL_ERROR, e.getMessage()));
        }
    }

    @Tool(name = "get_skill_reference", description = "Get one skill reference file content by skill id and reference id.")
    public String getSkillReference(@ToolParam(description = "Reference request") SkillReferenceRequest request) {
        if (!isEnabled()) {
            return toJson(ToolResponse.error(SKILLS_DISABLED, "Skills feature is disabled"));
        }
        if (request == null || request.skill_id() == null || request.skill_id().isBlank()
                || request.ref_id() == null || request.ref_id().isBlank()) {
            return toJson(ToolResponse.error(SKILL_REFERENCE_NOT_FOUND, "skill_id and ref_id are required"));
        }
        try {
            SkillReferenceContent content = registryService.getReferenceContent(request.skill_id(), request.ref_id());
            return toJson(ToolResponse.ok("Skill reference loaded", content));
        } catch (IllegalArgumentException e) {
            return toJson(ToolResponse.error(SKILL_REFERENCE_NOT_FOUND, e.getMessage()));
        } catch (Exception e) {
            logger.warn("get_skill_reference failed: {}", e.getMessage());
            return toJson(ToolResponse.error(INTERNAL_ERROR, e.getMessage()));
        }
    }

    private boolean isEnabled() {
        return properties.isEnabled() && properties.isToolEnabled();
    }

    private String toJson(ToolResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"error\",\"errorCode\":\"INTERNAL_ERROR\",\"message\":\"Serialization failed\"}";
        }
    }

    public record ListSkillsRequest(
            String query,
            Integer limit
    ) {
    }

    public record SkillDetailRequest(
            String skill_id
    ) {
    }

    public record SkillReferenceRequest(
            String skill_id,
            String ref_id
    ) {
    }
}

