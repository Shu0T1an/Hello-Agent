package cn.ts.web.channel.runtime.command;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class ChannelCommandRegistry {

    private final Map<String, ChannelCommandHandler> handlers;

    public ChannelCommandRegistry(List<ChannelCommandHandler> handlers) {
        Map<String, ChannelCommandHandler> map = new HashMap<>();
        if (handlers != null) {
            for (ChannelCommandHandler handler : handlers) {
                if (handler == null || handler.name() == null || handler.name().isBlank()) {
                    continue;
                }
                map.put(handler.name().toLowerCase(Locale.ROOT), handler);
            }
        }
        this.handlers = Map.copyOf(map);
    }

    public Optional<ChannelCommandHandler> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(name.toLowerCase(Locale.ROOT)));
    }
}
