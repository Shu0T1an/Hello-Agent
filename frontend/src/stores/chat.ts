import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { Message, ChatSession, Strategy, MessageAttachment, CitationReference, MessageRole } from '@/types/message'
import { useAgentStore } from './agent'
import { useAgentTimelineStore } from './agentTimeline'
import { API_BASE, DEFAULT_SESSION_TITLE } from '@/utils/constants'
import { formatTimestamp, generateSessionId } from '@/utils/helpers'
import { uploadTemporaryFiles, type TemporaryFileContent } from '@/api/file'

// SSE 事件数据类型（与后端 AgentResponse 一致）
interface AgentEvent {
  eventType: string
  nodeId?: string
  nodeType?: string  // 'llm' | 'tool' | 'custom'
  stateData?: Record<string, unknown> & {
    interruption?: {
      metadata: {
        nodeId: string
        message: string
        customData: {
          tool_feedbacks?: Array<{
            id: string
            name: string
            arguments: Record<string, unknown>
            description: string
            result: string
          }>
          message?: string
        }
        timestamp: string
      }
      checkpointId: string
    }
  }
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
  fileContents?: TemporaryFileContent[]
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
  metadata?: {
    tool_calls?: Array<{ id: string; name: string; type: string; arguments: string }>
    tool_responses?: Array<{ id: string; name: string; response: string }>
  }
}

