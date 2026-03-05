package cn.ts.web.channel.runtime.command;

import java.util.List;

public interface ChannelCommandHandler {

    String name();

    ChannelCommandResult handle(ChannelCommandContext context, List<String> args);
}
