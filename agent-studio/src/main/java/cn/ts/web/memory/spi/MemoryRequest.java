package cn.ts.web.memory.spi;

public record MemoryRequest(
        String sessionId,
        String executionId
) {
}
