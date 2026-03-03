package cn.ts.web.channel.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class ChannelConfigDTO {

    private Long id;
    private String channelName;
    private String channelType;
    private Map<String, Object> config;
    private Boolean enabled;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
