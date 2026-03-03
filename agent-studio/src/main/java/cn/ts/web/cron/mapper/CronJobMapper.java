package cn.ts.web.cron.mapper;

import cn.ts.web.cron.entity.CronJobEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.time.Instant;
import java.util.List;

@Mapper
public interface CronJobMapper {

    @Insert("INSERT INTO cron_job (job_name, cron_expression, zone_id, agent_name, session_id, input_text, enabled, max_retry_count, retry_interval_seconds, last_status) " +
            "VALUES (#{jobName}, #{cronExpression}, #{zoneId}, #{agentName}, #{sessionId}, #{inputText}, #{enabled}, #{maxRetryCount}, #{retryIntervalSeconds}, #{lastStatus})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CronJobEntity entity);

    @Update("UPDATE cron_job SET job_name=#{jobName}, cron_expression=#{cronExpression}, zone_id=#{zoneId}, agent_name=#{agentName}, session_id=#{sessionId}, input_text=#{inputText}, enabled=#{enabled}, max_retry_count=#{maxRetryCount}, retry_interval_seconds=#{retryIntervalSeconds}, last_status=#{lastStatus}, last_error=#{lastError}, next_run_at=#{nextRunAt} WHERE id=#{id}")
    int updateById(CronJobEntity entity);

    @Delete("DELETE FROM cron_job WHERE id=#{id}")
    int deleteById(Long id);

    @Select("SELECT * FROM cron_job WHERE id=#{id}")
    CronJobEntity selectById(Long id);

    @Select("SELECT * FROM cron_job ORDER BY created_at DESC")
    List<CronJobEntity> selectAll();

    @Select("SELECT * FROM cron_job WHERE enabled=TRUE ORDER BY created_at DESC")
    List<CronJobEntity> selectEnabled();

    @Select("SELECT COUNT(*) FROM cron_job WHERE job_name=#{jobName}")
    int countByName(String jobName);

    @Select("SELECT COUNT(*) FROM cron_job WHERE job_name=#{jobName} AND id != #{id}")
    int countByNameExcludeId(@Param("jobName") String jobName, @Param("id") Long id);

    @Update("UPDATE cron_job SET last_status=#{lastStatus}, last_error=#{lastError}, last_run_at=#{lastRunAt}, next_run_at=#{nextRunAt} WHERE id=#{id}")
    int updateRunMetadata(@Param("id") Long id,
                          @Param("lastStatus") String lastStatus,
                          @Param("lastError") String lastError,
                          @Param("lastRunAt") Instant lastRunAt,
                          @Param("nextRunAt") Instant nextRunAt);
}
