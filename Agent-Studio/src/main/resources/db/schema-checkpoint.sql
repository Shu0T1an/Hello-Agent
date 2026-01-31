-- =============================================
-- Checkpoint 系统数据库表结构
-- PostgreSQL 版本
-- 用于统一会话和状态管理
-- =============================================

-- 删除已存在的表（如果需要）
DROP TABLE IF EXISTS checkpoint_snapshots CASCADE;

-- 1. Checkpoint 快照表
CREATE TABLE IF NOT EXISTS checkpoint_snapshots (
    id BIGSERIAL PRIMARY KEY,
    thread_id VARCHAR(100) NOT NULL,
    checkpoint_id VARCHAR(100) NOT NULL,
    node_id VARCHAR(100),
    parent_id VARCHAR(100),
    state_json JSONB NOT NULL,
    metadata_json JSONB NOT NULL,
    source VARCHAR(20) NOT NULL,  -- auto/manual/error/restore
    iteration INT NOT NULL,
    is_latest BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (thread_id, checkpoint_id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_checkpoint_thread_id ON checkpoint_snapshots(thread_id);
CREATE INDEX IF NOT EXISTS idx_checkpoint_thread_latest ON checkpoint_snapshots(thread_id, is_latest);
CREATE INDEX IF NOT EXISTS idx_checkpoint_created_at ON checkpoint_snapshots(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_checkpoint_parent_id ON checkpoint_snapshots(parent_id);

-- 添加注释
COMMENT ON TABLE checkpoint_snapshots IS 'Checkpoint 快照表，用于统一会话和状态管理';
COMMENT ON COLUMN checkpoint_snapshots.id IS '主键ID';
COMMENT ON COLUMN checkpoint_snapshots.thread_id IS '线程ID（会话ID），用于标识一个完整的会话';
COMMENT ON COLUMN checkpoint_snapshots.checkpoint_id IS 'Checkpoint 唯一标识';
COMMENT ON COLUMN checkpoint_snapshots.node_id IS '当前执行的节点ID';
COMMENT ON COLUMN checkpoint_snapshots.parent_id IS '父 Checkpoint ID，支持检查点链';
COMMENT ON COLUMN checkpoint_snapshots.state_json IS '完整状态（JSON 格式）';
COMMENT ON COLUMN checkpoint_snapshots.metadata_json IS '元数据（JSON 格式）';
COMMENT ON COLUMN checkpoint_snapshots.source IS 'Checkpoint 来源：auto/manual/error/restore';
COMMENT ON COLUMN checkpoint_snapshots.iteration IS '当前迭代次数';
COMMENT ON COLUMN checkpoint_snapshots.is_latest IS '是否为该 thread 的最新 Checkpoint';
COMMENT ON COLUMN checkpoint_snapshots.created_at IS '创建时间';

-- 创建自动更新 is_latest 的触发器函数
CREATE OR REPLACE FUNCTION update_latest_checkpoint()
RETURNS TRIGGER AS $$
BEGIN
    -- 新插入的 Checkpoint，清除同 thread 的其他 is_latest 标志
    IF NEW.is_latest = TRUE THEN
        UPDATE checkpoint_snapshots
        SET is_latest = FALSE
        WHERE thread_id = NEW.thread_id AND checkpoint_id != NEW.checkpoint_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建触发器
DROP TRIGGER IF EXISTS trigger_update_latest_checkpoint ON checkpoint_snapshots;
CREATE TRIGGER trigger_update_latest_checkpoint
    BEFORE INSERT OR UPDATE OF is_latest ON checkpoint_snapshots
    FOR EACH ROW
    WHEN (NEW.is_latest = TRUE)
    EXECUTE FUNCTION update_latest_checkpoint();

-- 创建视图：会话列表（包含最新状态摘要）
CREATE OR REPLACE VIEW session_list AS
SELECT
    thread_id as session_id,
    state_json->>'current_agent' as current_agent,
    state_json->'messages'->-1->>'content' as last_message,
    created_at as updated_at,
    (SELECT COUNT(*) FROM checkpoint_snapshots WHERE thread_id = cs.thread_id) as checkpoint_count
FROM checkpoint_snapshots cs
WHERE is_latest = TRUE
ORDER BY created_at DESC;

COMMENT ON VIEW session_list IS '会话列表视图，基于最新的 Checkpoint';
