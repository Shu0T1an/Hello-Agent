package cn.ts.web.channel.dto;

import lombok.Data;

@Data
public class ChannelInboundMessage {

    private String channelType;
    private String channelUserId;
    private String channelSessionId;
    private String text;
    private String agentName;
}
