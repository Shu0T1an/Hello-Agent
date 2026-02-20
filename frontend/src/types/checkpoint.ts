/**
 * Checkpoint 来源类型
 */
export type CheckpointSource = 'auto' | 'manual' | 'error' | 'restore'

/**
 * Checkpoint 基础信息
 */
export interface Checkpoint {
  /** Checkpoint 唯一标识 */
  checkpointId: string
  /** 关联的会话/线程 ID (后端字段: threadId) */
  threadId: string
  /** 节点 ID */
  nodeId: string
  /** 来源类型 */
  source: CheckpointSource
  /** 父 Checkpoint ID */
  parentId: string | null
  /** 迭代次数 */
  iteration: number
  /** 创建时间（ISO 8601 格式） */
  createdAt: string
}

/**
 * Checkpoint 详情
 */
export interface CheckpointDetail extends Checkpoint {
  /** 步骤信息（执行上下文） */
  stepInfo: Record<string, unknown>
  /** 完整状态数据 */
  state: Record<string, unknown>
}

/**
 * Checkpoint 筛选条件
 */
export interface CheckpointFilter {
  /** 按来源类型筛选 */
  source?: CheckpointSource
  /** 按节点 ID 筛选 */
  nodeId?: string
}

/**
 * Checkpoint 恢复请求参数
 */
export interface RestoreCheckpointRequest {
  /** Checkpoint ID */
  checkpointId: string
  /** 状态更新（可选） */
  stateUpdates?: Record<string, unknown>
}

/**
 * Checkpoint 恢复响应
 */
export interface RestoreCheckpointResponse {
  /** 是否成功 */
  success: boolean
  /** 恢复后的会话 ID */
  sessionId: string
  /** 恢复后的 Checkpoint ID */
  checkpointId: string
}
