// 消息类型定义
export type MessageRole = 'user' | 'assistant' | 'system'

export type MessageStatus = 'idle' | 'thinking' | 'taking-action' | 'completed' | 'error'

export interface Message {
  id: string
  role: MessageRole
  content: string
  timestamp: string
  status?: MessageStatus
  avatar?: string
}

export interface ChatSession {
  id: string
  title: string
  messages: Message[]
  createdAt: string
  updatedAt: string
}

export type Strategy = 'deep-research' | 'quick' | 'detailed'
