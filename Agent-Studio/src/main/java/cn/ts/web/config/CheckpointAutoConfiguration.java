package cn.ts.web.config;

import cn.ts.graph.checkpoint.CheckpointConfig;
import cn.ts.graph.checkpoint.CheckpointManager;
import cn.ts.graph.checkpoint.CheckpointManagerImpl;
import cn.ts.graph.checkpoint.CheckpointStorage;
import cn.ts.graph.checkpoint.MemoryCheckpointStorage;
import cn.ts.web.checkpoint.DatabaseCheckpointStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Set;

/**
 * Checkpoint 自动配置类
 * <p>
 * 配置 CheckpointManager 和相关 Bean
 * 支持内存和数据库两种存储方式
 * </p>
 *
 * @author tianshuo
 */
@Configuration
@EnableConfigurationProperties(CheckpointAutoConfiguration.CheckpointProperties.class)
public class CheckpointAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CheckpointAutoConfiguration.class);

    /**
     * 创建 CheckpointConfig Bean
     *
     * @param properties 配置属性
     * @return CheckpointConfig
     */
    @Bean
    public CheckpointConfig checkpointConfig(CheckpointProperties properties) {
        log.info("初始化 Checkpoint 配置: storage={}, strategy={}, maxHistory={}, ttl={}",
                properties.getStorage().getType(),
                properties.getStorage().getStrategy(),
                properties.getStorage().getMaxHistorySize(),
                properties.getStorage().getTtl());

        return CheckpointConfig.builder()
                .strategy(properties.getStorage().getStrategy())
                .checkpointNodes(Set.of())
                .maxHistorySize(properties.getStorage().getMaxHistorySize())
                .ttl(parseDuration(properties.getStorage().getTtl()))
                .build();
    }

    /**
     * 创建内存存储 Bean
     * <p>
     * 当没有配置数据库存储时使用
     * </p>
     *
     * @return MemoryCheckpointStorage
     */
    @Bean
    @ConditionalOnMissingBean(DatabaseCheckpointStorage.class)
    public CheckpointStorage memoryCheckpointStorage() {
        log.info("使用内存 Checkpoint 存储");
        return new MemoryCheckpointStorage();
    }

    /**
     * 创建 CheckpointManager Bean
     *
     * @param storage 存储实现
     * @param config  检查点配置
     * @return CheckpointManager
     */
    @Bean
    public CheckpointManager checkpointManager(CheckpointStorage storage, CheckpointConfig config) {
        log.info("初始化 CheckpointManager: storage={}, config={}",
                storage.getClass().getSimpleName(), config);

        return new CheckpointManagerImpl(storage, config);
    }

    /**
     * 解析时间 duration
     *
     * @param duration 时间字符串（如 "7d", "24h", "30m"）
     * @return Duration
     */
    private Duration parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            return Duration.ofDays(7);
        }

        try {
            // 支持格式: 7d, 24h, 30m, 60s
            long value = Long.parseLong(duration.substring(0, duration.length() - 1));
            String unit = duration.substring(duration.length() - 1);

            return switch (unit.toLowerCase()) {
                case "d" -> Duration.ofDays(value);
                case "h" -> Duration.ofHours(value);
                case "m" -> Duration.ofMinutes(value);
                case "s" -> Duration.ofSeconds(value);
                default -> Duration.ofDays(7);
            };
        } catch (Exception e) {
            log.warn("无法解析 duration: {}, 使用默认值 7d", duration);
            return Duration.ofDays(7);
        }
    }

    /**
     * Checkpoint 配置属性
     */
    @ConfigurationProperties(prefix = "checkpoint.storage")
    public static class CheckpointProperties {

        private Storage storage = new Storage();

        public Storage getStorage() {
            return storage;
        }

        public void setStorage(Storage storage) {
            this.storage = storage;
        }

        public static class Storage {
            /**
             * 存储类型: memory | database
             */
            private StorageType type = StorageType.memory;

            /**
             * 检查点策略: ALWAYS | MANUAL | ERROR | ON_SPECIFIC_NODES
             */
            private CheckpointConfig.CheckpointStrategy strategy =
                    CheckpointConfig.CheckpointStrategy.MANUAL;

            /**
             * 最大历史记录数
             */
            private int maxHistorySize = 100;

            /**
             * TTL（生存时间）
             */
            private String ttl = "7d";

            public StorageType getType() {
                return type;
            }

            public void setType(StorageType type) {
                this.type = type;
            }

            public CheckpointConfig.CheckpointStrategy getStrategy() {
                return strategy;
            }

            public void setStrategy(CheckpointConfig.CheckpointStrategy strategy) {
                this.strategy = strategy;
            }

            public int getMaxHistorySize() {
                return maxHistorySize;
            }

            public void setMaxHistorySize(int maxHistorySize) {
                this.maxHistorySize = maxHistorySize;
            }

            public String getTtl() {
                return ttl;
            }

            public void setTtl(String ttl) {
                this.ttl = ttl;
            }
        }

        public enum StorageType {
            memory, database
        }
    }
}
