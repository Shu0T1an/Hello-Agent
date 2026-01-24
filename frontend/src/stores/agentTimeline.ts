import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { AgentEvent } from '@/types/agent'

/**
 * AgentTimeline Store
 * 专门管理 AgentTimeline 组件的事件数据
 *
 * 事件过滤规则：
 * - starting: 显示"已启动"
 * - running (LLM节点): 跳过，不在时间线显示（因为是流式输出）
 * - running (Tool节点): 正常显示
 * - completed: 显示完整节点（包含完整信息）
 * - failed: 正常显示错误信息
 */
export const useAgentTimelineStore = defineStore('agentTimeline', () => {
  // 状态
  const events = ref<AgentEvent[]>([])
  const isCollecting = ref(false)
  const sessionId = ref<string>('')

  // 计算属性
  const hasEvents = computed(() => events.value.length > 0)

  /**
   * 判断事件是否应该添加到时间线
   */
  function shouldAddEvent(event: AgentEvent): boolean {
    switch (event.eventType) {
      case 'starting':
        // starting: 显示"已启动"
        return true
      case 'running':
        // LLM节点的running跳过（流式输出）
        if (event.nodeType === 'llm') {
          return false
        }
        // Tool节点的running正常显示
        return true
      case 'completed':
      case 'failed':
        // completed和failed正常显示
        return true
      default:
        // 其他事件类型（如GRAPH_COMPLETED）
        return true
    }
  }

  /**
   * 添加事件到时间线（带过滤）
   */
  function addEvent(event: AgentEvent) {
    if (!isCollecting.value) return
    if (shouldAddEvent(event)) {
      events.value.push(event)
    }
  }

  /**
   * 清空事件列表
   */
  function clearEvents() {
    events.value = []
  }

  /**
   * 开始收集事件
   */
  function startCollecting(currentSessionId?: string) {
    isCollecting.value = true
    events.value = []
    if (currentSessionId) {
      sessionId.value = currentSessionId
    }
  }

  /**
   * 停止收集事件
   */
  function stopCollecting() {
    isCollecting.value = false
  }

  /**
   * 重置状态
   */
  function reset() {
    events.value = []
    isCollecting.value = false
    sessionId.value = ''
  }

  /**
   * 根据 nodeId 获取相关事件
   */
  function getEventsByNodeId(nodeId: string): AgentEvent[] {
    return events.value.filter(e => e.nodeId === nodeId)
  }

  /**
   * 获取最新的事件
   */
  function getLatestEvent(): AgentEvent | null {
    if (events.value.length === 0) return null
    return events.value[events.value.length - 1]
  }

  /**
   * 获取所有失败的事件
   */
  function getFailedEvents(): AgentEvent[] {
    return events.value.filter(e => e.eventType === 'failed')
  }

  return {
    // 状态
    events,
    isCollecting,
    sessionId,
    // 计算属性
    hasEvents,
    // 操作
    addEvent,
    clearEvents,
    startCollecting,
    stopCollecting,
    reset,
    getEventsByNodeId,
    getLatestEvent,
    getFailedEvents
  }
})
