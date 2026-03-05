package cn.ts.web.channel.runtime.command;

public record ChannelCommandResult(String replyMessage) {

    public static ChannelCommandResult of(String replyMessage) {
        return new ChannelCommandResult(replyMessage);
    }
}
