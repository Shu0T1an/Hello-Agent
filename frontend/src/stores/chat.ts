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

// Agent 执行请求 DTO（与后端 AgentExecuteRequest 一致）
export interface AgentExecuteRequest {
  input?: string
  sessionId?: string
  timeout?: number
  initialState?: Record<string, unknown>
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
  const currentKnowledgeBaseId = ref<string>('')

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
  async function sendMessage(content: string) {
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

    // 创建 AbortController 用于取消请求
    const controller = new AbortController()
    isProcessing.value = true

    const request: AgentExecuteRequest = {
      input: content,
      sessionId: currentSessionId.value || undefined,
      timeout: undefined, // 使用默认超时
      initialState: currentKnowledgeBaseId.value ? { knowledgeBaseId: currentKnowledgeBaseId.value } : undefined
    }

    const url = `${API_BASE}/api/stream/agent/${encodeURIComponent(agentStore.currentAgent)}/execute`
    console.log('POST URL:', url)

    try {
      // 使用 fetch 发送 POST 请求，绑定 AbortSignal
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(request),
        signal: controller.signal
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      // 从响应体读取 SSE 流
      const reader = response.body?.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      if (!reader) {
        throw new Error('无法获取响应流')
      }

      // 读取流
      while (true) {
        const { done, value } = await reader.read()
        console.log('[read] done:', done, 'value length:', value?.length)

        // 解码数据（包括最后一次）
        if (value) {
          const decoded = decoder.decode(value, { stream: true })
          console.log('[read] decoded:', decoded)
          buffer += decoded
        }

        console.log('[read] current buffer length:', buffer.length, 'content:', buffer.substring(0, 200))

        if (done) {
          console.log('=== SSE 流读取完成 ===')
          console.log('=== 最终 buffer length:', buffer.length, 'content:', buffer)
          // 流结束时，处理 buffer 中的剩余数据
          if (buffer.trim()) {
            console.log('=== 处理 buffer 中的剩余数据 ===')
            processSSEBuffer(buffer, aiMessage, agentTimelineStore, controller)
            buffer = ''
          } else {
            console.log('=== buffer 为空，没有数据可处理 ===')
          }
          break
        }

        // 使用 \n\n 分隔符（SSE 标准）处理粘包
        let parts = buffer.split('\n\n')
        buffer = parts.pop() || ''

        for (const eventBlock of parts) {
          // SSE 事件块可能包含多行（id: xxx\ndata: {...}）
          // 逐行查找 data: 开头的行
          const lines = eventBlock.split('\n')
          for (const line of lines) {
            const trimmedLine = line.trim()
            if (!trimmedLine.startsWith('data:')) continue

            const jsonStr = trimmedLine.replace(/^data:\s*/, '').trim()
            if (!jsonStr) continue

            try {
              const data: AgentEvent = JSON.parse(jsonStr)
              console.log(`[SSE] ${data.eventType} | ${data.nodeId || 'N/A'}`)

              // 直接传入解析好的数据，无需 mock EventSource
              handleSSEMessage(data, aiMessage, agentTimelineStore, controller, () => {
                isProcessing.value = false
              })
            } catch (error) {
              console.error('解析 SSE 数据失败:', error, jsonStr)
            }
          }
        }
      }
    } catch (error: any) {
      if (error.name === 'AbortError') {
        console.log('Fetch aborted')
      } else {
        console.error('SSE 连接错误:', error)
        handleSSEError(aiMessage)
      }
    } finally {
      isProcessing.value = false
    }
  }

  // 处理 buffer 中的剩余数据
  function processSSEBuffer(
    buffer: string,
    aiMessage: Message,
    agentTimelineStore: ReturnType<typeof useAgentTimelineStore>,
    controller: AbortController
  ) {
    // 使用 \n\n 分隔符处理事件块
    const eventBlocks = buffer.split('\n\n')
    for (const eventBlock of eventBlocks) {
      // 逐行查找 data: 开头的行
      const lines = eventBlock.split('\n')
      for (const line of lines) {
        const trimmedLine = line.trim()
        if (!trimmedLine.startsWith('data:')) continue

        const jsonStr = trimmedLine.replace(/^data:\s*/, '').trim()
        if (!jsonStr) continue

        try {
          const data: AgentEvent = JSON.parse(jsonStr)
          console.log(`[SSE] 最后处理 ${data.eventType} | ${data.nodeId || 'N/A'}`)
          handleSSEMessage(data, aiMessage, agentTimelineStore, controller, () => {
            isProcessing.value = false
          })
        } catch (error) {
          console.error('解析 buffer SSE 数据失败:', error, jsonStr)
        }
      }
    }
  }

