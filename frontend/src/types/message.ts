// 消息类型定义
export type MessageRole = 'user' | 'assistant' | 'system' | 'tool_call' | 'tool_response'

export type MessageStatus = 'idle' | 'thinking' | 'taking-action' | 'completed' | 'error' | 'interrupted'

// 工具调用元数据
export interface ToolCall {
  id: string
  name: string
  type: string
  arguments: string
}

// 工具响应元数据
export interface ToolResponse {
  id: string
  name: string
  response: string
}

export interface Message {
  id: string
  role: MessageRole
  content: string
  timestamp: string
  status?: MessageStatus
  avatar?: string
  checkpointId?: string
  metadata?: {
    tool_calls?: ToolCall[]
    tool_responses?: ToolResponse[]
  }
  interruptionData?: {
    message: string
    tool_feedbacks: Array<{
      id: string
      name: string
      arguments: Record<string, unknown>
      description: string
      result: string
    }>
  }
}

export interface ChatSession {
  id: string
  title: string
  messages: Message[]
  createdAt: string
  updatedAt: string
}

export type Strategy = 'deep-research' | 'quick' | 'detailed'
