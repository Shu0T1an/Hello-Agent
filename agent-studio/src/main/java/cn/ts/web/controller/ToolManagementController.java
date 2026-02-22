package cn.ts.web.controller;

import cn.ts.web.controller.response.Result;
import cn.ts.web.agent.dto.ToolDefinitionDTO;
import cn.ts.web.agent.dto.ToolType;
import cn.ts.web.service.ToolDefinitionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工具管理 Controller
 */
@RestController
@RequestMapping("/api/tools")
public class ToolManagementController {

    private final ToolDefinitionService toolDefinitionService;

    public ToolManagementController(ToolDefinitionService toolDefinitionService) {
        this.toolDefinitionService = toolDefinitionService;
    }

    /**
     * 创建工具定义
     */
    @PostMapping
    public Result<ToolDefinitionDTO> createTool(@Valid @RequestBody ToolDefinitionDTO dto) {
        ToolDefinitionDTO created = toolDefinitionService.createTool(dto);
        return Result.success("创建成功", created);
    }

    /**
     * 更新工具定义
     */
    @PutMapping("/{id}")
    public Result<ToolDefinitionDTO> updateTool(
            @PathVariable Long id,
            @Valid @RequestBody ToolDefinitionDTO dto) {
        ToolDefinitionDTO updated = toolDefinitionService.updateTool(id, dto);
        return Result.success("更新成功", updated);
    }

    /**
     * 删除工具定义
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteTool(@PathVariable Long id) {
        toolDefinitionService.deleteTool(id);
        return Result.success();
    }

    /**
     * 获取单个工具定义
     */
    @GetMapping("/{id}")
    public Result<ToolDefinitionDTO> getTool(@PathVariable Long id) {
        ToolDefinitionDTO tool = toolDefinitionService.getToolById(id);
        return Result.success(tool);
    }

    /**
     * 获取所有工具定义
     */
    @GetMapping
    public Result<List<ToolDefinitionDTO>> getAllTools(
            @RequestParam(required = false) ToolType type) {
        if (type != null) {
            List<ToolDefinitionDTO> tools = toolDefinitionService.getToolsByType(type);
            return Result.success(tools);
        }
        List<ToolDefinitionDTO> tools = toolDefinitionService.getAllTools();
        return Result.success(tools);
    }

    /**
     * 获取激活的工具定义
     */
    @GetMapping("/active")
    public Result<List<ToolDefinitionDTO>> getActiveTools() {
        List<ToolDefinitionDTO> tools = toolDefinitionService.getActiveTools();
        return Result.success(tools);
    }

    /**
     * 手动触发本地工具扫描
     */
    @PostMapping("/scan-local")
    public Result<List<ToolDefinitionDTO>> scanLocalTools() {
        // TODO: 实现本地工具扫描逻辑
        return Result.success();
    }

    /**
     * 手动触发 MCP 工具同步
     */
    @PostMapping("/sync-mcp/{connectionName}")
    public Result<List<ToolDefinitionDTO>> syncMcpTools(@PathVariable String connectionName) {
        // TODO: 实现 MCP 工具同步逻辑
        // 这个功能需要 McpToolSyncService 组件支持
        return Result.success();
    }
}
