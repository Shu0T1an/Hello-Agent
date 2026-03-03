package cn.ts.web.cron.mapper;

import cn.ts.web.cron.entity.CronJobRunEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface CronJobRunMapper {

    @Insert("INSERT INTO cron_job_run (job_id, trigger_type, status, started_at, execution_id, error_message) " +
            "VALUES (#{jobId}, #{triggerType}, #{status}, #{startedAt}, #{executionId}, #{errorMessage})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CronJobRunEntity entity);

    @Update("UPDATE cron_job_run SET status=#{status}, finished_at=#{finishedAt}, error_message=#{errorMessage} WHERE id=#{id}")
    int finish(@Param("id") Long id,
               @Param("status") String status,
               @Param("finishedAt") Instant finishedAt,
               @Param("errorMessage") String errorMessage);

    @Select("SELECT * FROM cron_job_run WHERE job_id=#{jobId} ORDER BY started_at DESC LIMIT #{limit}")
    List<CronJobRunEntity> selectByJobId(@Param("jobId") Long jobId, @Param("limit") int limit);
}
