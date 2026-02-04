/**
 * Agent Timeline 事件处理辅助函数
 */

import type { AgentEvent } from '@/types/agent'
import { Play, Bot, MessageSquare, Terminal, Cog } from 'lucide-vue-next'

/**
 * 判断是否为 Tool 节点
 */
export function isToolNode(event: AgentEvent): boolean {
  return event.nodeType === 'tool'
}

/**
 * 判断是否为 LLM 节点的工具调用
 */
export function isLLMToolCall(event: AgentEvent): boolean {
  if (event.nodeType !== 'llm') return false
  const toolCalls = event.stateData?.execution_record?.toolCalls
  return toolCalls !== undefined && toolCalls.length > 0
}

/**
 * 判断是否为 LLM 节点的响应
 */
export function isLLMResponse(event: AgentEvent): boolean {
  if (event.nodeType !== 'llm') return false
  const toolCalls = event.stateData?.execution_record?.toolCalls
  return !toolCalls || toolCalls.length === 0
}

/**
 * 节点颜色主题配置
 */
export interface NodeThemeConfig {
  icon: typeof Play | typeof Bot | typeof MessageSquare | typeof Terminal | typeof Cog
  label: string
  dotBg: string
  borderClass: string
  labelClass: string
}

/**
 * 获取节点配置
 */
export function getNodeConfig(event: AgentEvent): NodeThemeConfig {
  if (event.eventType === 'starting') {
    return {
      icon: Play,
      label: '已启动',
      dotBg: 'bg-zinc-400',
      borderClass: 'border-zinc-200',
      labelClass: 'text-zinc-600'
    }
  }

  if (isLLMToolCall(event)) {
    return {
      icon: Bot,
      label: 'AI Thinking',
      dotBg: 'bg-indigo-500',
      borderClass: 'border-indigo-200',
      labelClass: 'text-indigo-700'
    }
  }

  if (isLLMResponse(event)) {
    return {
      icon: MessageSquare,
      label: 'AI Response',
      dotBg: 'bg-emerald-500',
      borderClass: 'border-emerald-200',
      labelClass: 'text-emerald-700'
    }
  }

  if (isToolNode(event)) {
    return {
      icon: Terminal,
      label: 'Tool Execution',
      dotBg: 'bg-amber-500',
      borderClass: 'border-amber-200',
      labelClass: 'text-amber-700'
    }
  }

  return {
    icon: Cog,
    label: 'System Node',
    dotBg: 'bg-zinc-500',
    borderClass: 'border-zinc-200',
    labelClass: 'text-zinc-700'
  }
}

/**
 * 生成事件唯一 ID
 */
export function getEventId(event: AgentEvent, index: number): string {
  return `${event.nodeId}-${event.timestamp}-${index}`
}
