import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { Message, ChatSession, Strategy } from '@/types/message'
import { useAgentStore } from './agent'
import { useAgentTimelineStore } from './agentTimeline'
import { API_BASE, DEFAULT_SESSION_TITLE } from '@/utils/constants'
import { formatTimestamp, mapEventTypeToMessageStatus, generateSessionId } from '@/utils/helpers'

// SSE 事件数据类型（与后端 AgentResponse 一致）
interface AgentEvent {
  eventType: string
  nodeId?: string
  nodeType?: string  // 'llm' | 'tool' | 'custom'
  stateData?: Record<string, unknown>
  message?: string
  timestamp: string
  executionId?: string
  error?: string
  metadata?: Record<string, unknown>
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

// 创建本地会话
function createLocalSession(): ChatSession {
  const now = new Date().toLocaleString('zh-CN')
  return {
    id: generateSessionId(),
    title: DEFAULT_SESSION_TITLE,
    messages: [],
    createdAt: now,
    updatedAt: now
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
      const result: { code: number; message: string; data: BackendSession[] } = await response.json()
      const backendSessions = result.data || []
      sessions.value = backendSessions.map(backendSessionToFrontend)

      // 如果没有会话，创建一个默认会话
      if (sessions.value.length === 0) {
        await createNewSession()
      } else if (!currentSessionId.value) {
        // 设置当前会话为第一个
        const firstSession = sessions.value[0]
        if (firstSession) {
          currentSessionId.value = firstSession.id
        }
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
      const result: { code: number; message: string; data: BackendSessionDetail } = await response.json()
      const frontendSession = backendSessionToFrontend(result.data)

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
    const agentStore = useAgentStore()

    try {
      const response = await fetch(`${API_BASE}/api/sessions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          agentName: agentStore.currentAgent,
          title: DEFAULT_SESSION_TITLE
        })
      })

      if (!response.ok) {
        throw new Error('创建会话失败')
      }

      const result: { code: number; message: string; data: BackendSessionDetail } = await response.json()
      const newSession = backendSessionToFrontend(result.data)
      sessions.value.unshift(newSession)
      currentSessionId.value = newSession.id
      return newSession
    } catch (error) {
      console.error('创建会话失败:', error)
      // 降级：创建本地会话
      const newSession = createLocalSession()
      sessions.value.unshift(newSession)
      currentSessionId.value = newSession.id
      return newSession
    }
  }

  async function deleteSession(sessionId: string) {
    // 删除会话的通用逻辑
    function removeSession() {
      const index = sessions.value.findIndex(s => s.id === sessionId)
      if (index > -1) {
        sessions.value.splice(index, 1)
        if (currentSessionId.value === sessionId && sessions.value.length > 0) {
          currentSessionId.value = sessions.value[0]?.id || ''
        }
      }
    }

    try {
      const response = await fetch(`${API_BASE}/api/sessions/${sessionId}`, {
        method: 'DELETE'
      })

      if (!response.ok) {
        throw new Error('删除会话失败')
      }

      removeSession()
    } catch (error) {
      console.error('删除会话失败:', error)
      // 降级：仅删除本地会话
      removeSession()
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
    const agentTimelineStore = useAgentTimelineStore()

    // 创建用户消息
    const userMessage: Message = {
      id: `${Date.now()}`,
      role: 'user',
      content,
      timestamp: new Date().toLocaleString('zh-CN')
    }

    // 创建 AI 消息占位符
    const aiMessage: Message = {
      id: `${Date.now() + 1}`,
      role: 'assistant',
      content: '',
      timestamp: new Date().toLocaleString('zh-CN'),
      status: 'thinking'
    }

    // 添加消息到当前会话
    if (currentSession.value) {
      currentSession.value.messages.push(userMessage, aiMessage)
      currentSession.value.updatedAt = new Date().toLocaleString('zh-CN')
    }

    // 连接 SSE
    isProcessing.value = true
    const params = new URLSearchParams({
      input: content,
      ...(currentSessionId.value && { sessionId: currentSessionId.value })
    })

    const url = `${API_BASE}/api/stream/agent/${encodeURIComponent(agentStore.currentAgent)}/execute?${params.toString()}`
    console.log('SSE URL:', url)

    const eventSource = new EventSource(url)
    let completed = false

    // SSE 连接打开
    eventSource.onopen = () => {
      console.log('SSE 连接已打开')
    }

    // 处理 SSE 消息事件
    eventSource.onmessage = (event) => {
      handleSSEMessage(event, aiMessage, agentTimelineStore, eventSource, () => {
        completed = true
        isProcessing.value = false
      })
    }

    // 处理 SSE 错误
    eventSource.onerror = () => {
      handleSSEError(eventSource, aiMessage, completed)
    }
  }

  // 处理 SSE 消息事件
  function handleSSEMessage(
    event: MessageEvent,
    aiMessage: Message,
    agentTimelineStore: ReturnType<typeof useAgentTimelineStore>,
    eventSource: EventSource,
    onComplete: () => void
  ) {
    try {
      const data: AgentEvent = JSON.parse(event.data)
      console.log('收到事件:', data.eventType, data)

      // 添加到时间线
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

      // 更新 AI 消息
      updateAIMessage(aiMessage, data, eventSource, onComplete)

      // 更新会话时间戳
      if (currentSession.value) {
        currentSession.value.updatedAt = new Date().toLocaleString('zh-CN')
      }
    } catch (error) {
      console.error('Failed to parse SSE event:', error)
    }
  }

  // 更新 AI 消息状态
  function updateAIMessage(
    aiMessage: Message,
    data: AgentEvent,
    eventSource: EventSource,
    onComplete: () => void
  ) {
    const msgIndex = currentSession.value?.messages.findIndex(m => m.id === aiMessage.id)

    if (msgIndex === undefined || msgIndex < 0 || !currentSession.value) {
      return
    }

    const messages = currentSession.value.messages
    const msg = messages[msgIndex]

    if (!msg) {
      return
    }

    msg.status = mapEventTypeToMessageStatus(data.eventType)

    switch (data.eventType) {
      case 'running':
        // 流式输出：追加消息内容
        if (data.message) {
          msg.content += data.message
        }
        break

      case 'GRAPH_COMPLETED':
        // 图完成：标记为已完成，关闭 SSE 连接
        msg.status = 'completed'
        onComplete()
        eventSource.close()
        break

      case 'completed':
        // 节点完成：不关闭连接，继续等待后续节点
        break

      case 'failed':
        // 失败：显示错误信息，关闭 SSE 连接
        msg.content = `错误: ${data.nodeErrorMessage || data.error || '执行失败'}`
        onComplete()
        eventSource.close()
        break
    }
  }

  // 处理 SSE 错误
  function handleSSEError(eventSource: EventSource, aiMessage: Message, completed: boolean) {
    console.error('SSE connection error:', {
      readyState: eventSource.readyState,
      url: eventSource.url,
      completed
    })

    if (completed) {
      console.log('连接正常完成')
      isProcessing.value = false
      return
    }

    // 更新消息为错误状态
    const msgIndex = currentSession.value?.messages.findIndex(m => m.id === aiMessage.id)
    if (msgIndex !== undefined && msgIndex >= 0 && currentSession.value) {
      const msg = currentSession.value.messages[msgIndex]
      if (msg) {
        msg.content = '连接错误，请稍后重试'
        msg.status = 'error'
      }
    }

    isProcessing.value = false
    eventSource.close()
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
