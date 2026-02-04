/**
 * 全局常量定义
 */

// API 基础 URL
export const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

// 策略模式配置
export const STRATEGY_LABELS: Record<string, string> = {
  'deep-research': 'Deep Research',
  'quick': 'Quick',
  'detailed': 'Detailed'
} as const

// 消息状态配置
export const MESSAGE_STATUS_CONFIG: Record<string, { label: string; variant: 'default' | 'warning' | 'danger' | 'success' | 'info' | 'purple' }> = {
  'thinking': { label: '思考中...', variant: 'warning' },
  'taking-action': { label: '执行中', variant: 'purple' },
  'completed': { label: '完成', variant: 'success' },
  'error': { label: '错误', variant: 'danger' },
  'interrupted': { label: '等待审批', variant: 'warning' }
} as const

// 后端事件类型到前端消息状态的映射
export const EVENT_TYPE_TO_MESSAGE_STATUS: Record<string, 'thinking' | 'taking-action' | 'completed' | 'error'> = {
  'starting': 'thinking',
  'running': 'thinking',
  'completed': 'completed',
  'failed': 'error',
  'GRAPH_COMPLETED': 'completed'
} as const

// 默认会话标题
export const DEFAULT_SESSION_TITLE = '新对话'

// 默认会话 ID 前缀
export const SESSION_ID_PREFIX = 'session-'

// 默认 Agent 名称
export const DEFAULT_AGENT_NAME = 'default'

// SSE 连接超时时间（毫秒）
export const SSE_TIMEOUT = 300000

// 自动重连延迟（毫秒）
export const RECONNECT_DELAY = 3000
