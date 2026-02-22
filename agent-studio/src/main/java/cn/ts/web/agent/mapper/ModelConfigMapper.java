package cn.ts.web.agent.mapper;

import cn.ts.web.agent.entity.ModelConfigEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

/**
 * 模型配置 Mapper
 */
@Mapper
public interface ModelConfigMapper {

    /**
     * 插入模型配置
     */
    @Insert("INSERT INTO model_config (model_name, display_name, provider, model_id, base_url, api_key_encrypted, is_active) " +
            "VALUES (#{modelName}, #{displayName}, #{provider}, #{modelId}, #{baseUrl}, #{apiKeyEncrypted}, #{isActive})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ModelConfigEntity entity);

    /**
     * 更新模型配置
     */
    @Update("UPDATE model_config SET " +
            "display_name = #{displayName}, " +
            "provider = #{provider}, " +
            "model_id = #{modelId}, " +
            "base_url = #{baseUrl}, " +
            "api_key_encrypted = #{apiKeyEncrypted}, " +
            "is_active = #{isActive} " +
            "WHERE id = #{id}")
    int updateById(ModelConfigEntity entity);

    /**
     * 删除模型配置
     */
    @Delete("DELETE FROM model_config WHERE id = #{id}")
    int deleteById(Long id);

    /**
     * 根据ID查询
     */
    @Select("SELECT * FROM model_config WHERE id = #{id}")
    ModelConfigEntity selectById(Long id);

    /**
     * 根据模型名称查询
     */
    @Select("SELECT * FROM model_config WHERE model_name = #{modelName}")
    Optional<ModelConfigEntity> selectByModelName(String modelName);

    /**
     * 查询所有模型配置
     */
    @Select("SELECT * FROM model_config ORDER BY created_at DESC")
    List<ModelConfigEntity> selectAll();

    /**
     * 查询激活的模型配置
     */
    @Select("SELECT * FROM model_config WHERE is_active = TRUE ORDER BY created_at DESC")
    List<ModelConfigEntity> selectActive();

    /**
     * 根据提供商查询
     */
    @Select("SELECT * FROM model_config WHERE provider = #{provider} ORDER BY created_at DESC")
    List<ModelConfigEntity> selectByProvider(String provider);

    /**
     * 检查模型名称是否存在
     */
    @Select("SELECT COUNT(*) FROM model_config WHERE model_name = #{modelName}")
    int countByModelName(String modelName);

    /**
     * 检查模型名称是否存在（排除指定ID）
     */
    @Select("SELECT COUNT(*) FROM model_config WHERE model_name = #{modelName} AND id != #{id}")
    int countByModelNameExcludeId(@Param("modelName") String modelName, @Param("id") Long id);
}
