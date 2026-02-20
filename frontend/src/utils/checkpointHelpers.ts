import type { CheckpointSource } from '@/types/checkpoint'

/**
 * 获取 Checkpoint 来源对应的颜色类名
 */
export function getCheckpointSourceColor(source: CheckpointSource): string {
  const colors: Record<CheckpointSource, string> = {
    auto: 'text-blue-500',
    manual: 'text-purple-500',
    error: 'text-red-500',
    restore: 'text-green-500',
  }
  return colors[source] || colors.auto
}

/**
 * 获取 Checkpoint 来源对应的背景色类名
 */
export function getCheckpointSourceBgColor(source: CheckpointSource): string {
  const colors: Record<CheckpointSource, string> = {
    auto: 'bg-blue-500',
    manual: 'bg-purple-500',
    error: 'bg-red-500',
    restore: 'bg-green-500',
  }
  return colors[source] || colors.auto
}

/**
 * 获取 Checkpoint 来源对应的浅色背景类名
 */
export function getCheckpointSourceLightBgColor(source: CheckpointSource): string {
  const colors: Record<CheckpointSource, string> = {
    auto: 'bg-blue-50 dark:bg-blue-950',
    manual: 'bg-purple-50 dark:bg-purple-950',
    error: 'bg-red-50 dark:bg-red-950',
    restore: 'bg-green-50 dark:bg-green-950',
  }
  return colors[source] || colors.auto
}

/**
 * 获取 Checkpoint 来源对应的边框色类名
 */
export function getCheckpointSourceBorderColor(source: CheckpointSource): string {
  const colors: Record<CheckpointSource, string> = {
    auto: 'border-blue-200 dark:border-blue-800',
    manual: 'border-purple-200 dark:border-purple-800',
    error: 'border-red-200 dark:border-red-800',
    restore: 'border-green-200 dark:border-green-800',
  }
  return colors[source] || colors.auto
}

/**
 * 获取 Checkpoint 来源显示名称
 */
export function getCheckpointSourceLabel(source: CheckpointSource): string {
  const labels: Record<CheckpointSource, string> = {
    auto: '自动',
    manual: '手动',
    error: '错误',
    restore: '恢复',
  }
  return labels[source] || source
}

/**
 * 格式化 Checkpoint 时间显示
 */
export function formatCheckpointTime(timestamp: string): string {
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  // 小于 1 分钟
  if (diff < 60 * 1000) {
    return '刚刚'
  }

  // 小于 1 小时
  if (diff < 60 * 60 * 1000) {
    const minutes = Math.floor(diff / (60 * 1000))
    return `${minutes} 分钟前`
  }

  // 小于 1 天
  if (diff < 24 * 60 * 60 * 1000) {
    const hours = Math.floor(diff / (60 * 60 * 1000))
    return `${hours} 小时前`
  }

  // 小于 7 天
  if (diff < 7 * 24 * 60 * 60 * 1000) {
    const days = Math.floor(diff / (24 * 60 * 60 * 1000))
    return `${days} 天前`
  }

  // 其他情况显示完整日期
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * 格式化 Checkpoint 时间为完整日期时间
 */
export function formatCheckpointFullTime(timestamp: string): string {
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

/**
 * 获取节点显示标签
 */
export function getCheckpointNodeLabel(nodeId: string): string {
  const labels: Record<string, string> = {
    START: '开始',
    END: '结束',
    AGENT_MODEL: 'LLM 节点',
    AGENT_TOOL: '工具节点',
    AGENT_END: 'Agent 结束',
  }
  return labels[nodeId] || nodeId
}

/**
 * 获取节点图标
 */
export function getCheckpointNodeIcon(nodeId: string): string {
  const icons: Record<string, string> = {
    START: 'play',
    END: 'flag',
    AGENT_MODEL: 'cpu',
    AGENT_TOOL: 'wrench',
    AGENT_END: 'check-circle',
  }
  return icons[nodeId] || 'circle'
}
