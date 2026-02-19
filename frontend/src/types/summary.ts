/**
 * 会话摘要相关类型定义
 */

// 统一 API 响应格式
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

// 基础统计
export interface BasicStats {
  totalTokens?: number
  totalToolCalls?: number
  totalDuration?: number
  totalIterations?: number
  llmCallCount?: number
  startTime?: string
  endTime?: string
}

// 工具统计
export interface ToolStats {
  toolName: string
  callCount: number
  successCount: number
  failureCount: number
  successRate: number
  totalDuration: number
  avgDuration: number
}

// LLM 调用详情
export interface LLMCallStats {
  nodeId: string
  iteration?: number
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  duration?: number
  timestamp?: string
  toolCalls?: string[]
}

// 会话摘要
export interface SessionSummary {
  sessionId: string
  title: string
  basicStats?: BasicStats
  toolStats?: ToolStats[]
  llmCalls?: LLMCallStats[]
}
