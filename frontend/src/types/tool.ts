/**
 * 工具定义类型定义
 */

/**
 * 工具类型枚举
 */
export enum ToolType {
  LOCAL = 'LOCAL',
  MCP = 'MCP'
}

/**
 * 工具定义接口
 */
export interface ToolDefinition {
  id: number
  toolName: string
  displayName: string
  description: string
  toolType: ToolType
  className?: string
  mcpConnectionName?: string
  mcpToolName?: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

/**
 * 创建工具定义 DTO
 */
export interface CreateToolDTO {
  toolName: string
  displayName: string
  description: string
  toolType: ToolType
  className?: string
  mcpConnectionName?: string
  mcpToolName?: string
  isActive?: boolean
}

/**
 * 更新工具定义 DTO
 */
export interface UpdateToolDTO {
  displayName?: string
  description?: string
  isActive?: boolean
}

/**
 * MCP 连接类型枚举
 */
export enum McpConnectionType {
  STDIO = 'STDIO',
  SSE = 'SSE'
}
