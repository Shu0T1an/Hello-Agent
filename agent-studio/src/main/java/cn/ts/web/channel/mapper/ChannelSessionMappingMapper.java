package cn.ts.web.channel.mapper;

import cn.ts.web.channel.entity.ChannelSessionMappingEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChannelSessionMappingMapper {

    @Select("SELECT * FROM channel_session_mapping " +
            "WHERE channel_type=#{channelType} AND external_session_id=#{externalSessionId}")
    ChannelSessionMappingEntity selectByExternal(
            @Param("channelType") String channelType,
            @Param("externalSessionId") String externalSessionId
    );

    @Insert("INSERT INTO channel_session_mapping (channel_type, external_session_id, internal_session_id) " +
            "VALUES (#{channelType}, #{externalSessionId}, #{internalSessionId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChannelSessionMappingEntity entity);

    @Update("UPDATE channel_session_mapping SET internal_session_id=#{internalSessionId} " +
            "WHERE channel_type=#{channelType} AND external_session_id=#{externalSessionId}")
    int updateInternalSessionId(
            @Param("channelType") String channelType,
            @Param("externalSessionId") String externalSessionId,
            @Param("internalSessionId") String internalSessionId
    );
}
