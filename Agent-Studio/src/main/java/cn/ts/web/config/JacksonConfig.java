package cn.ts.web.config;

import cn.ts.graph.serialization.MessageJsonModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson 配置
 * <p>
 * 配置 ObjectMapper 以支持：
 * <ul>
 *   <li>Java 8 日期时间类型（Instant 等）</li>
 *   <li>Spring AI Message 类的正确反序列化</li>
 *   <li>漂亮的 JSON 输出（开发环境）</li>
 * </ul>
 * </p>
 *
 * @author tianshuo
 */
@Configuration
public class JacksonConfig {

    /**
     * 创建配置好的 ObjectMapper Bean
     * <p>
     * 注册 MessageJsonModule 以正确处理 Message 类的序列化和反序列化。
     * </p>
     *
     * @return ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 注册 Java 8 日期时间模块
        mapper.registerModule(new JavaTimeModule());

        // 注册 Message 反序列化模块
        mapper.registerModule(new MessageJsonModule());

        // 配置序列化选项
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }
}
