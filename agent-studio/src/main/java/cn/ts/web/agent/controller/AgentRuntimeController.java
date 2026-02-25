package cn.ts.web.agent.controller;

import cn.ts.web.agent.dto.AgentGraphDTO;
import cn.ts.web.agent.dto.RuntimeAgentDTO;
import cn.ts.web.agent.service.AgentGraphQueryService;
import cn.ts.web.agent.service.RuntimeAgentQueryService;
import cn.ts.web.shared.response.Result;
import cn.ts.web.shared.response.ResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Runtime agent query endpoints.
 */
@RestController
@RequestMapping("/api/agents/runtime")
public class AgentRuntimeController {

    private final RuntimeAgentQueryService runtimeAgentQueryService;
    private final AgentGraphQueryService agentGraphQueryService;

    public AgentRuntimeController(
            RuntimeAgentQueryService runtimeAgentQueryService,
            AgentGraphQueryService agentGraphQueryService) {
        this.runtimeAgentQueryService = runtimeAgentQueryService;
        this.agentGraphQueryService = agentGraphQueryService;
    }

    @GetMapping
    public Result<List<RuntimeAgentDTO>> listRuntimeAgents() {
        return Result.success(runtimeAgentQueryService.listRuntimeAgents());
    }

    @GetMapping("/{agentName}/graph")
    public ResponseEntity<Result<AgentGraphDTO>> getRuntimeAgentGraph(@PathVariable String agentName) {
        try {
            AgentGraphDTO graph = agentGraphQueryService.queryByAgentName(agentName);
            return ResponseEntity.ok(Result.success(graph));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(ResultCode.NOT_FOUND, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(ResultCode.BAD_REQUEST, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.error(ResultCode.CONFLICT, e.getMessage()));
        }
    }
}
