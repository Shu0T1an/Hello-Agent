/**
 * Session 管理 API
 */

import type { SessionSummary, ApiResponse } from '@/types/summary'
import type { SessionAuditData, SessionAuditApiResponse } from '@/types/session-audit'

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

/**
 * 获取会话审计记录
 */
export async function fetchSessionAudits(sessionId: string, limit = 200): Promise<SessionAuditData> {
  const res = await fetch(
    `${API_BASE}/api/sessions/${encodeURIComponent(sessionId)}/audits?limit=${encodeURIComponent(String(limit))}`
  )
  if (!res.ok) throw new Error(`Failed to fetch session audits: ${sessionId}`)
  const json: SessionAuditApiResponse = await res.json()
  if (json.code !== 200) {
    throw new Error(json.message || 'Failed to fetch session audits')
  }
  return json.data
}
