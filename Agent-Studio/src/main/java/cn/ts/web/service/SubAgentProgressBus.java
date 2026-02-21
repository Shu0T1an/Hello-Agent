package cn.ts.web.service;

import cn.ts.agent.extension.progress.SubAgentProgressEvent;
import cn.ts.agent.extension.progress.SubAgentProgressReporter;
import cn.ts.web.constant.ApiConstants;
import cn.ts.web.dto.AgentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory bus used to stream subagent progress events by execution id.
 */
@Component
public class SubAgentProgressBus implements SubAgentProgressReporter {

    private final Map<String, Sinks.Many<AgentResponse>> sinksByExecutionId = new ConcurrentHashMap<>();
    private final int replayLimit;

    public SubAgentProgressBus(@Value("${agent.subagent.progress-buffer-size:1024}") int replayLimit) {
        this.replayLimit = replayLimit > 0 ? replayLimit : 1024;
    }

    public Flux<AgentResponse> stream(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return Flux.empty();
        }
        return getOrCreateSink(executionId).asFlux();
    }

    @Override
    public void emit(String executionId, SubAgentProgressEvent event) {
        if (executionId == null || executionId.isBlank() || event == null) {
            return;
        }
        Sinks.Many<AgentResponse> sink = getOrCreateSink(executionId);
        sink.tryEmitNext(toAgentResponse(executionId, event));
    }

    public void complete(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return;
        }
        Sinks.Many<AgentResponse> sink = sinksByExecutionId.remove(executionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    private Sinks.Many<AgentResponse> getOrCreateSink(String executionId) {
        return sinksByExecutionId.computeIfAbsent(
                executionId,
                key -> Sinks.many().replay().limit(replayLimit)
        );
    }

    private AgentResponse toAgentResponse(String executionId, SubAgentProgressEvent event) {
        String nodeType = event.nodeType();
        if (nodeType == null || nodeType.isBlank()) {
            nodeType = ApiConstants.NodeTypes.SUBAGENT;
        }
        return AgentResponse.builder()
                .eventType(event.eventType())
                .nodeId(event.nodeId())
                .nodeType(nodeType)
                .message(event.message())
                .timestamp(event.timestamp() != null ? event.timestamp() : Instant.now())
                .executionId(executionId)
                .metadata(event.metadata())
                .build();
    }
}
