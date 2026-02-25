package cn.ts.web.agent.controller;

import cn.ts.web.agent.dto.AgentGraphDTO;
import cn.ts.web.agent.service.AgentGraphQueryService;
import cn.ts.web.shared.response.Result;
import cn.ts.web.shared.response.ResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/**
 * Runtime graph endpoint for agent visualization.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentGraphController {

    private final AgentGraphQueryService agentGraphQueryService;

    public AgentGraphController(AgentGraphQueryService agentGraphQueryService) {
        this.agentGraphQueryService = agentGraphQueryService;
    }

    @GetMapping("/{id}/graph")
    public ResponseEntity<Result<AgentGraphDTO>> getAgentGraph(@PathVariable Long id) {
        try {
            AgentGraphDTO graph = agentGraphQueryService.queryByAgentId(id);
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

