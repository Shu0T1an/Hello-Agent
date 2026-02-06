-- =============================================
-- 数据库迁移脚本：添加 last_node_id 列
-- 用于追踪节点执行流程
-- =============================================

-- 添加 last_node_id 列到 checkpoint_snapshots 表
ALTER TABLE checkpoint_snapshots
ADD COLUMN IF NOT EXISTS last_node_id VARCHAR(100);

-- 添加注释
COMMENT ON COLUMN checkpoint_snapshots.last_node_id IS '上一个执行的节点ID，用于追踪执行流程';
