import type { AgentGraphResponse } from '@/types/agent-graph'

const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export async function fetchAgentGraph(agentId: number): Promise<AgentGraphResponse> {
  const res = await fetch(`${API_BASE}/api/agents/${agentId}/graph`)
  const text = await res.text()
  let json: ApiResponse<AgentGraphResponse> | null = null
  try {
    json = JSON.parse(text) as ApiResponse<AgentGraphResponse>
  } catch {
    json = null
  }
  if (!res.ok) {
    throw new Error(json?.message || `Failed to fetch graph for agent ${agentId}`)
  }
  if (!json || typeof json !== 'object') {
    throw new Error('Invalid graph response')
  }
  if (json.code !== 200) {
    throw new Error(json.message || `Failed to fetch graph for agent ${agentId}`)
  }
  return json.data
}
