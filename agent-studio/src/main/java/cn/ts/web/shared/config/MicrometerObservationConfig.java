package cn.ts.web.shared.config;

import cn.ts.graph.observation.GraphObservationLifecycleListener;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer 可观测性配置
 * <p>
 * 配置 ObservationRegistry 和可观测性监听器
 * </p>
 *
 * @author tianshuo
 */
@Configuration
public class MicrometerObservationConfig {

    /**
     * 创建 ObservationRegistry Bean
     * <p>
     * 用于 Micrometer Observation API，支持图执行的可观测性
     * </p>
     *
     * @return ObservationRegistry 实例
     */
    @Bean
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }

    /**
     * 创建图可观测性监听器 Bean
     * <p>
     * 监听图执行的生命周期事件，收集 Metrics 和 Tracing 数据
     * </p>
     *
     * @param observationRegistry ObservationRegistry Bean
     * @return GraphObservationLifecycleListener 实例
     */
    @Bean
    public GraphObservationLifecycleListener graphObservationLifecycleListener(
            ObservationRegistry observationRegistry) {
        return new GraphObservationLifecycleListener(observationRegistry);
    }
}
