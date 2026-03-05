package cn.ts.web.channel.runtime.command;

import cn.ts.web.channel.dto.ChannelInboundMessage;

public record ChannelCommandContext(ChannelInboundMessage message, String agentName) {
}
