/**
 * 通用辅助函数
 */

import { MESSAGE_STATUS_CONFIG } from './constants'

/**
 * 格式化时间戳为本地化字符串
 */
export function formatTimestamp(timestamp: string): string {
  try {
    const date = new Date(timestamp)
    return date.toLocaleString('zh-CN')
  } catch {
    return timestamp
  }
}

/**
 * 格式化时间为时分秒
 */
export function formatTime(timestamp: string): string {
  try {
    const date = new Date(timestamp)
    return date.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch {
    return timestamp
  }
}

/**
 * 格式化时间为完整的日期时间
 */
export function formatDateTime(timestamp: string): string {
  try {
    const date = new Date(timestamp)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  } catch {
    return timestamp
  }
}

/**
 * 生成唯一的会话 ID
 */
export function generateSessionId(): string {
  return `session-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
}

/**
 * 格式化 JSON 字符串
 */
export function formatJSON(jsonStr: string): string {
  try {
    const parsed = JSON.parse(jsonStr)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return jsonStr
  }
}

/**
 * 获取消息状态的标签
 */
export function getMessageStatusLabel(status: string): string {
  return MESSAGE_STATUS_CONFIG[status]?.label || status
}

/**
 * 获取消息状态的变体
 */
export function getMessageStatusVariant(status: string): 'default' | 'warning' | 'danger' | 'success' | 'info' | 'purple' {
  return MESSAGE_STATUS_CONFIG[status]?.variant || 'info'
}

/**
 * 映射后端事件类型到前端消息状态
 */
export function mapEventTypeToMessageStatus(eventType: string): 'thinking' | 'taking-action' | 'completed' | 'error' {
  const statusMap: Record<string, 'thinking' | 'taking-action' | 'completed' | 'error'> = {
    'starting': 'thinking',
    'running': 'thinking',
    'completed': 'completed',
    'failed': 'error',
    'GRAPH_COMPLETED': 'completed'
  }
  return statusMap[eventType] || 'thinking'
}

/**
 * 清理对象中的空值
 */
export function cleanObject<T extends Record<string, unknown>>(obj: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(obj).filter(([_, value]) => value !== null && value !== undefined && value !== '')
  ) as Partial<T>
}

/**
 * 防抖函数
 */
export function debounce<T extends (...args: unknown[]) => unknown>(
  func: T,
  wait: number
): (...args: Parameters<T>) => void {
  let timeout: ReturnType<typeof setTimeout> | null = null
  return function executedFunction(...args: Parameters<T>) {
    const later = () => {
      timeout = null
      func(...args)
    }
    if (timeout) clearTimeout(timeout)
    timeout = setTimeout(later, wait)
  }
}

/**
 * 节流函数
 */
export function throttle<T extends (...args: unknown[]) => unknown>(
  func: T,
  limit: number
): (...args: Parameters<T>) => void {
  let inThrottle: boolean
  return function executedFunction(...args: Parameters<T>) {
    if (!inThrottle) {
      func(...args)
      inThrottle = true
      setTimeout(() => (inThrottle = false), limit)
    }
  }
}

/**
 * 格式化数字（添加千分位）
 */
export function formatNumber(num?: number): string {
  if (num === undefined || num === null) return '-'
  return num.toLocaleString('zh-CN')
}

/**
 * 格式化时长（毫秒转为可读格式）
 */
export function formatDuration(ms?: number): string {
  if (ms === undefined || ms === null) return '-'

  if (ms < 1000) {
    return `${ms}ms`
  } else if (ms < 60000) {
    const seconds = (ms / 1000).toFixed(1)
    return `${seconds}s`
  } else if (ms < 3600000) {
    const minutes = Math.floor(ms / 60000)
    const seconds = Math.floor((ms % 60000) / 1000)
    return `${minutes}m ${seconds}s`
  } else {
    const hours = Math.floor(ms / 3600000)
    const minutes = Math.floor((ms % 3600000) / 60000)
    return `${hours}h ${minutes}m`
  }
}
