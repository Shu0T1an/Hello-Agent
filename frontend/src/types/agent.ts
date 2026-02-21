// ============================================================================
// Agent Timeline 类型定义
// ============================================================================

export interface ToolCall {
  id: string
  name: string
  arguments: string  // JSON string
}

export interface ToolExecution {
  success: boolean
  arguments: string   // JSON string
  id: string
  result: string
  name: string
}

export interface ExecutionRecord {
  output?: string
  toolCalls?: ToolCall[]
  executions?: ToolExecution[]
}

export interface StateData {
  input?: string
  execution_record?: ExecutionRecord
  [key: string]: unknown
}

export type AgentEventType =
  | 'starting'
  | 'running'
  | 'completed'
  | 'failed'
  | 'GRAPH_COMPLETED'
  | 'SUBAGENT_STARTED'
  | 'SUBAGENT_PROGRESS'
  | 'SUBAGENT_COMPLETED'
  | 'SUBAGENT_FAILED'
export type NodeType = 'llm' | 'tool' | 'custom' | 'subagent'

export interface SubAgentMetadata {
  subagentTaskId?: string
  subagentType?: string
  parentToolCallId?: string
  parentExecutionId?: string
  phase?: 'queued' | 'planning' | 'running' | 'tool_call' | 'tool_result' | 'synthesizing' | 'done' | 'failed' | string
  progress?: number
  durationMs?: number
  summary?: string
  errorCode?: string
  errorMessage?: string
  seq?: number
  stepId?: string
  stepTitle?: string
  toolName?: string
  toolCallId?: string
  [key: string]: unknown
}

export interface AgentEvent {
  eventType: AgentEventType
  nodeId: string
  nodeType: NodeType
  stateData: StateData
  message?: string
  timestamp: string
  metadata?: SubAgentMetadata
  title?: string            // 节点标题（来自后端）
  startTime?: string
  endTime?: string
  nodeErrorMessage?: string // 节点错误信息（来自后端）
  logs?: string[]           // 日志列表（来自后端）
}

// ============================================================================
// Agent 配置管理类型定义
// ============================================================================

/**
 * 模型配置简略信息
 */
export interface ModelConfigRef {
  id: number
  modelName: string
  displayName: string
  provider: string
  modelId: string
}

/**
 * 工具定义简略信息
 */
export interface ToolDefinitionRef {
  id: number
  toolName: string
  displayName: string
  description: string
  toolType: 'LOCAL' | 'MCP'
  mcpConnectionName?: string
}

/**
 * Agent 配置接口
 */
export interface AgentConfig {
  id: number
  agentName: string
  displayName: string
  description?: string
  modelId: number
  modelConfig?: ModelConfigRef
  systemPrompt?: string
  maxIterations?: number
  temperature?: number
  enableStreaming?: boolean
  isActive: boolean
  createdBy?: string
  createdAt: string
  updatedAt: string
  toolIds?: number[]
  toolDefinitions?: ToolDefinitionRef[]
}

/**
 * 创建 Agent DTO
 */
export interface CreateAgentDTO {
  agentName: string
  displayName: string
  description?: string
  modelId: number
  systemPrompt?: string
  maxIterations?: number
  temperature?: number
  enableStreaming?: boolean
  toolIds?: number[]
}

/**
 * 更新 Agent DTO
 */
export interface UpdateAgentDTO {
  displayName?: string
  description?: string
  modelId?: number
  systemPrompt?: string
  maxIterations?: number
  temperature?: number
  enableStreaming?: boolean
  isActive?: boolean
  toolIds?: number[]
}
