package cn.ts.web.channel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChannelHealthDTO {

    private String channelName;
    private boolean healthy;
    private String status;
}
