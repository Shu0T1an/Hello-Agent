import type { AgentEvent } from '@/types/agent'
import { Play, Bot, MessageSquare, Terminal, Cog, GitBranch } from 'lucide-vue-next'

export function isToolNode(event: AgentEvent): boolean {
  return event.nodeType === 'tool'
}

export function isSubAgentEvent(event: AgentEvent): boolean {
  return event.nodeType === 'subagent' || event.eventType.startsWith('SUBAGENT_')
}

export function isLLMToolCall(event: AgentEvent): boolean {
  if (event.nodeType !== 'llm') return false
  const toolCalls = event.stateData?.execution_record?.toolCalls
  return toolCalls !== undefined && toolCalls.length > 0
}

export function isLLMResponse(event: AgentEvent): boolean {
  if (event.nodeType !== 'llm') return false
  const toolCalls = event.stateData?.execution_record?.toolCalls
  return !toolCalls || toolCalls.length === 0
}

export interface NodeThemeConfig {
  icon: typeof Play | typeof Bot | typeof MessageSquare | typeof Terminal | typeof Cog | typeof GitBranch
  label: string
  dotBg: string
  borderClass: string
  labelClass: string
}

export function getNodeConfig(event: AgentEvent): NodeThemeConfig {
  if (event.eventType === 'starting') {
    return {
      icon: Play,
      label: 'Started',
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

  if (isSubAgentEvent(event)) {
    const failed = event.eventType === 'SUBAGENT_FAILED'
    const completed = event.eventType === 'SUBAGENT_COMPLETED'
    return {
      icon: GitBranch,
      label: failed ? 'SubAgent Failed' : completed ? 'SubAgent Done' : 'SubAgent',
      dotBg: failed ? 'bg-rose-500' : completed ? 'bg-sky-500' : 'bg-cyan-500',
      borderClass: failed ? 'border-rose-200' : 'border-cyan-200',
      labelClass: failed ? 'text-rose-700' : 'text-cyan-700'
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

export function getEventId(event: AgentEvent, index: number): string {
  return `${event.nodeId}-${event.timestamp}-${index}`
}
