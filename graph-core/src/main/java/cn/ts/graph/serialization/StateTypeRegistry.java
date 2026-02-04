package cn.ts.graph.serialization;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.ai.chat.messages.Message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 状态类型注册表
 * <p>
 * 维护 State 键到 Java 类型的映射关系，用于在序列化和反序列化过程中保持类型信息。
 * 解决泛型类型擦除导致的类型丢失问题（如 List&lt;Message&gt; 变成 List&lt;LinkedHashMap&gt;）。
 * </p>
 *
 * @author tianshuo
 * @see TypedStateSerializer
 * @see TypedStateDeserializer
 */
public class StateTypeRegistry {

    /**
     * 类型元数据键，用于在 JSON 中存储类型信息
     */
    public static final String TYPE_METADATA_KEY = "__type_metadata__";

    private final Map<String, TypeReference<?>> typeMap = new ConcurrentHashMap<>();

    // 单例实例
    private static volatile StateTypeRegistry instance;

    private StateTypeRegistry() {
        registerDefaultTypes();
    }

    /**
     * 获取单例实例（双重检查锁定）
     *
     * @return StateTypeRegistry 实例
     */
    public static StateTypeRegistry getInstance() {
        if (instance == null) {
            synchronized (StateTypeRegistry.class) {
                if (instance == null) {
                    instance = new StateTypeRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * 注册状态键到类型引用的映射
     *
     * @param key           状态键
     * @param typeReference 类型引用
     * @param <T>           类型参数
     */
    public <T> void register(String key, TypeReference<T> typeReference) {
        typeMap.put(key, typeReference);
    }

    /**
     * 获取状态键对应的类型引用
     *
     * @param key 状态键
     * @return 类型引用的 Optional 包装
     */
    public Optional<TypeReference<?>> getTypeReference(String key) {
        return Optional.ofNullable(typeMap.get(key));
    }

    /**
     * 检查是否已注册某个键的类型
     *
     * @param key 状态键
     * @return 是否已注册
     */
    public boolean isRegistered(String key) {
        return typeMap.containsKey(key);
    }

    /**
     * 获取所有已注册的类型映射
     *
     * @return 类型映射的副本
     */
    public Map<String, TypeReference<?>> getAllTypes() {
        return new HashMap<>(typeMap);
    }

    /**
     * 清除所有类型注册
     */
    public void clear() {
        typeMap.clear();
    }

    /**
     * 注销某个键的类型
     *
     * @param key 状态键
     */
    public void unregister(String key) {
        typeMap.remove(key);
    }

    /**
     * 注册默认类型
     * <p>
     * 默认注册 messages → List&lt;Message&gt;
     * </p>
     */
    private void registerDefaultTypes() {
        register("messages", new TypeReference<List<Message>>() {
        });
    }
}
