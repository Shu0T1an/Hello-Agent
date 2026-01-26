/**
 * Agent 管理 API
 */

import type { AgentConfig, CreateAgentDTO, UpdateAgentDTO } from '@/types/agent'

const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

/**
 * 统一响应接口
 */
interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/**
 * 获取所有 Agent
 */
export async function fetchAgents(): Promise<AgentConfig[]> {
  const res = await fetch(`${API_BASE}/api/agents`)
  if (!res.ok) throw new Error('Failed to fetch agents')
  const json: ApiResponse<AgentConfig[]> = await res.json()
  return json.data
}

/**
 * 根据 ID 获取单个 Agent
 */
export async function fetchAgent(id: number): Promise<AgentConfig> {
  const res = await fetch(`${API_BASE}/api/agents/${id}`)
  if (!res.ok) throw new Error(`Failed to fetch agent ${id}`)
  const json: ApiResponse<AgentConfig> = await res.json()
  return json.data
}

/**
 * 创建 Agent
 */
export async function createAgent(data: CreateAgentDTO): Promise<AgentConfig> {
  const res = await fetch(`${API_BASE}/api/agents`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
  if (!res.ok) throw new Error('Failed to create agent')
  const json: ApiResponse<AgentConfig> = await res.json()
  return json.data
}

/**
 * 更新 Agent
 */
export async function updateAgent(id: number, data: UpdateAgentDTO): Promise<AgentConfig> {
  const res = await fetch(`${API_BASE}/api/agents/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
  if (!res.ok) throw new Error(`Failed to update agent ${id}`)
  const json: ApiResponse<AgentConfig> = await res.json()
  return json.data
}

/**
 * 删除 Agent
 */
export async function deleteAgent(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/api/agents/${id}`, {
    method: 'DELETE'
  })
  if (!res.ok) throw new Error(`Failed to delete agent ${id}`)
}

/**
 * 激活 Agent
 */
export async function activateAgent(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/api/agents/${id}/activate`, {
    method: 'POST'
  })
  if (!res.ok) throw new Error(`Failed to activate agent ${id}`)
}

/**
 * 停用 Agent
 */
export async function deactivateAgent(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/api/agents/${id}/deactivate`, {
    method: 'POST'
  })
  if (!res.ok) throw new Error(`Failed to deactivate agent ${id}`)
}

/**
 * 重载 Agent
 */
export async function reloadAgent(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/api/agents/${id}/reload`, {
    method: 'POST'
  })
  if (!res.ok) throw new Error(`Failed to reload agent ${id}`)
}

/**
 * 重载所有 Agent
 */
export async function reloadAllAgents(): Promise<void> {
  const res = await fetch(`${API_BASE}/api/agents/reload-all`, {
    method: 'POST'
  })
  if (!res.ok) throw new Error('Failed to reload all agents')
}
