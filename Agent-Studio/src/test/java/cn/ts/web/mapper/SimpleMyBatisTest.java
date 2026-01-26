package cn.ts.web.mapper;

import cn.ts.web.entity.ModelConfigEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 简单的 MyBatis 连接测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SimpleMyBatisTest {

    @Autowired(required = false)
    private ModelConfigMapper modelConfigMapper;

    @Test
    void testContextLoads() {
        assertNotNull(modelConfigMapper);
        System.out.println("✓ Spring 上下文加载成功");
        System.out.println("✓ ModelConfigMapper 注入成功");
    }

    @Test
    void testDatabaseConnection() {
        assertNotNull(modelConfigMapper);

        List<ModelConfigEntity> allModels = modelConfigMapper.selectAll();
        assertNotNull(allModels);
        System.out.println("✓ 数据库连接成功");
        System.out.println("✓ 当前模型配置数量: " + allModels.size());
    }

    @Test
    void testInsertAndSelect() {
        assertNotNull(modelConfigMapper);

        // 创建测试数据
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setModelName("test-simple-connection");
        entity.setDisplayName("Simple Test Model");
        entity.setProvider("test-provider");
        entity.setModelId("test-model-id");
        entity.setBaseUrl("https://test.api.com");
        entity.setApiKeyEncrypted("test-encrypted-key");
        entity.setIsActive(true);

        // 插入
        int insertResult = modelConfigMapper.insert(entity);
        assertEquals(1, insertResult);
        assertNotNull(entity.getId());
        System.out.println("✓ 插入成功，ID: " + entity.getId());

        // 查询
        ModelConfigEntity selected = modelConfigMapper.selectById(entity.getId());
        assertNotNull(selected);
        assertEquals("test-simple-connection", selected.getModelName());
        System.out.println("✓ 查询成功，模型名称: " + selected.getModelName());

        // 按名称查询
        var byName = modelConfigMapper.selectByModelName("test-simple-connection");
        assertTrue(byName.isPresent());
        System.out.println("✓ 按名称查询成功");
    }
}
