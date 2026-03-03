package cn.ts.web.channel.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class ChannelConfigEntity {

    private Long id;
    private String channelName;
    private String channelType;
    private String configJson;
    private Boolean enabled;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
