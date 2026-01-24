// Agent Timeline 类型定义

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

export type AgentEventType = 'starting' | 'running' | 'completed' | 'failed' | 'GRAPH_COMPLETED'
export type NodeType = 'llm' | 'tool' | 'custom'

export interface AgentEvent {
  eventType: AgentEventType
  nodeId: string
  nodeType: NodeType
  stateData: StateData
  message?: string
  timestamp: string
  title?: string            // 节点标题（来自后端）
  startTime?: string
  endTime?: string
  nodeErrorMessage?: string // 节点错误信息（来自后端）
  logs?: string[]           // 日志列表（来自后端）
}
