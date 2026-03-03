package cn.ts.web.channel.mapper;

import cn.ts.web.channel.entity.ChannelConfigEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ChannelConfigMapper {

    @Insert("INSERT INTO channel_config (channel_name, channel_type, config_json, enabled, status) " +
            "VALUES (#{channelName}, #{channelType}, CAST(#{configJson} AS jsonb), #{enabled}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChannelConfigEntity entity);

    @Update("UPDATE channel_config SET channel_name=#{channelName}, channel_type=#{channelType}, config_json=CAST(#{configJson} AS jsonb), enabled=#{enabled}, status=#{status} WHERE id=#{id}")
    int updateById(ChannelConfigEntity entity);

    @Delete("DELETE FROM channel_config WHERE id=#{id}")
    int deleteById(Long id);

    @Select("SELECT * FROM channel_config WHERE id=#{id}")
    ChannelConfigEntity selectById(Long id);

    @Select("SELECT * FROM channel_config ORDER BY created_at DESC")
    List<ChannelConfigEntity> selectAll();

    @Select("SELECT * FROM channel_config WHERE enabled = TRUE ORDER BY created_at DESC")
    List<ChannelConfigEntity> selectEnabled();

    @Select("SELECT count(*) FROM channel_config WHERE channel_name=#{channelName} AND id != #{id}")
    int countNameExcludeId(@Param("channelName") String channelName, @Param("id") Long id);

    @Select("SELECT count(*) FROM channel_config WHERE channel_name=#{channelName}")
    int countName(String channelName);
}
