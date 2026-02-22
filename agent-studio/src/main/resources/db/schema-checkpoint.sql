-- =============================================
-- Checkpoint 系统数据库表结构（重构版）
-- PostgreSQL 版本
-- Session 和 Checkpoint 分离设计
-- =============================================

-- 删除现有表
DROP TABLE IF EXISTS checkpoint_snapshots CASCADE;

-- 1. 创建 sessions 表
CREATE TABLE IF NOT EXISTS sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL DEFAULT '新对话',
    current_agent VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    agent_switch_history JSONB DEFAULT '[]',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. 创建 checkpoint_snapshots 表（重构）
CREATE TABLE IF NOT EXISTS checkpoint_snapshots (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    checkpoint_id VARCHAR(255) NOT NULL,
    node_id VARCHAR(255),
    last_node_id VARCHAR(255),
    parent_id VARCHAR(255),
    state_json JSONB NOT NULL,
    metadata_json JSONB NOT NULL,
    source VARCHAR(20) NOT NULL,
    iteration INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (session_id, checkpoint_id),
    CONSTRAINT fk_checkpoint_session
        FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status);
CREATE INDEX IF NOT EXISTS idx_sessions_updated_at ON sessions(updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_checkpoint_session_id ON checkpoint_snapshots(session_id);
CREATE INDEX IF NOT EXISTS idx_checkpoint_parent_id ON checkpoint_snapshots(parent_id);
CREATE INDEX IF NOT EXISTS idx_checkpoint_created_at ON checkpoint_snapshots(created_at DESC);



-- 注释
COMMENT ON TABLE sessions IS '会话表，存储会话级别的元数据';
COMMENT ON COLUMN sessions.session_id IS '会话唯一标识';
COMMENT ON COLUMN sessions.title IS '会话标题';
COMMENT ON COLUMN sessions.current_agent IS '当前使用的 Agent 名称';
COMMENT ON COLUMN sessions.status IS '会话状态：active/deleted';
COMMENT ON COLUMN sessions.agent_switch_history IS 'Agent 切换历史（JSON 数组）';

COMMENT ON TABLE checkpoint_snapshots IS 'Checkpoint 快照表，存储执行状态';
COMMENT ON COLUMN checkpoint_snapshots.session_id IS '关联的会话ID（外键）';
COMMENT ON COLUMN checkpoint_snapshots.checkpoint_id IS 'Checkpoint 唯一标识';
COMMENT ON COLUMN checkpoint_snapshots.node_id IS '当前执行的节点ID';
COMMENT ON COLUMN checkpoint_snapshots.last_node_id IS '上一个执行的节点ID，用于追踪执行流程';
COMMENT ON COLUMN checkpoint_snapshots.parent_id IS '父 Checkpoint ID，支持检查点链';
COMMENT ON COLUMN checkpoint_snapshots.state_json IS '完整状态（JSON 格式）';
COMMENT ON COLUMN checkpoint_snapshots.metadata_json IS '元数据（JSON 格式）';
COMMENT ON COLUMN checkpoint_snapshots.source IS 'Checkpoint 来源：auto/manual/error/restore';
COMMENT ON COLUMN checkpoint_snapshots.iteration IS '当前迭代次数';
