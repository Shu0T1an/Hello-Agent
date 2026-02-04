package cn.ts.graph.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 类型化状态反序列化器
 * <p>
 * 在反序列化 State 时使用类型元数据还原正确的类型，确保泛型类型不丢失。
 * 从 JSON 中提取类型元数据，使用对应的 TypeReference 进行反序列化。
 * </p>
 * <p>
 * 期望的 JSON 结构：
 * <pre>
 * {
 *   "messages": [...],
 *   "__type_metadata__": {
 *     "messages": "java.util.List<org.springframework.ai.chat.messages.Message>"
 *   }
 * }
 * </pre>
 * </p>
 *
 * @author tianshuo
 * @see StateTypeRegistry
 * @see TypedStateSerializer
 */
public class TypedStateDeserializer {

    private static final Logger log = LoggerFactory.getLogger(TypedStateDeserializer.class);

    private final ObjectMapper objectMapper;
    private final StateTypeRegistry registry;

    /**
     * 创建反序列化器
     *
     * @param ObjectMapper Jackson ObjectMapper
     */
    public TypedStateDeserializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.registry = StateTypeRegistry.getInstance();
    }

    /**
     * 创建反序列化器（使用自定义注册表）
     *
     * @param ObjectMapper Jackson ObjectMapper
     * @param registry      类型注册表
     */
    public TypedStateDeserializer(ObjectMapper objectMapper, StateTypeRegistry registry) {
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    /**
     * 反序列化 JSON 并根据类型元数据还原类型
     *
     * @param json JSON 字符串
     * @return 包含正确类型的状态数据
     * @throws JsonProcessingException 反序列化失败时抛出
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> deserializeWithTypeMetadata(String json) throws JsonProcessingException {
        if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) {
            return new HashMap<>();
        }

        // 首先解析为 Map 以提取类型元数据
        // 注意：此时 Message 会被反序列化为 LinkedHashMap
        Map<String, Object> rawData = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

        // 提取并移除类型元数据
        Map<String, String> typeMetadata = extractTypeMetadata(rawData);

        // 使用类型元数据重新反序列化各个字段
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : rawData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // 跳过类型元数据字段本身
            if (StateTypeRegistry.TYPE_METADATA_KEY.equals(key)) {
                continue;
            }

            // 如果有注册的类型信息，使用 TypeReference 重新从原始 JSON 反序列化
            if (typeMetadata.containsKey(key) && registry.isRegistered(key)) {
                result.put(key, deserializeFromOriginalJson(json, key));
            } else {
                // 否则直接使用原始值
                result.put(key, value);
            }
        }

        return result;
    }

    /**
     * 从原始 JSON 中提取并反序列化指定键的值
     * <p>
     * 这个方法直接从原始 JSON 中提取字段值，而不是使用已经被反序列化的 LinkedHashMap。
     * 这样可以确保 MessageJsonModule 正确处理 Message 类型的反序列化。
     * </p>
     *
     * @param originalJson 原始 JSON 字符串
     * @param key          要提取的键
     * @return 反序列化后的对象
     */
    private Object deserializeFromOriginalJson(String originalJson, String key) {
        try {
            // 将原始 JSON 解析为 JsonNode
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(originalJson);

            // 获取指定键的值
            com.fasterxml.jackson.databind.JsonNode valueNode = rootNode.get(key);
            if (valueNode == null || valueNode.isNull()) {
                return null;
            }

            // 获取注册的 TypeReference
            TypeReference<?> typeRef = registry.getTypeReference(key)
                    .orElseThrow(() -> new IllegalArgumentException("No type registered for key: " + key));

            // 使用 ObjectMapper 和 TypeReference 直接反序列化
            // 这里 MessageJsonModule 会被正确调用
            return objectMapper.readValue(valueNode.toString(), typeRef);
        } catch (Exception e) {
            log.warn("Failed to deserialize key '{}' from original JSON: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 从原始数据中提取类型元数据
     *
     * @param rawData 原始数据映射
     * @return 类型元数据映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractTypeMetadata(Map<String, Object> rawData) {
        Object metadataObj = rawData.remove(StateTypeRegistry.TYPE_METADATA_KEY);
        if (metadataObj instanceof Map) {
            return (Map<String, String>) metadataObj;
        }
        return new HashMap<>();
    }

    /**
     * 获取使用的 ObjectMapper
     *
     * @return ObjectMapper
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * 获取使用的类型注册表
     *
     * @return StateTypeRegistry
     */
    public StateTypeRegistry getRegistry() {
        return registry;
    }
}
