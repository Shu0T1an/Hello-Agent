package cn.ts.agent.interceptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Result container for model invocation.
 */
public final class ModelInvocationResult {

    private final Map<String, Object> updates;

    private ModelInvocationResult(Map<String, Object> updates) {
        this.updates = Collections.unmodifiableMap(new HashMap<>(updates));
    }

    public static ModelInvocationResult of(Map<String, Object> updates) {
        Map<String, Object> safeUpdates = updates != null ? updates : Map.of();
        return new ModelInvocationResult(safeUpdates);
    }

    public Map<String, Object> updates() {
        return updates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModelInvocationResult that)) {
            return false;
        }
        return Objects.equals(updates, that.updates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(updates);
    }
}
