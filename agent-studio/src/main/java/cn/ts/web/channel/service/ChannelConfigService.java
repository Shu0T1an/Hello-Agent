package cn.ts.web.channel.service;

import cn.ts.web.channel.dto.ChannelConfigDTO;
import cn.ts.web.channel.entity.ChannelConfigEntity;
import cn.ts.web.channel.mapper.ChannelConfigMapper;
import cn.ts.web.channel.runtime.ChannelRuntimeManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ChannelConfigService {

    private final ChannelConfigMapper channelConfigMapper;
    private final ChannelRuntimeManager channelRuntimeManager;
    private final ObjectMapper objectMapper;

    public ChannelConfigService(ChannelConfigMapper channelConfigMapper,
                                ChannelRuntimeManager channelRuntimeManager,
                                ObjectMapper objectMapper) {
        this.channelConfigMapper = channelConfigMapper;
        this.channelRuntimeManager = channelRuntimeManager;
        this.objectMapper = objectMapper;
    }

    public List<ChannelConfigDTO> list() {
        return channelConfigMapper.selectAll().stream().map(this::toDTO).toList();
    }

    public ChannelConfigDTO getById(Long id) {
        ChannelConfigEntity entity = channelConfigMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Channel config not found: " + id);
        }
        return toDTO(entity);
    }

    @Transactional
    public ChannelConfigDTO create(ChannelConfigDTO dto) {
        ChannelConfigDTO normalized = normalizeAndValidate(dto);
        if (channelConfigMapper.countName(normalized.getChannelName()) > 0) {
            throw new IllegalArgumentException("Channel name already exists: " + normalized.getChannelName());
        }
        ChannelConfigEntity entity = toEntity(normalized);
        if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("stopped");
        }
        channelConfigMapper.insert(entity);
        channelRuntimeManager.refresh(entity.getId());
        return toDTO(entity);
    }

    @Transactional
    public ChannelConfigDTO update(Long id, ChannelConfigDTO dto) {
        ChannelConfigEntity existing = channelConfigMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Channel config not found: " + id);
        }
        ChannelConfigDTO normalized = normalizeAndValidate(dto);
        if (channelConfigMapper.countNameExcludeId(normalized.getChannelName(), id) > 0) {
            throw new IllegalArgumentException("Channel name already exists: " + normalized.getChannelName());
        }
        existing.setChannelName(normalized.getChannelName());
        existing.setChannelType(normalized.getChannelType());
        existing.setEnabled(Boolean.TRUE.equals(normalized.getEnabled()));
        existing.setStatus(normalized.getStatus());
        existing.setConfigJson(toJson(normalized.getConfig()));
        channelConfigMapper.updateById(existing);
        channelRuntimeManager.refresh(existing.getId());
        return toDTO(existing);
    }

    @Transactional
    public void delete(Long id) {
        channelConfigMapper.deleteById(id);
        channelRuntimeManager.refresh(id);
    }

    @Transactional
    public ChannelConfigDTO setEnabled(Long id, boolean enabled) {
        ChannelConfigEntity entity = channelConfigMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Channel config not found: " + id);
        }
        entity.setEnabled(enabled);
        entity.setStatus(enabled ? "running" : "stopped");
        channelConfigMapper.updateById(entity);
        channelRuntimeManager.refresh(id);
        return toDTO(entity);
    }

    private ChannelConfigDTO toDTO(ChannelConfigEntity entity) {
        ChannelConfigDTO dto = new ChannelConfigDTO();
        dto.setId(entity.getId());
        dto.setChannelName(entity.getChannelName());
        dto.setChannelType(entity.getChannelType());
        dto.setConfig(fromJson(entity.getConfigJson()));
        dto.setEnabled(entity.getEnabled());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private ChannelConfigEntity toEntity(ChannelConfigDTO dto) {
        ChannelConfigEntity entity = new ChannelConfigEntity();
        entity.setId(dto.getId());
        entity.setChannelName(dto.getChannelName());
        entity.setChannelType(dto.getChannelType());
        entity.setConfigJson(toJson(dto.getConfig()));
        entity.setEnabled(dto.getEnabled());
        entity.setStatus(dto.getStatus());
        return entity;
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid channel config json", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private ChannelConfigDTO normalizeAndValidate(ChannelConfigDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Channel config is required");
        }
        dto.setChannelName(requireText(dto.getChannelName(), "channelName is required"));
        String channelType = requireText(dto.getChannelType(), "channelType is required")
                .toLowerCase(Locale.ROOT);
        dto.setChannelType(channelType);
        Map<String, Object> normalizedConfig = normalizeConfig(dto.getConfig());
        if ("dingtalk".equals(channelType)) {
            validateDingTalkConfig(normalizedConfig);
        }
        dto.setConfig(normalizedConfig);
        return dto;
    }

    private Map<String, Object> normalizeConfig(Map<String, Object> config) {
        if (config == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(config);
    }

    private void validateDingTalkConfig(Map<String, Object> config) {
        String clientId = firstNonBlank(
                readText(config, "clientId"),
                readText(config, "appKey")
        );
        String clientSecret = firstNonBlank(
                readText(config, "clientSecret"),
                readText(config, "appSecret")
        );
        if (isBlank(clientId)) {
            throw new IllegalArgumentException("DingTalk config clientId is required");
        }
        if (isBlank(clientSecret)) {
            throw new IllegalArgumentException("DingTalk config clientSecret is required");
        }
        config.put("clientId", clientId);
        config.put("clientSecret", clientSecret);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String readText(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
