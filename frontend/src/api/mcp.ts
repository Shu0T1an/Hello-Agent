/**
 * MCP 连接管理 API
 */

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
 * MCP 连接类型
 */
export type McpConnectionType = 'STDIO' | 'SSE' | 'HTTP'

/**
 * MCP 连接信息
 */
export interface McpConnection {
  name: string
  description?: string
  config: {
    type: McpConnectionType
    command?: string
    args?: string[]
    env?: Record<string, string>
    url?: string
  }
  status: 'CONNECTED' | 'DISCONNECTED' | 'ERROR'
  error?: string
  toolCount?: number
}

/**
 * 创建 MCP 连接请求（后端期望的格式）
 */
export interface CreateMcpConnectionRequest {
  name: string
  description?: string
  type: McpConnectionType
  // STDIO 配置
  command?: string
  args?: string[]
  env?: Record<string, string>
  workingDir?: string
  // SSE 配置
  sseUrl?: string
  sseHeaders?: Record<string, string>
  // HTTP 配置
  httpUrl?: string
  httpHeaders?: Record<string, string>
  httpMethod?: string
  // 通用配置
  timeoutSeconds?: number
  autoReconnect?: boolean
  maxRetries?: number
  retryIntervalSeconds?: number
}

/**
 * 获取所有 MCP 连接
 */
export async function fetchConnections(): Promise<McpConnection[]> {
  const res = await fetch(`${API_BASE}/api/mcp/connections`)
  if (!res.ok) throw new Error('Failed to fetch MCP connections')
  const json: ApiResponse<McpConnection[]> = await res.json()
  return json.data
}

/**
 * 创建 MCP 连接
 */
export async function createConnection(data: CreateMcpConnectionRequest): Promise<void> {
  const res = await fetch(`${API_BASE}/api/mcp/connections`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
  if (!res.ok) {
    const error = await res.json()
    throw new Error(error.message || 'Failed to create MCP connection')
  }
}

/**
 * 连接到 MCP 服务器
 */
export async function connectConnection(name: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/mcp/connections/${encodeURIComponent(name)}/connect`, {
    method: 'POST'
  })
  if (!res.ok) throw new Error('Failed to connect to MCP server')
}

/**
 * 断开 MCP 连接
 */
export async function disconnectConnection(name: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/mcp/connections/${encodeURIComponent(name)}/disconnect`, {
    method: 'POST'
  })
  if (!res.ok) throw new Error('Failed to disconnect MCP server')
}

/**
 * 删除 MCP 连接
 */
export async function deleteConnection(name: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/mcp/connections/${encodeURIComponent(name)}`, {
    method: 'DELETE'
  })
  if (!res.ok) throw new Error('Failed to delete MCP connection')
}

/**
 * 同步指定 MCP 连接的工具
 */
export async function syncMcpTools(connectionName: string): Promise<{ count: number }> {
  const res = await fetch(`${API_BASE}/api/tools/sync-mcp/${encodeURIComponent(connectionName)}`, {
    method: 'POST'
  })
  if (!res.ok) throw new Error(`Failed to sync MCP tools for ${connectionName}`)
  const json: ApiResponse<{ count: number }> = await res.json()
  return json.data
}

/**
 * 同步所有 MCP 连接的工具
 */
export async function syncAllMcpTools(): Promise<{ total: number; connections: number }> {
  const connections = await fetchConnections()
  const connected = connections.filter(c => c.status === 'CONNECTED')

  let totalTools = 0
  for (const conn of connected) {
    const result = await syncMcpTools(conn.name)
    totalTools += result.count
  }

  return { total: totalTools, connections: connected.length }
}
