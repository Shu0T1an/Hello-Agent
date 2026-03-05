package cn.ts.web.channel.runtime.command;

import cn.ts.web.channel.dto.ChannelInboundMessage;
import cn.ts.web.channel.entity.ChannelSessionMappingEntity;
import cn.ts.web.channel.mapper.ChannelSessionMappingMapper;
import cn.ts.web.session.dto.SessionDetailDTO;
import cn.ts.web.session.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClearCommandHandler implements ChannelCommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(ClearCommandHandler.class);
    private static final String SUCCESS_MESSAGE = "已清空上下文，开始新对话。";
    private static final String UNSUPPORTED_MESSAGE = "当前渠道会话标识缺失，无法执行 /clear";
    private static final String ERROR_MESSAGE = "执行 /clear 失败，请稍后重试";

    private final SessionService sessionService;
    private final ChannelSessionMappingMapper channelSessionMappingMapper;

    public ClearCommandHandler(SessionService sessionService,
                               ChannelSessionMappingMapper channelSessionMappingMapper) {
        this.sessionService = sessionService;
        this.channelSessionMappingMapper = channelSessionMappingMapper;
    }

    @Override
    public String name() {
        return "clear";
    }

    @Override
    public ChannelCommandResult handle(ChannelCommandContext context, List<String> args) {
        ChannelInboundMessage message = context.message();
        String channelType = cleanText(message.getChannelType());
        String externalSessionId = cleanText(message.getChannelSessionId());
        if (channelType == null || externalSessionId == null) {
            return ChannelCommandResult.of(UNSUPPORTED_MESSAGE);
        }

        try {
            SessionDetailDTO newSession = sessionService.createSession(context.agentName(), "Channel Session");
            String newInternalSessionId = newSession.getId();

            int updated = channelSessionMappingMapper.updateInternalSessionId(
                    channelType,
                    externalSessionId,
                    newInternalSessionId
            );
            if (updated == 0) {
                ChannelSessionMappingEntity mapping = new ChannelSessionMappingEntity();
                mapping.setChannelType(channelType);
                mapping.setExternalSessionId(externalSessionId);
                mapping.setInternalSessionId(newInternalSessionId);
                try {
                    channelSessionMappingMapper.insert(mapping);
                } catch (DuplicateKeyException ex) {
                    channelSessionMappingMapper.updateInternalSessionId(
                            channelType,
                            externalSessionId,
                            newInternalSessionId
                    );
                }
            }
            return ChannelCommandResult.of(SUCCESS_MESSAGE);
        } catch (RuntimeException ex) {
            logger.error("Failed to execute /clear for channelType={}, externalSessionId={}, message={}",
                    channelType, externalSessionId, ex.getMessage(), ex);
            return ChannelCommandResult.of(ERROR_MESSAGE);
        }
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
