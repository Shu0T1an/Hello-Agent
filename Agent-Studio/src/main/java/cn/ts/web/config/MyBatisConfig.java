package cn.ts.web.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * MyBatis 配置类
 * 配置 MyBatis 与 Spring 的集成
 */
@Configuration
@MapperScan("cn.ts.web.mapper")
public class MyBatisConfig {

    /**
     * 配置 SqlSessionFactory
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);

        // 配置 Mapper XML 文件位置（如果存在）
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath:mapper/**/*.xml");
            if (resources.length > 0) {
                factoryBean.setMapperLocations(resources);
            }
        } catch (Exception e) {
            // 没有找到 XML 文件，使用注解方式
        }

        // 配置类型别名包
        factoryBean.setTypeAliasesPackage("cn.ts.web.entity");

        // MyBatis 配置
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true); // 驼峰命名转换
        configuration.setCallSettersOnNulls(true); // 空值调用 setter
        configuration.setLogImpl(org.apache.ibatis.logging.slf4j.Slf4jImpl.class); // 日志实现
        factoryBean.setConfiguration(configuration);

        return factoryBean.getObject();
    }
}
