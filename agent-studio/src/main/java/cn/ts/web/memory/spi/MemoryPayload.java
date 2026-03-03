package cn.ts.web.memory.spi;

import java.util.Map;

public record MemoryPayload(
        String content,
        String source,
        boolean truncated,
        Map<String, Object> metadata
) {
}
