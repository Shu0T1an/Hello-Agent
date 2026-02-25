import type { AgentGraphResponse, RuntimeAgentSummary } from '@/types/agent-graph'

const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

async function parseApiResponse<T>(res: Response, fallbackMessage: string): Promise<ApiResponse<T>> {
  const text = await res.text()
  let json: ApiResponse<T> | null = null
  try {
    json = JSON.parse(text) as ApiResponse<T>
  } catch {
    json = null
  }
  if (!res.ok) {
    throw new Error(json?.message || fallbackMessage)
  }
  if (!json || typeof json !== 'object') {
    throw new Error('Invalid response payload')
  }
  if (json.code !== 200) {
    throw new Error(json.message || fallbackMessage)
  }
  return json
}

export async function fetchAgentGraph(agentId: number): Promise<AgentGraphResponse> {
  const res = await fetch(`${API_BASE}/api/agents/${agentId}/graph`)
  const json = await parseApiResponse<AgentGraphResponse>(res, `Failed to fetch graph for agent ${agentId}`)
  return json.data
}

export async function fetchRuntimeAgentGraph(agentName: string): Promise<AgentGraphResponse> {
  const encoded = encodeURIComponent(agentName)
  const res = await fetch(`${API_BASE}/api/agents/runtime/${encoded}/graph`)
  const json = await parseApiResponse<AgentGraphResponse>(res, `Failed to fetch graph for runtime agent ${agentName}`)
  return json.data
}

export async function fetchRuntimeAgents(): Promise<RuntimeAgentSummary[]> {
  const res = await fetch(`${API_BASE}/api/agents/runtime`)
  const json = await parseApiResponse<RuntimeAgentSummary[]>(res, 'Failed to fetch runtime agents')
  return json.data ?? []
}
