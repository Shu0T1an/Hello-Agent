import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { Message, ChatSession, Strategy, MessageStatus } from '@/types/message'
import { useAgentStore } from './agent'
import { useAgentTimelineStore } from './agentTimeline'

// SSE 事件数据类型（与后端 AgentResponse 一致）
interface AgentEvent {
  eventType: string
  nodeId?: string
  nodeType?: string  // 'llm' | 'tool' | 'custom'
  stateData?: Record<string, any>
  message?: string
  timestamp: string
  executionId?: string
  error?: string
  metadata?: Record<string, any>
  // 节点状态相关字段
  nodeStatus?: string           // 'starting' | 'running' | 'completed' | 'failed'
  title?: string                // 节点标题
  startTime?: string             // 开始时间 (ISO 8601)
  endTime?: string               // 结束时间 (ISO 8601)
  logs?: string[]                // 日志列表
  nodeErrorMessage?: string      // 节点错误信息
}

// 后端会话数据类型
interface BackendSession {
  id: string
  title: string
  agentName: string
  createdAt: string
  updatedAt: string
  messageCount: number
}

interface BackendSessionDetail extends BackendSession {
  messages: BackendMessage[]
}

interface BackendMessage {
  id: string
  role: string
  content: string
  timestamp: string
}

// API 基础 URL
const API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : ''

// 时间戳格式化
function formatTimestamp(timestamp: string): string {
  try {
    const date = new Date(timestamp)
    return date.toLocaleString('zh-CN')
  } catch {
    return timestamp
  }
}

// 后端事件类型到前端消息状态的映射
// eventType 现在是 NodeStatus.getCode()：'starting' | 'running' | 'completed' | 'failed'
function mapEventTypeToMessageStatus(eventType: string): MessageStatus {
  const statusMap: Record<string, MessageStatus> = {
    'starting': 'thinking',
    'running': 'thinking',
    'completed': 'completed',
    'failed': 'error'
  }
  return statusMap[eventType] || 'thinking'
}

// 后端消息转换为前端消息
function backendMessageToFrontend(msg: BackendMessage): Message {
  return {
    id: msg.id,
    role: msg.role as 'user' | 'assistant',
    content: msg.content,
    timestamp: formatTimestamp(msg.timestamp)
  }
}

// 后端会话转换为前端会话
function backendSessionToFrontend(session: BackendSession | BackendSessionDetail): ChatSession {
  const detail = session as BackendSessionDetail
  return {
    id: session.id,
    title: session.title,
    messages: detail.messages ? detail.messages.map(backendMessageToFrontend) : [],
    createdAt: formatTimestamp(session.createdAt),
    updatedAt: formatTimestamp(session.updatedAt)
  }
}