// 后端消息转换为前端消息
function backendMessageToFrontend(msg: BackendMessage): Message {
  return {
    id: msg.id,
    role: msg.role as MessageRole,
    content: msg.content,
    timestamp: formatTimestamp(msg.timestamp),
    metadata: msg.metadata ? {
      tool_calls: msg.metadata.tool_calls,
      tool_responses: msg.metadata.tool_responses
    } : undefined
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

  async function deleteAllSessions() {
    try {
      const response = await fetch(`${API_BASE}/api/sessions/delete-all`, {
        method: 'DELETE'
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }

      const result: { code: number; message: string; data?: { count: number } } = await response.json()

      // 清空会话列表
      sessions.value = []
      currentSessionId.value = ''
      // 创建默认会话
      await createNewSession()

      return result.data?.count ?? 0
    } catch (error) {
      console.error('删除所有会话失败:', error)
      // 即使 API 调用失败，也清空本地会话列表并创建默认会话
      sessions.value = []
      currentSessionId.value = ''
      await createNewSession()
      throw error
    }
  }

  function switchSession(sessionId: string) {
    currentSessionId.value = sessionId
    // 从后端加载会话详情
    loadSessionDetail(sessionId)
  }

  // 发送消息
  async function sendMessage(content: string, files?: File[]) {
    if ((!content.trim() && !files?.length) || isProcessing.value) return

    const agentStore = useAgentStore()
    const agentTimelineStore = useAgentTimelineStore()

    // 上传文件（如果有）
    let fileContents: TemporaryFileContent[] | null = null
    let attachments: MessageAttachment[] | undefined

    if (files && files.length > 0) {
      try {
        const response = await uploadTemporaryFiles(files, currentSessionId.value || undefined)
        fileContents = response.fileContents
        attachments = files.map((f, i) => ({
          id: `attach-${Date.now()}-${i}`,
          fileName: f.name,
          fileSize: f.size,
          fileType: f.type
        }))
        console.log('文件上传成功:', response.summary)
      } catch (error) {
        console.error('文件上传失败:', error)
        alert('文件上传失败: ' + (error as Error).message)
        return
      }
    }

    // 创建用户消息
    const userMessage: Message = {
      id: `${Date.now()}`,
      role: 'user',
      content,
      timestamp: new Date().toLocaleString('zh-CN'),
      attachments
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
      initialState: currentKnowledgeBaseId.value ? { knowledgeBaseId: currentKnowledgeBaseId.value } : undefined,
      fileContents: fileContents || undefined
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

    // 检测并添加工具调用消息到聊天框
    handleToolCallMessage(data)

    // 检测并添加工具执行消息到聊天框
    handleToolExecutionMessage(data)

    // 处理标题生成事件
    if (data.eventType === 'TITLE_GENERATED' && data.metadata?.title) {
      const session = sessions.value.find(s => s.id === currentSessionId.value)
      if (session) {
        session.title = data.metadata.title as string
      }
    }

    // 处理中断事件（HILP - 人工在环）
    if (data.eventType === 'INTERRUPTION') {
      console.log('[INTERRUPTION] 收到中断事件:', data)
      // 停止流处理
      abortController.abort()
      // 将消息状态设置为等待审批
      const msgIndex = currentSession.value?.messages.findIndex(m => m.id === aiMessage.id)
      if (msgIndex !== undefined && msgIndex >= 0 && currentSession.value) {
        const msg = currentSession.value.messages[msgIndex]
        if (msg) {
          msg.status = 'interrupted'
          // 保存完整的审批数据
          const toolFeedbacks = data.stateData?.interruption?.metadata?.customData?.tool_feedbacks
          if (toolFeedbacks && toolFeedbacks.length > 0) {
            ;(msg as any).interruptionData = {
              message: data.stateData?.interruption?.metadata?.message || '需要人工审批',
              tool_feedbacks: toolFeedbacks
            }
          }
          // 保存检查点 ID 和 threadId 以便后续恢复
          if (data.stateData?.interruption?.checkpointId) {
            ;(msg as any).checkpointId = data.stateData.interruption.checkpointId
            // 优先使用 threadId，如果没有则使用当前会话 ID
            ;(msg as any).threadId = data.stateData.interruption.threadId || currentSessionId.value
          }
        }
      }
      onComplete()
      return
    }

    // 直接调用 updateAIMessage，无需再次解析
    updateAIMessage(aiMessage, data, abortController, onComplete)

    // 更新会话时间戳
    if (currentSession.value) {
      currentSession.value.updatedAt = new Date().toLocaleString('zh-CN')
    }
  }

  // 处理工具调用消息（Function Call）
  function handleToolCallMessage(data: AgentEvent) {
    // 只在 LLM 节点完成时处理
    if (data.nodeType !== 'llm' || data.eventType !== 'completed') return

    const toolCalls = data.stateData?.execution_record?.toolCalls
    if (!toolCalls || toolCalls.length === 0) return

    // 检查是否已经添加过这个工具调用消息
    const existingMsg = currentSession.value?.messages.find(m =>
      m.role === 'tool_call' &&
      m.metadata?.tool_calls?.[0]?.id === toolCalls[0]?.id
    )
    if (existingMsg) return

    // 创建 tool_call 消息
    const toolCallMessage: Message = {
      id: `tool-call-${Date.now()}-${toolCalls[0]?.id}`,
      role: 'tool_call',
      content: '',
      timestamp: new Date().toLocaleString('zh-CN'),
      status: 'completed',
      metadata: {
        tool_calls: toolCalls.map((tc: any) => ({
          id: tc.id,
          name: tc.name,
          type: tc.type || 'function',
          arguments: tc.arguments
        }))
      }
    }

    console.log('[handleToolCallMessage] 添加工具调用消息:', toolCallMessage)
    currentSession.value?.messages.push(toolCallMessage)
  }

  // 处理工具执行消息（Tool Execution）
  function handleToolExecutionMessage(data: AgentEvent) {
    // 检查是否是 tool 节点且已完成
    if (data.nodeType !== 'tool' || data.eventType !== 'completed') return

    const executions = data.stateData?.execution_record?.executions
    if (!executions || executions.length === 0) return

    // 检查是否已经添加过这个工具执行消息
    const existingMsg = currentSession.value?.messages.find(m =>
      m.role === 'tool_response' &&
      m.metadata?.tool_responses?.[0]?.id === executions[0]?.id
    )
    if (existingMsg) return

    // 创建 tool_response 消息
    const toolResponseMessage: Message = {
      id: `tool-response-${Date.now()}-${executions[0]?.id}`,
      role: 'tool_response',
      content: '',
      timestamp: new Date().toLocaleString('zh-CN'),
      status: 'completed',
      metadata: {
        tool_responses: executions.map((exec: any) => {
          // 处理 result 字段 - 如果是对象则转换为 JSON 字符串
          let resultStr = exec.result
          if (typeof exec.result === 'object' && exec.result !== null) {
            resultStr = JSON.stringify(exec.result, null, 2)
          } else if (resultStr === undefined || resultStr === null) {
            resultStr = 'No result'
          }
          return {
            id: exec.id,
            name: exec.name,
            response: resultStr
          }
        })
      }
    }

    console.log('[handleToolExecutionMessage] 添加工具执行消息:', toolResponseMessage)
    currentSession.value?.messages.push(toolResponseMessage)
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
        if (data.message && currentMsg) {
          messages[msgIndex] = {
            ...currentMsg,
            id: currentMsg.id,
            role: currentMsg.role,
            content: currentMsg.content + data.message,
            timestamp: currentMsg.timestamp,
            status: 'thinking'
          }
          console.log('[updateAIMessage] running - 更新后:', messages[msgIndex])
        }
        break

      case 'completed':
        // 全量覆盖：仅 _AGENT_MODEL_ 节点 - 重新获取最新消息引用
        const completedMsg = messages[msgIndex]
        if (data.nodeId === '_AGENT_MODEL_' && data.message && completedMsg) {
          console.log('[updateAIMessage] completed - _AGENT_MODEL_')
          console.log('[updateAIMessage] metadata:', data.metadata)
          console.log('[updateAIMessage] metadata.citations:', data.metadata?.citations)

          // 提取 citations
          const citations = data.metadata?.citations as CitationReference[] | undefined

          messages[msgIndex] = {
            ...completedMsg,
            id: completedMsg.id,
            role: completedMsg.role,
            content: data.message,
            timestamp: completedMsg.timestamp,
            status: 'thinking',
            citations: citations?.length ? citations : undefined
          }

          console.log('[updateAIMessage] 已设置 citations:', citations?.length || 0)
        }
        break

      case 'GRAPH_COMPLETED':
        // 重新获取最新消息引用
        const graphCompletedMsg = messages[msgIndex]
        if (graphCompletedMsg) {
          console.log('[updateAIMessage] GRAPH_COMPLETED - 完成')
          console.log('[updateAIMessage] citations 已存在:', graphCompletedMsg.citations?.length || 0)
          messages[msgIndex] = {
            ...graphCompletedMsg,
            id: graphCompletedMsg.id,
            role: graphCompletedMsg.role,
            content: graphCompletedMsg.content,
            timestamp: graphCompletedMsg.timestamp,
            status: 'completed'
            // citations 已经在 completed 事件中设置了
          }
        }
        onComplete()
        abortController.abort()  // 真正停止流
        break

      case 'failed':
        // 重新获取最新消息引用
        const failedMsg = messages[msgIndex]
        if (failedMsg) {
          console.log('[updateAIMessage] failed - 失败:', data.nodeErrorMessage)
          messages[msgIndex] = {
            ...failedMsg,
            id: failedMsg.id,
            role: failedMsg.role,
            content: failedMsg.content + `\n(错误: ${data.nodeErrorMessage || '执行失败'})`,
            timestamp: failedMsg.timestamp,
            status: 'error'
          }
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

  // Resume agent execution after approval
  async function resumeAgent(
    agentName: string,
    checkpointId: string,
    sessionId: string,
    feedbacks: Array<{ id: string; name: string; result: string }>,
    messageId: string
  ) {
    if (isProcessing.value) return

    const agentTimelineStore = useAgentTimelineStore()

    // 找到对应的 AI 消息
    const msgIndex = currentSession.value?.messages.findIndex(m => m.id === messageId)
    if (msgIndex === undefined || msgIndex < 0 || !currentSession.value) {
      console.error('[resumeAgent] 消息未找到:', messageId)
      return
    }

    const aiMessage = currentSession.value.messages[msgIndex]

    // 更新消息状态为思考中
    if (aiMessage) {
      aiMessage.status = 'thinking'
      aiMessage.content += '\n\n--- 审批通过，继续执行 ---\n\n'
    }

    // 创建 AbortController
    const controller = new AbortController()
    isProcessing.value = true

    const request = {
      checkpointId,
      feedbackData: { feedbacks },
      sessionId
    }

    const url = `${API_BASE}/api/stream/agent/${encodeURIComponent(agentName)}/resume`
    console.log('[resumeAgent] POST URL:', url)

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
        signal: controller.signal
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const reader = response.body?.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      if (!reader) {
        throw new Error('无法获取响应流')
      }

      while (true) {
        const { done, value } = await reader.read()
        console.log('[resumeAgent] done:', done, 'value length:', value?.length)

        if (value) {
          const decoded = decoder.decode(value, { stream: true })
          buffer += decoded
        }

        if (done) {
          console.log('[resumeAgent] SSE 流读取完成')
          if (buffer.trim()) {
            processSSEBuffer(buffer, aiMessage, agentTimelineStore, controller)
            buffer = ''
          }
          break
        }

        // 处理粘包
        let parts = buffer.split('\n\n')
        buffer = parts.pop() || ''

        for (const eventBlock of parts) {
          const lines = eventBlock.split('\n')
          for (const line of lines) {
            const trimmedLine = line.trim()
            if (!trimmedLine.startsWith('data:')) continue

            const jsonStr = trimmedLine.replace(/^data:\s*/, '').trim()
            if (!jsonStr) continue

            try {
              const data: AgentEvent = JSON.parse(jsonStr)
              console.log(`[resumeAgent] SSE ${data.eventType}`)

              handleSSEMessage(data, aiMessage!, agentTimelineStore, controller, () => {
                isProcessing.value = false
              })
            } catch (error) {
              console.error('[resumeAgent] 解析 SSE 数据失败:', error, jsonStr)
            }
          }
        }
      }
    } catch (error: any) {
      if (error.name === 'AbortError') {
        console.log('[resumeAgent] Fetch aborted')
      } else {
        console.error('[resumeAgent] SSE 连接错误:', error)
        handleSSEError(aiMessage!)
      }
    } finally {
      isProcessing.value = false
    }
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
    resumeAgent,
    switchSession,
    createNewSession,
    deleteSession,
    deleteAllSessions,
    setStrategy,
    setKnowledgeBaseId
  }
})
