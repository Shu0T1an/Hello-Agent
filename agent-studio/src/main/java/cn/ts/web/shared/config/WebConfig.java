package cn.ts.web.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Web 配置类
 * <p>
 * 配置 CORS 和其他 Web 相关设置
 * </p>
 *
 * @author tianshuo
 */
@Configuration
public class WebConfig {

    /**
     * 配置 CORS 过滤器
     * <p>
     * 项目同时使用 Spring MVC 和 WebFlux，需要使用 CorsFilter 来确保所有请求都能正确处理 CORS
     * </p>
     */
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // 允许所有来源（开发环境）
        config.addAllowedOriginPattern("*");

        // 允许所有请求头
        config.addAllowedHeader("*");

        // 允许所有 HTTP 方法
        config.addAllowedMethod("*");

        // 允许携带凭证
        config.setAllowCredentials(true);

        // 预飞请求的有效期（秒）
        config.setMaxAge(3600L);

        // 对所有路径应用 CORS 配置
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
