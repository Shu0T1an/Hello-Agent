# 统一会话与 Checkpoint 管理系统设计

## 设计目标

实现应用层级的会话管理，支持跨 Agent 对话，通过 Checkpoint 系统统一管理状态，实现数据库持久化。

## 架构原则

1. **单一数据源**：所有会话状态存储在 Checkpoint 系统中
2. **分层 API**：提供简化的会话 API 用于前端，暴露 Checkpoint API 用于高级场景
3. **应用层持久化**：在 Agent-Studio 模块实现 DatabaseCheckpointStorage
4. **单会话多 Agent**：在 State 中支持 Agent 切换

## 数据库表设计

### checkpoint_snapshots 表

```sql
CREATE TABLE IF NOT EXISTS checkpoint_snapshots (
    id BIGSERIAL PRIMARY KEY,
    thread_id VARCHAR(100) NOT NULL,
    checkpoint_id VARCHAR(100) NOT NULL,
    node_id VARCHAR(100),
    parent_id VARCHAR(100),
    state_json JSONB NOT NULL,
    metadata_json JSONB NOT NULL,
    source VARCHAR(20) NOT NULL,
    iteration INT NOT NULL,
    is_latest BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (thread_id, checkpoint_id)
);

CREATE INDEX idx_checkpoint_thread_id ON checkpoint_snapshots(thread_id);
CREATE INDEX idx_checkpoint_latest ON checkpoint_snapshots(thread_id, is_latest);
CREATE INDEX idx_checkpoint_created_at ON checkpoint_snapshots(created_at DESC);
```

### State 结构

```json
{
  "messages": [
    {"role": "user", "content": "你好"},
    {"role": "assistant", "content": "你好！有什么可以帮助？"}
  ],
  "current_agent": "chat-agent",
  "agent_history": [
    {"agent": "chat-agent", "switched_at": "2025-01-30T10:00:00Z", "reason": "user_initiated"}
  ],
  "iteration": 1,
  "input": "你好"
}
```

## API 设计

### 会话 API（简化层）

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/sessions` | GET | 获取所有会话 |
| `/api/sessions/{sessionId}` | GET | 获取会话详情（包含消息历史） |
| `/api/sessions` | POST | 创建新会话 |
| `/api/sessions/{sessionId}/switch-agent` | PUT | 切换会话的 Agent |

### Checkpoint API（高级层）

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/checkpoints/threads/{threadId}` | GET | 获取会话的所有 Checkpoint |
| `/api/checkpoints/{checkpointId}/restore` | POST | 恢复到指定 Checkpoint |
| `/api/checkpoints/{checkpointId}` | GET | 获取 Checkpoint 详情 |

## 实施计划

### 阶段 1：数据库层
1. 创建 `schema-checkpoint.sql`
2. 创建 CheckpointEntity 和 CheckpointMapper
3. 实现 DatabaseCheckpointStorage

### 阶段 2：核心集成
1. 修改 SessionService 使用 CheckpointManager
2. 更新 SessionController 和 StreamController
3. 实现 Agent 切换逻辑

### 阶段 3：高级功能
1. 创建 CheckpointController
2. 实现 Checkpoint 恢复功能

### 阶段 4：测试
1. 单元测试
2. 集成测试
3. 性能测试
