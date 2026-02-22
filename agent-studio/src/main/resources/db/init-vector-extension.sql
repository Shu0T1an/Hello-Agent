-- PgVector 扩展初始化脚本
-- 在 Docker 容器启动时自动执行

-- 启用 vector 扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS vector_store (
                                            id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
                                            content text,
                                            metadata json,
                                            embedding vector(1536) -- 1536 is the default embedding dimension
);

CREATE INDEX ON vector_store USING HNSW (embedding vector_cosine_ops);
-- 验证扩展已安装
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
