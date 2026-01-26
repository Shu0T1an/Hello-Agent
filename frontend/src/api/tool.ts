/**
 * 工具定义管理 API
 */

import type { ToolDefinition, CreateToolDTO, UpdateToolDTO, ToolType } from '@/types/tool'

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
 * 获取所有工具
 */
export async function fetchTools(): Promise<ToolDefinition[]> {
  const res = await fetch(`${API_BASE}/api/tools`)
  if (!res.ok) throw new Error('Failed to fetch tools')
  const json: ApiResponse<ToolDefinition[]> = await res.json()
  return json.data
}

/**
 * 根据 ID 获取单个工具
 */
export async function fetchTool(id: number): Promise<ToolDefinition> {
  const res = await fetch(`${API_BASE}/api/tools/${id}`)
  if (!res.ok) throw new Error(`Failed to fetch tool ${id}`)
  const json: ApiResponse<ToolDefinition> = await res.json()
  return json.data
}

/**
 * 根据类型获取工具
 */
export async function fetchToolsByType(type: ToolType): Promise<ToolDefinition[]> {
  const res = await fetch(`${API_BASE}/api/tools/type/${type}`)
  if (!res.ok) throw new Error(`Failed to fetch tools of type ${type}`)
  const json: ApiResponse<ToolDefinition[]> = await res.json()
  return json.data
}

/**
 * 获取本地工具
 */
export async function fetchLocalTools(): Promise<ToolDefinition[]> {
  return fetchToolsByType('LOCAL' as ToolType)
}

/**
 * 获取 MCP 工具
 */
export async function fetchMcpTools(): Promise<ToolDefinition[]> {
  return fetchToolsByType('MCP' as ToolType)
}

/**
 * 创建工具定义
 */
export async function createTool(data: CreateToolDTO): Promise<ToolDefinition> {
  const res = await fetch(`${API_BASE}/api/tools`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
  if (!res.ok) throw new Error('Failed to create tool')
  const json: ApiResponse<ToolDefinition> = await res.json()
  return json.data
}

/**
 * 更新工具定义
 */
export async function updateTool(id: number, data: UpdateToolDTO): Promise<ToolDefinition> {
  const res = await fetch(`${API_BASE}/api/tools/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
  if (!res.ok) throw new Error(`Failed to update tool ${id}`)
  const json: ApiResponse<ToolDefinition> = await res.json()
  return json.data
}

/**
 * 删除工具定义
 */
export async function deleteTool(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/api/tools/${id}`, {
    method: 'DELETE'
  })
  if (!res.ok) throw new Error(`Failed to delete tool ${id}`)
}

/**
 * 手动触发本地工具扫描
 */
export async function scanLocalTools(): Promise<{ count: number }> {
  const res = await fetch(`${API_BASE}/api/tools/scan-local`, {
    method: 'POST'
  })
  if (!res.ok) throw new Error('Failed to scan local tools')
  const json: ApiResponse<{ count: number }> = await res.json()
  return json.data
}

/**
 * 手动触发 MCP 工具同步
 */
export async function syncMcpTools(connectionName: string): Promise<{ count: number }> {
  const res = await fetch(`${API_BASE}/api/tools/sync-mcp/${encodeURIComponent(connectionName)}`, {
    method: 'POST'
  })
  if (!res.ok) throw new Error(`Failed to sync MCP tools for ${connectionName}`)
  const json: ApiResponse<{ count: number }> = await res.json()
  return json.data
}
