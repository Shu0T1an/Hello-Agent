package cn.ts.graph.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 类型化状态序列化器
 * <p>
 * 在序列化 State 时附加类型元数据，确保反序列化时能正确还原类型。
 * 通过将类型信息作为特殊字段嵌入 JSON，解决泛型类型擦除问题。
 * </p>
 * <p>
 * 序列化后的 JSON 结构示例：
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
 * @see TypedStateDeserializer
 */
public class TypedStateSerializer {

    private static final Logger log = LoggerFactory.getLogger(TypedStateSerializer.class);

    private final ObjectMapper objectMapper;
    private final StateTypeRegistry registry;

    /**
     * 创建序列化器
     *
     * @param ObjectMapper Jackson ObjectMapper
     */
    public TypedStateSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.registry = StateTypeRegistry.getInstance();
    }

    /**
     * 创建序列化器（使用自定义注册表）
     *
     * @param ObjectMapper Jackson ObjectMapper
     * @param registry      类型注册表
     */
    public TypedStateSerializer(ObjectMapper objectMapper, StateTypeRegistry registry) {
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    /**
     * 序列化状态并附加类型元数据
     *
     * @param state 状态数据
     * @return 包含类型元数据的 JSON 字符串
     * @throws JsonProcessingException 序列化失败时抛出
     */
    public String serializeWithTypeMetadata(Map<String, Object> state) throws JsonProcessingException {
        if (state == null || state.isEmpty()) {
            return "{}";
        }

        // 创建可修改的状态副本
        Map<String, Object> stateCopy = new LinkedHashMap<>(state);

        // 生成类型元数据
        Map<String, String> typeMetadata = generateTypeMetadata(state);

        // 将类型元数据添加到状态中
        if (!typeMetadata.isEmpty()) {
            stateCopy.put(StateTypeRegistry.TYPE_METADATA_KEY, typeMetadata);
        }

        // 序列化为 JSON
        return objectMapper.writeValueAsString(stateCopy);
    }

    /**
     * 为状态数据生成类型元数据
     * <p>
     * 遍历状态中的每个键，如果在注册表中找到对应的类型，则记录类型信息。
     * </p>
     *
     * @param state 状态数据
     * @return 类型元数据映射（键 -> 类型字符串）
     */
    private Map<String, String> generateTypeMetadata(Map<String, Object> state) {
        Map<String, String> metadata = new HashMap<>();

        for (String key : state.keySet()) {
            registry.getTypeReference(key).ifPresent(typeRef -> {
                // 将 TypeReference 转换为可序列化的类型字符串
                String typeString = typeRef.getType().toString();
                // 简化类型字符串表示（移除 package 路径等）
                metadata.put(key, typeString);
            });
        }

        return metadata;
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
