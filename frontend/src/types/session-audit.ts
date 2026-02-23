import type { ApiResponse } from './summary'

export interface SessionAuditRecord {
  id: number
  traceId: string
  sessionId: string
  executionId?: string
  agentName?: string
  phase: 'REQUEST' | 'RESPONSE' | 'ERROR' | string
  requestJson?: string
  responseJson?: string
  errorMessage?: string
  createdAt: string
}

export interface SessionAuditData {
  sessionId: string
  total: number
  limit: number
  records: SessionAuditRecord[]
}

export type SessionAuditApiResponse = ApiResponse<SessionAuditData>
