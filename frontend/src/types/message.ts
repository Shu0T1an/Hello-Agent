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

// 消息附件
export interface MessageAttachment {
  id: string
  fileName: string
  fileSize: number
  fileType: string
}

// 引用信息
export interface CitationReference {
  chunkId: string
  fileName: string
  content: string
  chunkIndex: number
}

export interface Message {
  id: string
  role: MessageRole
  content: string
  thinkingContent?: string
  timestamp: string
  status?: MessageStatus
  avatar?: string
  checkpointId?: string
  metadata?: {
    tool_calls?: ToolCall[]
    tool_responses?: ToolResponse[]
    citations?: CitationReference[]
    hide_thinking?: boolean
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
  attachments?: MessageAttachment[]
  citations?: CitationReference[]
}

export interface ChatSession {
  id: string
  title: string
  messages: Message[]
  createdAt: string
  updatedAt: string
}

export type Strategy = 'deep-research' | 'quick' | 'detailed'