  // 处理 SSE 消息事件
  function handleSSEMessage(
    data: AgentEvent,
    aiMessage: Message,
    agentTimelineStore: ReturnType<typeof useAgentTimelineStore>,
    abortController: AbortController,
    onComplete: () => void
  ) {
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

    // 直接调用 updateAIMessage，无需再次解析
    updateAIMessage(aiMessage, data, abortController, onComplete)

    // 更新会话时间戳
    if (currentSession.value) {
      currentSession.value.updatedAt = new Date().toLocaleString('zh-CN')
    }
  }

  // 更新 AI 消息状态
  function updateAIMessage(
    aiMessage: Message,
    data: AgentEvent,
    abortController: AbortController,
    onComplete: () => void
  ) {
    const msgIndex = currentSession.value?.messages.findIndex(m => m.id === aiMessage.id)

    if (msgIndex === undefined || msgIndex < 0 || !currentSession.value) {
      console.warn('[updateAIMessage] 消息未找到:', aiMessage.id)
      return
    }

    const messages = currentSession.value.messages

    // 验证消息存在
    const initialMsg = messages[msgIndex]
    if (!initialMsg) {
      console.warn('[updateAIMessage] 消息对象为空')
      return
    }

    console.log(`[updateAIMessage] 处理事件: eventType=${data.eventType}`)

    switch (data.eventType) {
      case 'running':
        // 增量追加：流式输出 - 重新获取最新消息引用
        const currentMsg = messages[msgIndex]
        console.log('[updateAIMessage] running - 当前消息:', currentMsg)
        console.log('[updateAIMessage] running - 收到内容:', data.message)
        if (data.message) {
          messages[msgIndex] = {
            ...currentMsg,
            status: 'thinking',
            content: currentMsg.content + data.message
          }
          console.log('[updateAIMessage] running - 更新后:', messages[msgIndex])
        }
        break

      case 'completed':
        // 全量覆盖：仅 _AGENT_MODEL_ 节点 - 重新获取最新消息引用
        const completedMsg = messages[msgIndex]
        if (data.nodeId === '_AGENT_MODEL_' && data.message) {
          console.log('[updateAIMessage] completed - 全量覆盖:', data.message)
          messages[msgIndex] = {
            ...completedMsg,
            status: 'thinking',
            content: data.message  // 全量覆盖，防止增量丢失
          }
        }
        break

      case 'GRAPH_COMPLETED':
        // 重新获取最新消息引用
        const graphCompletedMsg = messages[msgIndex]
        console.log('[updateAIMessage] GRAPH_COMPLETED - 完成')
        messages[msgIndex] = { ...graphCompletedMsg, status: 'completed' }
        onComplete()
        abortController.abort()  // 真正停止流
        break

      case 'failed':
        // 重新获取最新消息引用
        const failedMsg = messages[msgIndex]
        console.log('[updateAIMessage] failed - 失败:', data.nodeErrorMessage)
        messages[msgIndex] = {
          ...failedMsg,
          status: 'error',
          content: failedMsg.content + `\n(错误: ${data.nodeErrorMessage || '执行失败'})`
        }
        onComplete()
        abortController.abort()
        break
    }
  }

  // 处理 SSE 错误
  function handleSSEError(aiMessage: Message) {
    console.error('SSE connection error')

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
  }

  function setStrategy(strategy: Strategy) {
    currentStrategy.value = strategy
  }

  function setKnowledgeBaseId(knowledgeBaseId: string) {
    currentKnowledgeBaseId.value = knowledgeBaseId
  }

  return {
    // 状态
    sessions,
    currentSessionId,
    currentStrategy,
    isProcessing,
    isLoading,
    currentKnowledgeBaseId,
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
    setStrategy,
    setKnowledgeBaseId
  }
})
