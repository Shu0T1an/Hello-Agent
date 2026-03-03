package cn.ts.web.channel.controller;

import cn.ts.web.channel.dto.ChannelConfigDTO;
import cn.ts.web.channel.dto.ChannelHealthDTO;
import cn.ts.web.channel.service.ChannelConfigService;
import cn.ts.web.channel.runtime.ChannelRuntimeManager;
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

@RestController
@RequestMapping("/api/channels")
public class ChannelManagementController {

    private final ChannelConfigService channelConfigService;
    private final ChannelRuntimeManager channelRuntimeManager;

    public ChannelManagementController(ChannelConfigService channelConfigService,
                                       ChannelRuntimeManager channelRuntimeManager) {
        this.channelConfigService = channelConfigService;
        this.channelRuntimeManager = channelRuntimeManager;
    }

    @GetMapping
    public Result<List<ChannelConfigDTO>> list() {
        return Result.success(channelConfigService.list());
    }

    @GetMapping("/{id}")
    public Result<ChannelConfigDTO> getById(@PathVariable Long id) {
        return Result.success(channelConfigService.getById(id));
    }

    @PostMapping
    public Result<ChannelConfigDTO> create(@RequestBody ChannelConfigDTO dto) {
        return Result.success(channelConfigService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<ChannelConfigDTO> update(@PathVariable Long id, @RequestBody ChannelConfigDTO dto) {
        return Result.success(channelConfigService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        channelConfigService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/enable")
    public Result<ChannelConfigDTO> setEnabled(@PathVariable Long id,
                                               @RequestParam(defaultValue = "true") boolean enabled) {
        return Result.success(channelConfigService.setEnabled(id, enabled));
    }

    @GetMapping("/{id}/health")
    public Result<ChannelHealthDTO> health(@PathVariable Long id) {
        ChannelConfigDTO channel = channelConfigService.getById(id);
        boolean healthy = channelRuntimeManager.health(id);
        String status = healthy ? "running" : "stopped";
        return Result.success(new ChannelHealthDTO(channel.getChannelName(), healthy, status));
    }
}
