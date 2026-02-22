package cn.ts.web.rag.service;

import cn.ts.web.rag.entity.KnowledgeBaseEntity;
import cn.ts.web.rag.mapper.KnowledgeBaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 知识库服务
 * <p>
 * 提供知识库管理的业务逻辑
 * </p>
 *
 * @author tianshuo
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    /**
     * 获取所有知识库
     */
    public List<KnowledgeBaseEntity> getAllKnowledgeBases() {
        return knowledgeBaseMapper.selectAll();
    }

    /**
     * 根据ID获取知识库
     */
    public Optional<KnowledgeBaseEntity> getKnowledgeBaseById(String kbId) {
        return knowledgeBaseMapper.selectByKbId(kbId);
    }

    /**
     * 创建知识库
     */
    @Transactional
    public KnowledgeBaseEntity createKnowledgeBase(KnowledgeBaseEntity entity) {
        // 设置默认值
        if (entity.getStatus() == null) {
            entity.setStatus("ACTIVE");
        }
        if (entity.getEmbeddingModel() == null) {
            entity.setEmbeddingModel("text-embedding-3-small");
        }
        if (entity.getDimension() == null) {
            entity.setDimension(1536);
        }
        if (entity.getDocumentCount() == null) {
            entity.setDocumentCount(0);
        }
        if (entity.getTotalChunks() == null) {
            entity.setTotalChunks(0);
        }
        if (entity.getCreatedBy() == null) {
            entity.setCreatedBy("system");
        }
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        knowledgeBaseMapper.insert(entity);
        log.info("创建知识库成功: kbId={}, kbName={}", entity.getKbId(), entity.getKbName());
        return entity;
    }

    /**
     * 更新知识库
     */
    @Transactional
    public Optional<KnowledgeBaseEntity> updateKnowledgeBase(KnowledgeBaseEntity entity) {
        Optional<KnowledgeBaseEntity> existing = knowledgeBaseMapper.selectByKbId(entity.getKbId());
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        entity.setUpdatedAt(Instant.now());
        knowledgeBaseMapper.updateByKbId(entity);
        log.info("更新知识库成功: kbId={}", entity.getKbId());
        return knowledgeBaseMapper.selectByKbId(entity.getKbId());
    }

    /**
     * 删除知识库
     */
    @Transactional
    public boolean deleteKnowledgeBase(String kbId) {
        int deleted = knowledgeBaseMapper.deleteByKbId(kbId);
        if (deleted > 0) {
            log.info("删除知识库成功: kbId={}", kbId);
            return true;
        }
        return false;
    }

    /**
     * 检查知识库是否存在
     */
    public boolean existsByKbId(String kbId) {
        return knowledgeBaseMapper.countByKbId(kbId) > 0;
    }

    /**
     * 更新文档数量
     */
    @Transactional
    public void updateDocumentCount(String kbId, Integer documentCount) {
        knowledgeBaseMapper.updateDocumentCount(kbId, documentCount);
    }

    /**
     * 更新总块数
     */
    @Transactional
    public void updateTotalChunks(String kbId, Integer totalChunks) {
        knowledgeBaseMapper.updateTotalChunks(kbId, totalChunks);
    }

    /**
     * 增加文档数量
     */
    @Transactional
    public void incrementDocumentCount(String kbId, Integer increment) {
        knowledgeBaseMapper.incrementDocumentCount(kbId, increment);
    }

    /**
     * 增加总块数
     */
    @Transactional
    public void incrementTotalChunks(String kbId, Integer increment) {
        knowledgeBaseMapper.incrementTotalChunks(kbId, increment);
    }

    /**
     * 根据状态查询知识库
     */
    public List<KnowledgeBaseEntity> getKnowledgeBasesByStatus(String status) {
        return knowledgeBaseMapper.selectByStatus(status);
    }
}
