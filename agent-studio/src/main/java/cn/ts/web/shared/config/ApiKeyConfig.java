package cn.ts.web.shared.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * API 密钥配置类
 * 从配置文件或环境变量读取各类 API 密钥
 *
 * @author tianshuo
 */
@Configuration
@ConfigurationProperties(prefix = "api.keys")
@Data
public class ApiKeyConfig {

    /**
     * API 密钥映射
     * 键为服务名称（如 amap、openai 等），值为对应的 API 密钥
     */
    private Map<String, String> keys = new HashMap<>();

    /**
     * 获取指定服务的 API 密钥
     *
     * @param serviceName 服务名称（如 "amap"）
     * @return API 密钥，如果不存在则返回 null
     */
    public String getKey(String serviceName) {
        return keys.get(serviceName);
    }

    /**
     * 获取指定服务的 API 密钥，如果不存在则返回默认值
     *
     * @param serviceName 服务名称
     * @param defaultValue 默认值
     * @return API 密钥或默认值
     */
    public String getKeyOrDefault(String serviceName, String defaultValue) {
        return keys.getOrDefault(serviceName, defaultValue);
    }

    /**
     * 检查指定服务的 API 密钥是否存在
     *
     * @param serviceName 服务名称
     * @return 是否存在该服务的 API 密钥
     */
    public boolean hasKey(String serviceName) {
        return keys.containsKey(serviceName) && keys.get(serviceName) != null && !keys.get(serviceName).isBlank();
    }

    /**
     * 便捷方法：获取高德地图 API 密钥
     */
    public String getAmapKey() {
        return getKey("amap");
    }

    /**
     * 便捷方法：获取 OpenAI API 密钥
     */
    public String getOpenaiKey() {
        return getKey("openai");
    }
}