export const useChatStore = defineStore('chat', () => {
  // 状态
  const sessions = ref<ChatSession[]>([])
  const currentSessionId = ref<string>('')
  const currentStrategy = ref<Strategy>('deep-research')
  const isProcessing = ref<boolean>(false)
  const isLoading = ref<boolean>(false)

  // 计算属性
  const currentSession = computed(() => {
    return sessions.value.find(s => s.id === currentSessionId.value)
  })

  const messages = computed(() => {
    return currentSession.value?.messages || []
  })

  // API 调用方法
  async function loadSessions() {
    try {
      isLoading.value = true
      const response = await fetch(`${API_BASE}/api/sessions`)
      if (!response.ok) throw new Error('加载会话列表失败')
      const backendSessions: BackendSession[] = await response.json()
      sessions.value = backendSessions.map(backendSessionToFrontend)

      // 如果没有会话，创建一个默认会话
      if (sessions.value.length === 0) {
        await createNewSession()
      } else if (!currentSessionId.value) {
        // 设置当前会话为第一个
        currentSessionId.value = sessions.value[0].id
      }
    } catch (error) {
      console.error('加载会话列表失败:', error)
      // 失败时创建默认会话
      if (sessions.value.length === 0) {
        await createNewSession()
      }
    } finally {
      isLoading.value = false
    }
  }

  async function loadSessionDetail(sessionId: string) {
    try {
      const response = await fetch(`${API_BASE}/api/sessions/${sessionId}`)
      if (!response.ok) throw new Error('加载会话详情失败')
      const backendSession: BackendSessionDetail = await response.json()
      const frontendSession = backendSessionToFrontend(backendSession)

      // 更新或添加会话
      const index = sessions.value.findIndex(s => s.id === sessionId)
      if (index >= 0) {
        sessions.value[index] = frontendSession
      } else {
        sessions.value.unshift(frontendSession)
      }
    } catch (error) {
      console.error('加载会话详情失败:', error)
    }
  }

  async function createNewSession() {
    try {
      const response = await fetch(`${API_BASE}/api/sessions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          agentName: useAgentStore().currentAgent,
          title: '新对话'
        })
      })
      if (!response.ok) throw new Error('创建会话失败')
      const backendSession: BackendSessionDetail = await response.json()
      const newSession = backendSessionToFrontend(backendSession)
      sessions.value.unshift(newSession)
      currentSessionId.value = newSession.id
      return newSession
    } catch (error) {
      console.error('创建会话失败:', error)
      // 降级：创建本地会话
      const newSession: ChatSession = {
        id: `session-${Date.now()}`,
        title: '新对话',
        messages: [],
        createdAt: new Date().toLocaleString('zh-CN'),
        updatedAt: new Date().toLocaleString('zh-CN')
      }
      sessions.value.unshift(newSession)
      currentSessionId.value = newSession.id
      return newSession
    }
  }

  async function deleteSession(sessionId: string) {
    try {
      const response = await fetch(`${API_BASE}/api/sessions/${sessionId}`, {
        method: 'DELETE'
      })
      if (!response.ok) throw new Error('删除会话失败')

      const index = sessions.value.findIndex(s => s.id === sessionId)
      if (index > -1) {
        sessions.value.splice(index, 1)
        if (currentSessionId.value === sessionId && sessions.value.length > 0) {
          currentSessionId.value = sessions.value[0]?.id || ''
        }
      }
    } catch (error) {
      console.error('删除会话失败:', error)
      // 降级：仅删除本地会话
      const index = sessions.value.findIndex(s => s.id === sessionId)
      if (index > -1) {
        sessions.value.splice(index, 1)
        if (currentSessionId.value === sessionId && sessions.value.length > 0) {
          currentSessionId.value = sessions.value[0]?.id || ''
        }
      }
    }
  }

  function switchSession(sessionId: string) {
    currentSessionId.value = sessionId
    // 从后端加载会话详情
    loadSessionDetail(sessionId)
  }

  // 发送消息
  function sendMessage(content: string) {
    if (!content.trim() || isProcessing.value) return

    const agentStore = useAgentStore()

    const newMessage: Message = {
      id: Date.now().toString(),
      role: 'user',
      content,
      timestamp: new Date().toLocaleString('zh-CN')
    }

    // 添加用户消息到本地状态
    if (currentSession.value) {
      currentSession.value.messages.push(newMessage)
      currentSession.value.updatedAt = new Date().toLocaleString('zh-CN')
    }

    // 创建 AI 消息占位符
    const aiMessage: Message = {
      id: (Date.now() + 1).toString(),
      role: 'assistant',
      content: '',
      timestamp: new Date().toLocaleString('zh-CN'),
      status: 'thinking'
    }

    if (currentSession.value) {
      currentSession.value.messages.push(aiMessage)
    }

    // 连接 SSE
    isProcessing.value = true
    const params = new URLSearchParams()
    params.append('input', content)
    // 传递会话ID以支持连续对话
    if (currentSessionId.value) {
      params.append('sessionId', currentSessionId.value)
    }
    const url = `${API_BASE}/api/stream/agent/${encodeURIComponent(agentStore.currentAgent)}/execute?${params.toString()}`
    console.log('SSE URL:', url)
    const eventSource = new EventSource(url)

    // 标记连接是否正常完成
    let completed = false

    eventSource.onopen = () => {
      console.log('SSE 连接已打开')
    }

    eventSource.onmessage = (event) => {
      try {
        const data: AgentEvent = JSON.parse(event.data)
        console.log('收到事件:', data.eventType, data)

        // 添加到 AgentTimeline（内部会过滤running的LLM事件）
        const agentTimelineStore = useAgentTimelineStore()
        agentTimelineStore.addEvent({
          eventType: data.eventType as any,
          nodeId: data.nodeId || '',
          nodeType: data.nodeType as any || 'custom',
          stateData: data.stateData || {},
          message: data.message,
          timestamp: data.timestamp || new Date().toISOString(),
          title: data.title,
          startTime: data.startTime,
          endTime: data.endTime,
          nodeErrorMessage: data.nodeErrorMessage,
          logs: data.logs
        })

        // 处理标题生成事件
        if (data.eventType === 'TITLE_GENERATED' && data.metadata?.title) {
          const session = sessions.value.find(s => s.id === currentSessionId.value)
          if (session) {
            session.title = data.metadata.title as string
          }
        }

        // 更新消息状态
        const msgIndex = currentSession.value?.messages.findIndex(m => m.id === aiMessage.id)
        if (msgIndex !== undefined && msgIndex >= 0 && currentSession.value) {
          const msg = currentSession.value.messages[msgIndex]
          msg.status = mapEventTypeToMessageStatus(data.eventType)

          switch (data.eventType) {
            case 'running':
              // 流式输出：追加 message 内容
              if (data.message) {
                msg.content += data.message
              }
              break
            case 'GRAPH_COMPLETED':
              // 图完成：标记为已完成，关闭 SSE 连接
              completed = true
              isProcessing.value = false
              msg.status = 'completed'
              eventSource.close()
              break
            case 'completed':
              // 节点完成：不关闭连接，继续等待后续节点
              break
            case 'failed':
              // 失败：显示错误信息，关闭 SSE 连接
              msg.content = `错误: ${data.nodeErrorMessage || data.error || '执行失败'}`
              isProcessing.value = false
              eventSource.close()
              break
          }
        }

        if (currentSession.value) {
          currentSession.value.updatedAt = new Date().toLocaleString('zh-CN')
        }
      } catch (error) {
        console.error('Failed to parse SSE event:', error)
      }
    }

    eventSource.onerror = (error) => {
      console.error('SSE connection error:', error)
      console.error('EventSource readyState:', eventSource.readyState)
      console.error('EventSource URL:', eventSource.url)
      console.error('Completed:', completed)

      if (completed) {
        console.log('连接正常完成')
        isProcessing.value = false
        return
      }

      const msgIndex = currentSession.value?.messages.findIndex(m => m.id === aiMessage.id)
      if (msgIndex !== undefined && msgIndex >= 0 && currentSession.value) {
        currentSession.value.messages[msgIndex].content = '连接错误，请稍后重试'
        currentSession.value.messages[msgIndex].status = 'error'
      }
      isProcessing.value = false
      eventSource.close()
    }
  }

  function setStrategy(strategy: Strategy) {
    currentStrategy.value = strategy
  }

  return {
    // 状态
    sessions,
    currentSessionId,
    currentStrategy,
    isProcessing,
    isLoading,
    // 计算属性
    currentSession,
    messages,
    // 操作
    loadSessions,
    loadSessionDetail,
    sendMessage,
    switchSession,
    createNewSession,
    deleteSession,
    setStrategy
  }
})
