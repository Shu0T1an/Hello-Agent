/**
 * Session 管理 API
 */

import type { SessionSummary, ApiResponse } from '@/types/summary'

const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

/**
 * 获取会话摘要
 */
export async function fetchSessionSummary(sessionId: string): Promise<SessionSummary> {
  const res = await fetch(`${API_BASE}/api/sessions/${sessionId}/summary`)
  if (!res.ok) throw new Error(`Failed to fetch session summary: ${sessionId}`)
  const json: ApiResponse<SessionSummary> = await res.json()
  return json.data
}
