import type {
  Checkpoint,
  CheckpointDetail,
  RestoreCheckpointRequest,
  RestoreCheckpointResponse,
} from '@/types/checkpoint'

const API_BASE = '/api'

/**
 * 获取会话的所有 Checkpoint
 */
export async function fetchCheckpoints(sessionId: string): Promise<Checkpoint[]> {
  const response = await fetch(`${API_BASE}/checkpoints/sessions/${encodeURIComponent(sessionId)}`)
  if (!response.ok) {
    throw new Error(`Failed to fetch checkpoints: ${response.statusText}`)
  }
  const json = await response.json()
  // 后端返回的数据格式: { code, message, data, timestamp }
  return (json.data as Checkpoint[]) || []
}

/**
 * 获取 Checkpoint 详情
 */
export async function fetchCheckpointDetail(checkpointId: string): Promise<CheckpointDetail> {
  const response = await fetch(`${API_BASE}/checkpoints/${encodeURIComponent(checkpointId)}`)
  if (!response.ok) {
    throw new Error(`Failed to fetch checkpoint detail: ${response.statusText}`)
  }
  const json = await response.json()
  // 后端返回的数据格式: { code, message, data, timestamp }
  return json.data as CheckpointDetail
}

/**
 * 恢复 Checkpoint
 */
export async function restoreCheckpoint(
  request: RestoreCheckpointRequest
): Promise<RestoreCheckpointResponse> {
  const response = await fetch(
    `${API_BASE}/checkpoints/${encodeURIComponent(request.checkpointId)}/restore`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ stateUpdates: request.stateUpdates }),
    }
  )
  if (!response.ok) {
    throw new Error(`Failed to restore checkpoint: ${response.statusText}`)
  }
  const json = await response.json()
  // 后端返回的数据格式: { code, message, data, timestamp }
  return json.data as RestoreCheckpointResponse
}

/**
 * 删除 Checkpoint
 */
export async function deleteCheckpoint(checkpointId: string): Promise<void> {
  const response = await fetch(`${API_BASE}/checkpoints/${encodeURIComponent(checkpointId)}`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    throw new Error(`Failed to delete checkpoint: ${response.statusText}`)
  }
}
