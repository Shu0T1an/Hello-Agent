package cn.ts.web.memory.spi;

import java.util.Optional;

public interface MemoryProvider {

    String providerName();

    Optional<MemoryPayload> load(MemoryRequest request);
}
