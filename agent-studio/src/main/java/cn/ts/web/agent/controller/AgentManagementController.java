package cn.ts.web.agent.controller;

import cn.ts.web.shared.response.Result;
import cn.ts.web.shared.response.ResultCode;
import cn.ts.web.agent.dto.AgentConfigDTO;
import cn.ts.web.agent.dto.CreateAgentDTO;
import cn.ts.web.agent.dto.UpdateAgentDTO;
import cn.ts.web.agent.service.AgentConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent 管理 Controller
 */
@RestController
@RequestMapping("/api/agents")
public class AgentManagementController {

    private final AgentConfigService agentConfigService;

    public AgentManagementController(AgentConfigService agentConfigService) {
        this.agentConfigService = agentConfigService;
    }

    /**
     * 创建 Agent
     */
    @PostMapping
    public Result<AgentConfigDTO> createAgent(@Valid @RequestBody CreateAgentDTO request) {
        AgentConfigDTO dto = convertToDTO(request);
        AgentConfigDTO created = agentConfigService.createAgent(dto);
        return Result.success("创建成功", created);
    }

    /**
     * 更新 Agent
     */
    @PutMapping("/{id}")
    public Result<AgentConfigDTO> updateAgent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAgentDTO request) {
        AgentConfigDTO dto = convertToDTO(request);
        AgentConfigDTO updated = agentConfigService.updateAgent(id, dto);
        return Result.success("更新成功", updated);
    }

    /**
     * 删除 Agent
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteAgent(@PathVariable Long id) {
        agentConfigService.deleteAgent(id);
        return Result.success();
    }

    /**
     * 获取单个 Agent
     */
    @GetMapping("/{id}")
    public Result<AgentConfigDTO> getAgent(@PathVariable Long id) {
        AgentConfigDTO agent = agentConfigService.getAgentById(id);
        return Result.success(agent);
    }

    /**
     * 获取所有 Agent
     */
    @GetMapping
    public Result<List<AgentConfigDTO>> getAllAgents() {
        List<AgentConfigDTO> agents = agentConfigService.getAllAgents();
        return Result.success(agents);
    }

    /**
     * 激活 Agent
     */
    @PostMapping("/{id}/activate")
    public Result<Void> activateAgent(@PathVariable Long id) {
        agentConfigService.activateAgent(id);
        return Result.success();
    }

    /**
     * 停用 Agent
     */
    @PostMapping("/{id}/deactivate")
    public Result<Void> deactivateAgent(@PathVariable Long id) {
        agentConfigService.deactivateAgent(id);
        return Result.success();
    }

    /**
     * 重载 Agent
     */
    @PostMapping("/{id}/reload")
    public Result<Void> reloadAgent(@PathVariable Long id) {
        AgentConfigDTO agent = agentConfigService.getAgentById(id);
        if (agent != null) {
            agentConfigService.reloadAgent(agent.getAgentName());
        }
        return Result.success();
    }

    /**
     * 重载所有 Agent
     */
    @PostMapping("/reload-all")
    public Result<Void> reloadAllAgents() {
        agentConfigService.reloadAllAgents();
        return Result.success();
    }

    /**
     * 转换 CreateAgentDTO 为 AgentConfigDTO
     */
    private AgentConfigDTO convertToDTO(CreateAgentDTO request) {
        AgentConfigDTO dto = new AgentConfigDTO();
        dto.setAgentName(request.getAgentName());
        dto.setDisplayName(request.getDisplayName());
        dto.setDescription(request.getDescription());
        dto.setModelId(request.getModelId());
        dto.setSystemPrompt(request.getSystemPrompt());
        dto.setMaxIterations(request.getMaxIterations());
        dto.setTemperature(request.getTemperature());
        dto.setEnableStreaming(request.getEnableStreaming());
        dto.setToolIds(request.getToolIds());
        dto.setEnableSubAgentInterceptor(request.getEnableSubAgentInterceptor());
        dto.setIncludeGeneralPurpose(request.getIncludeGeneralPurpose());
        dto.setSubAgentToolsPolicy(request.getSubAgentToolsPolicy());
        dto.setSubAgents(request.getSubAgents());
        return dto;
    }

    /**
     * 转换 UpdateAgentDTO 为 AgentConfigDTO
     */
    private AgentConfigDTO convertToDTO(UpdateAgentDTO request) {
        AgentConfigDTO dto = new AgentConfigDTO();
        dto.setDisplayName(request.getDisplayName());
        dto.setDescription(request.getDescription());
        dto.setModelId(request.getModelId());
        dto.setSystemPrompt(request.getSystemPrompt());
        dto.setMaxIterations(request.getMaxIterations());
        dto.setTemperature(request.getTemperature());
        dto.setEnableStreaming(request.getEnableStreaming());
        dto.setIsActive(request.getIsActive());
        dto.setToolIds(request.getToolIds());
        dto.setEnableSubAgentInterceptor(request.getEnableSubAgentInterceptor());
        dto.setIncludeGeneralPurpose(request.getIncludeGeneralPurpose());
        dto.setSubAgentToolsPolicy(request.getSubAgentToolsPolicy());
        dto.setSubAgents(request.getSubAgents());
        return dto;
    }
}
