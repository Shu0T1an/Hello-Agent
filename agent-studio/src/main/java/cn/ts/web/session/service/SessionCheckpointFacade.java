package cn.ts.web.session.service;

import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.checkpoint.CheckpointMetadata;
import cn.ts.graph.checkpoint.StateSnapshot;
import cn.ts.web.shared.constant.SessionConstants;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
class SessionCheckpointFacade {

    private final CheckpointManager checkpointManager;
    private final SessionStateAccessor stateAccessor;

    SessionCheckpointFacade(CheckpointManager checkpointManager, SessionStateAccessor stateAccessor) {
        this.checkpointManager = checkpointManager;
        this.stateAccessor = stateAccessor;
    }

    Optional<StateSnapshot> latest(String sessionId) {
        return checkpointManager.getState(sessionId);
    }

    void saveInitial(String sessionId, String title, String agentName) {
        CheckpointMetadata metadata = CheckpointMetadata.builder()
                .source(SessionConstants.Checkpoint.SOURCE_MANUAL)
                .stepInfo(Map.of("title", title))
                .build();
        StateSnapshot snapshot = StateSnapshot.builder()
                .checkpointId(UUID.randomUUID().toString())
                .threadId(sessionId)
                .nodeId(SessionConstants.Checkpoint.INIT_NODE)
                .lastNodeId(null)
                .state(stateAccessor.createInitialState(agentName))
                .metadata(metadata)
                .iteration(SessionConstants.Defaults.DEFAULT_ITERATION)
                .build();
        checkpointManager.getStorage().saveCheckpoint(sessionId, snapshot);
    }

    void saveAfterAgentSwitch(String sessionId, String newAgentName, StateSnapshot latest) {
        CheckpointMetadata metadata = CheckpointMetadata.builder()
                .source(SessionConstants.Checkpoint.SOURCE_MANUAL)
                .parentId(latest.getCheckpointId())
                .stepInfo(Map.of("agent_switch", true))
                .build();

        StateSnapshot newSnapshot = StateSnapshot.builder()
                .checkpointId(UUID.randomUUID().toString())
                .threadId(sessionId)
                .nodeId(latest.getNodeId())
                .lastNodeId(latest.getLastNodeId())
                .state(stateAccessor.stateWithSwitchedAgent(latest, newAgentName))
                .metadata(metadata)
                .iteration(latest.getIteration())
                .build();
        checkpointManager.getStorage().saveCheckpoint(sessionId, newSnapshot);
    }

    void saveAfterMessageAppend(String sessionId, String role, String content, StateSnapshot latest) {
        CheckpointMetadata metadata = CheckpointMetadata.builder()
                .source(SessionConstants.Checkpoint.SOURCE_MANUAL)
                .parentId(latest.getCheckpointId())
                .build();
        StateSnapshot newSnapshot = StateSnapshot.builder()
                .checkpointId(UUID.randomUUID().toString())
                .threadId(sessionId)
                .nodeId(latest.getNodeId())
                .lastNodeId(latest.getLastNodeId())
                .state(stateAccessor.stateWithAppendedMessage(latest, role, content))
                .metadata(metadata)
                .iteration(latest.getIteration())
                .build();
        checkpointManager.getStorage().saveCheckpoint(sessionId, newSnapshot);
    }

    void deleteThread(String sessionId) {
        checkpointManager.deleteThread(sessionId);
    }
}
