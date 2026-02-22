package cn.ts.web.infra.checkpoint.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Checkpoint 详情数据传输对象
 */
public class CheckpointDetailDTO extends CheckpointDTO {

    private Map<String, Object> stepInfo;
    private Map<String, Object> state;

    public Map<String, Object> getStepInfo() {
        return stepInfo;
    }

    public void setStepInfo(Map<String, Object> stepInfo) {
        this.stepInfo = stepInfo;
    }

    public Map<String, Object> getState() {
        return state;
    }

    public void setState(Map<String, Object> state) {
        this.state = state;
    }
}
