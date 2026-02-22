-- =============================================
-- RAG功能 - PgVector 表结构
-- =============================================

-- 1. 启用 PgVector 扩展（如果尚未启用）
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 创建向量存储表
-- ----------------------------
-- Table structure for vector_store
-- ----------------------------
DROP TABLE IF EXISTS "public"."vector_store";
CREATE TABLE "public"."vector_store" (
                                         "id" uuid NOT NULL DEFAULT uuid_generate_v4(),
                                         "content" text COLLATE "pg_catalog"."default",
                                         "metadata" json,
                                         "embedding" "public"."vector"
)
;

-- 6. 创建知识库配置表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    kb_id VARCHAR(100) NOT NULL UNIQUE,
    kb_name VARCHAR(200) NOT NULL,
    description TEXT,
    embedding_model VARCHAR(100) DEFAULT 'text-embedding-3-small',
    dimension INT DEFAULT 1536,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    document_count INT DEFAULT 0,
    total_chunks INT DEFAULT 0,
    created_by VARCHAR(50) DEFAULT 'system',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 7. 创建文档记录表
CREATE TABLE IF NOT EXISTS document_record (
    id BIGSERIAL PRIMARY KEY,
    doc_id VARCHAR(100) NOT NULL UNIQUE,
    kb_id VARCHAR(100) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path TEXT,
    file_size BIGINT,
    file_type VARCHAR(50),
    chunk_count INT DEFAULT 0,
    upload_status VARCHAR(20) DEFAULT 'PROCESSING',
    error_message TEXT,
    uploaded_by VARCHAR(50) DEFAULT 'system',
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_time TIMESTAMP,
    CONSTRAINT fk_doc_kb FOREIGN KEY (kb_id) REFERENCES knowledge_base(kb_id) ON DELETE CASCADE
);

-- 8. 创建文档记录索引
CREATE INDEX IF NOT EXISTS document_record_kb_id_idx ON document_record(kb_id);
CREATE INDEX IF NOT EXISTS document_record_upload_status_idx ON document_record(upload_status);

-- 9. 添加注释
COMMENT ON TABLE vector_store IS '向量存储表，用于RAG检索';
COMMENT ON TABLE knowledge_base IS '知识库配置表';
COMMENT ON TABLE document_record IS '文档记录表';

-- 10. 创建更新时间触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 11. 应用触发器
DROP TRIGGER IF EXISTS update_vector_store_updated_at ON vector_store;
CREATE TRIGGER update_vector_store_updated_at
    BEFORE UPDATE ON vector_store
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_knowledge_base_updated_at ON knowledge_base;
CREATE TRIGGER update_knowledge_base_updated_at
    BEFORE UPDATE ON knowledge_base
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 12. 创建默认知识库
INSERT INTO knowledge_base (kb_id, kb_name, description, status)
VALUES ('default', '默认知识库', '系统默认知识库', 'ACTIVE')
ON CONFLICT (kb_id) DO NOTHING;
