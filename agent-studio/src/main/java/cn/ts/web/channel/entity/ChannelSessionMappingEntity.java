package cn.ts.web.channel.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class ChannelSessionMappingEntity {

    private Long id;
    private String channelType;
    private String externalSessionId;
    private String internalSessionId;
    private Instant createdAt;
    private Instant updatedAt;
}
