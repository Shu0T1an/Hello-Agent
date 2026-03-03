package cn.ts.web.memory.service;

import cn.ts.web.memory.spi.MemoryPayload;
import cn.ts.web.memory.spi.MemoryProvider;
import cn.ts.web.memory.spi.MemoryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MemoryService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryService.class);

    private final List<MemoryProvider> providers;

    public MemoryService(List<MemoryProvider> providers) {
        this.providers = providers != null ? List.copyOf(providers) : List.of();
    }

    public Optional<MemoryPayload> loadForInvocation(MemoryRequest request) {
        for (MemoryProvider provider : providers) {
            try {
                Optional<MemoryPayload> payload = provider.load(request);
                if (payload != null && payload.isPresent()) {
                    return payload;
                }
            } catch (Exception ex) {
                logger.warn("Memory provider {} failed: {}", provider.providerName(), ex.getMessage());
            }
        }
        return Optional.empty();
    }
}
