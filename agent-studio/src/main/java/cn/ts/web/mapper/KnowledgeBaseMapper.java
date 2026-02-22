package cn.ts.web.mapper;

import cn.ts.web.entity.KnowledgeBaseEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;

/**
 * 知识库 Mapper
 * <p>
 * 提供 knowledge_base 表的 CRUD 操作
 * </p>
 *
 * @author tianshuo
 */
@Mapper
public interface KnowledgeBaseMapper {

    /**
     * 插入知识库
     */
    @Insert("INSERT INTO knowledge_base (kb_id, kb_name, description, embedding_model, dimension, status, document_count, total_chunks, created_by) " +
            "VALUES (#{kbId}, #{kbName}, #{description}, #{embeddingModel}, #{dimension}, #{status}, #{documentCount}, #{totalChunks}, #{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KnowledgeBaseEntity entity);

    /**
     * 根据 kb_id 更新知识库
     */
    @Update("UPDATE knowledge_base SET " +
            "kb_name = #{kbName}, " +
            "description = #{description}, " +
            "embedding_model = #{embeddingModel}, " +
            "dimension = #{dimension}, " +
            "status = #{status}, " +
            "document_count = #{documentCount}, " +
            "total_chunks = #{totalChunks} " +
            "WHERE kb_id = #{kbId}")
    int updateByKbId(KnowledgeBaseEntity entity);

    /**
     * 删除知识库
     */
    @Delete("DELETE FROM knowledge_base WHERE kb_id = #{kbId}")
    int deleteByKbId(String kbId);

    /**
     * 根据 kb_id 查询
     */
    @Select("SELECT * FROM knowledge_base WHERE kb_id = #{kbId}")
    Optional<KnowledgeBaseEntity> selectByKbId(String kbId);

    /**
     * 查询所有知识库
     */
    @Select("SELECT * FROM knowledge_base ORDER BY created_at DESC")
    List<KnowledgeBaseEntity> selectAll();

    /**
     * 根据 status 查询知识库
     */
    @Select("SELECT * FROM knowledge_base WHERE status = #{status} ORDER BY created_at DESC")
    List<KnowledgeBaseEntity> selectByStatus(String status);

    /**
     * 检查 kb_id 是否存在
     */
    @Select("SELECT COUNT(*) FROM knowledge_base WHERE kb_id = #{kbId}")
    int countByKbId(String kbId);

    /**
     * 更新文档数量
     */
    @Update("UPDATE knowledge_base SET document_count = #{documentCount} WHERE kb_id = #{kbId}")
    int updateDocumentCount(@Param("kbId") String kbId, @Param("documentCount") Integer documentCount);

    /**
     * 更新总块数
     */
    @Update("UPDATE knowledge_base SET total_chunks = #{totalChunks} WHERE kb_id = #{kbId}")
    int updateTotalChunks(@Param("kbId") String kbId, @Param("totalChunks") Integer totalChunks);

    /**
     * 增加文档数量
     */
    @Update("UPDATE knowledge_base SET document_count = document_count + #{increment} WHERE kb_id = #{kbId}")
    int incrementDocumentCount(@Param("kbId") String kbId, @Param("increment") Integer increment);

    /**
     * 增加总块数
     */
    @Update("UPDATE knowledge_base SET total_chunks = total_chunks + #{increment} WHERE kb_id = #{kbId}")
    int incrementTotalChunks(@Param("kbId") String kbId, @Param("increment") Integer increment);
}
