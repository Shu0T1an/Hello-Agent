import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { Message, ChatSession, Strategy, MessageAttachment, CitationReference, MessageRole } from '@/types/message'
import { useAgentStore } from './agent'
import { useAgentTimelineStore } from './agentTimeline'
import { useTodoStore } from './todo'
import { API_BASE, DEFAULT_SESSION_TITLE } from '@/utils/constants'
import { formatTimestamp, generateSessionId } from '@/utils/helpers'
import { uploadTemporaryFiles, type TemporaryFileContent } from '@/api/file'

// SSE 浜嬩欢鏁版嵁绫诲瀷锛堜笌鍚庣 AgentResponse 涓€鑷达級
interface AgentEvent {
  eventType: string
  nodeId?: string
  nodeType?: string  // 'llm' | 'tool' | 'custom' | 'subagent'
  stateData?: Record<string, unknown> & {
    todos?: unknown
    todos_meta?: unknown
    execution_record?: {
      toolCalls?: Array<Record<string, unknown>>
      executions?: Array<Record<string, unknown>>
    }
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
      threadId?: string
    }
  }
  message?: string
  timestamp: string
  executionId?: string
  error?: string
  metadata?: Record<string, unknown>
  // 鑺傜偣鐘舵€佺浉鍏冲瓧娈?
  nodeStatus?: string           // 'starting' | 'running' | 'completed' | 'failed'
  title?: string                // 鑺傜偣鏍囬
  startTime?: string             // 寮€濮嬫椂闂?(ISO 8601)
  endTime?: string               // 缁撴潫鏃堕棿 (ISO 8601)
  logs?: string[]                // 鏃ュ織鍒楄〃
  nodeErrorMessage?: string      // 鑺傜偣閿欒淇℃伅
}

// Agent 鎵ц璇锋眰 DTO锛堜笌鍚庣 AgentExecuteRequest 涓€鑷达級
export interface AgentExecuteRequest {
  input?: string
  sessionId?: string
  timeout?: number
  initialState?: Record<string, unknown>
  fileContents?: TemporaryFileContent[]
}

// 鍚庣浼氳瘽鏁版嵁绫诲瀷
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
    think?: string
    think_delta?: string
    reasoningContent?: string
    reasoning_content?: string
  }
}

const TODO_TOOL_NAMES = new Set([
  'upsert_todos',
  'list_todos',
  'complete_todo',
  'delete_todo',
  'clear_todos'
])

function isTodoToolName(name: unknown): boolean {
  return typeof name === 'string' && TODO_TOOL_NAMES.has(name)
}

function toNonEmptyString(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined
  const trimmed = value.trim()
  return trimmed.length > 0 ? value : undefined
}

function extractThinkingDelta(metadata?: Record<string, unknown>): string | undefined {
  if (!metadata) return undefined
  return toNonEmptyString(metadata.think_delta)
    ?? toNonEmptyString(metadata.reasoningContent)
    ?? toNonEmptyString(metadata.reasoning_content)
}

function extractFinalThinking(metadata?: Record<string, unknown>): string | undefined {
  if (!metadata) return undefined
  return toNonEmptyString(metadata.think)
    ?? toNonEmptyString(metadata.reasoningContent)
    ?? toNonEmptyString(metadata.reasoning_content)
}

function normalizeToolCalls(rawCalls: unknown): Array<{ id: string; name: string; type: string; arguments: string }> {
  if (!Array.isArray(rawCalls)) return []

  return rawCalls
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const record = item as Record<string, unknown>
      const name = toNonEmptyString(record.name)
      if (!name) return null

      const id = toNonEmptyString(record.id) ?? `${name}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
      const type = toNonEmptyString(record.type) ?? 'function'
      const rawArguments = record.arguments
      let argumentsStr = ''

      if (typeof rawArguments === 'string') {
        argumentsStr = rawArguments
      } else if (rawArguments !== undefined) {
        try {
          argumentsStr = JSON.stringify(rawArguments, null, 2)
        } catch {
          argumentsStr = String(rawArguments)
        }
      }

      return {
        id,
        name,
        type,
        arguments: argumentsStr
      }
    })
    .filter((item): item is { id: string; name: string; type: string; arguments: string } => item !== null)
}

function hasToolCallPayload(data: AgentEvent): boolean {
  const fromExecutionRecord = normalizeToolCalls(data.stateData?.execution_record?.toolCalls)
  if (fromExecutionRecord.length > 0) return true
  const fromMetadata = normalizeToolCalls((data.metadata as Record<string, unknown> | undefined)?.tool_calls)
  return fromMetadata.length > 0
}

// 后端消息转换为前端消息
function backendMessageToFrontend(msg: BackendMessage): Message {
  const thinkingContent = msg.metadata
    ? extractFinalThinking(msg.metadata as Record<string, unknown>)
    : undefined

  return {
    id: msg.id,
    role: msg.role as MessageRole,
    content: msg.content,
    timestamp: formatTimestamp(msg.timestamp),
    thinkingContent,
    metadata: msg.metadata ? {
      tool_calls: msg.metadata.tool_calls,
      tool_responses: msg.metadata.tool_responses
    } : undefined
  }
}

// 鍚庣浼氳瘽杞崲涓哄墠绔細璇?
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

// 鍒涘缓鏈湴浼氳瘽
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
  // 鐘舵€?
  const sessions = ref<ChatSession[]>([])
  const currentSessionId = ref<string>('')
  const currentStrategy = ref<Strategy>('deep-research')
  const isProcessing = ref<boolean>(false)
  const isLoading = ref<boolean>(false)
  const currentKnowledgeBaseId = ref<string>('')
  const currentLlmMessageId = ref<string | null>(null)
  const todoStore = useTodoStore()

  // 璁＄畻灞炴€?
  const currentSession = computed(() => {
    return sessions.value.find(s => s.id === currentSessionId.value)
  })

  const messages = computed(() => {
    return currentSession.value?.messages || []
  })

  // API 璋冪敤鏂规硶
  async function loadSessions() {
    try {
      isLoading.value = true
      const response = await fetch(`${API_BASE}/api/sessions`)
      if (!response.ok) throw new Error('鍔犺浇浼氳瘽鍒楄〃澶辫触')
      const result: { code: number; message: string; data: BackendSession[] } = await response.json()
      const backendSessions = result.data || []
      sessions.value = backendSessions.map(backendSessionToFrontend)

      // 濡傛灉娌℃湁浼氳瘽锛屽垱寤轰竴涓粯璁や細璇?
      if (sessions.value.length === 0) {
        await createNewSession()
      } else if (!currentSessionId.value) {
        // 璁剧疆褰撳墠浼氳瘽涓虹涓€涓?
        const firstSession = sessions.value[0]
        if (firstSession) {
          currentSessionId.value = firstSession.id
          todoStore.clearOnSessionReset()
        }
      }
    } catch (error) {
      console.error('鍔犺浇浼氳瘽鍒楄〃澶辫触:', error)
      // 澶辫触鏃跺垱寤洪粯璁や細璇?
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
      if (!response.ok) throw new Error('鍔犺浇浼氳瘽璇︽儏澶辫触')
      const result: { code: number; message: string; data: BackendSessionDetail } = await response.json()
      const frontendSession = backendSessionToFrontend(result.data)

      // 鏇存柊鎴栨坊鍔犱細璇?
      const index = sessions.value.findIndex(s => s.id === sessionId)
      if (index >= 0) {
        sessions.value[index] = frontendSession
      } else {
        sessions.value.unshift(frontendSession)
      }
    } catch (error) {
      console.error('鍔犺浇浼氳瘽璇︽儏澶辫触:', error)
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
        throw new Error('鍒涘缓浼氳瘽澶辫触')
      }

      const result: { code: number; message: string; data: BackendSessionDetail } = await response.json()
      const newSession = backendSessionToFrontend(result.data)
      sessions.value.unshift(newSession)
      currentSessionId.value = newSession.id
      todoStore.clearOnSessionReset()
      return newSession
    } catch (error) {
      console.error('鍒涘缓浼氳瘽澶辫触:', error)
      // 闄嶇骇锛氬垱寤烘湰鍦颁細璇?
      const newSession = createLocalSession()
      sessions.value.unshift(newSession)
      currentSessionId.value = newSession.id
      todoStore.clearOnSessionReset()
      return newSession
    }
  }

  async function deleteSession(sessionId: string) {
    // 鍒犻櫎浼氳瘽鐨勯€氱敤閫昏緫
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
        throw new Error('鍒犻櫎浼氳瘽澶辫触')
      }

      removeSession()
    } catch (error) {
      console.error('鍒犻櫎浼氳瘽澶辫触:', error)
      // 闄嶇骇锛氫粎鍒犻櫎鏈湴浼氳瘽
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

      // 娓呯┖浼氳瘽鍒楄〃
      sessions.value = []
      currentSessionId.value = ''
      // 鍒涘缓榛樿浼氳瘽
      await createNewSession()

      return result.data?.count ?? 0
    } catch (error) {
      console.error('鍒犻櫎鎵€鏈変細璇濆け璐?', error)
      // 鍗充娇 API 璋冪敤澶辫触锛屼篃娓呯┖鏈湴浼氳瘽鍒楄〃骞跺垱寤洪粯璁や細璇?
      sessions.value = []
      currentSessionId.value = ''
      await createNewSession()
      throw error
    }
  }

  function switchSession(sessionId: string) {
    currentSessionId.value = sessionId
    todoStore.clearOnSessionReset()
    // 浠庡悗绔姞杞戒細璇濊鎯?
    loadSessionDetail(sessionId)
  }

  // 鍙戦€佹秷鎭?
  async function sendMessage(content: string, files?: File[]) {
    if ((!content.trim() && !files?.length) || isProcessing.value) return

    const agentStore = useAgentStore()
    const agentTimelineStore = useAgentTimelineStore()

    // 涓婁紶鏂囦欢锛堝鏋滄湁锛?
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
        console.log('鏂囦欢涓婁紶鎴愬姛:', response.summary)
      } catch (error) {
        console.error('鏂囦欢涓婁紶澶辫触:', error)
        alert('鏂囦欢涓婁紶澶辫触: ' + (error as Error).message)
        return
      }
    }

    // 鍒涘缓鐢ㄦ埛娑堟伅
    const userMessage: Message = {
      id: `${Date.now()}`,
      role: 'user',
      content,
      timestamp: new Date().toLocaleString('zh-CN'),
      attachments
    }

    // 娣诲姞娑堟伅鍒板綋鍓嶄細璇?
    if (currentSession.value) {
      currentSession.value.messages.push(userMessage)
      currentSession.value.updatedAt = new Date().toLocaleString('zh-CN')
    }
    currentLlmMessageId.value = null

    // 鍒涘缓 AbortController 鐢ㄤ簬鍙栨秷璇锋眰
    const controller = new AbortController()
    isProcessing.value = true

    const request: AgentExecuteRequest = {
      input: content,
      sessionId: currentSessionId.value || undefined,
      timeout: undefined, // 浣跨敤榛樿瓒呮椂
      initialState: currentKnowledgeBaseId.value ? { knowledgeBaseId: currentKnowledgeBaseId.value } : undefined,
      fileContents: fileContents || undefined
    }

    const url = `${API_BASE}/api/stream/agent/${encodeURIComponent(agentStore.currentAgent)}/execute`
    console.log('POST URL:', url)

    try {
      // 浣跨敤 fetch 鍙戦€?POST 璇锋眰锛岀粦瀹?AbortSignal
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

      // 浠庡搷搴斾綋璇诲彇 SSE 娴?
      const reader = response.body?.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      if (!reader) {
        throw new Error('Failed to read streaming response')
      }

      // 璇诲彇娴?
      while (true) {
        const { done, value } = await reader.read()
        console.log('[read] done:', done, 'value length:', value?.length)

        // 瑙ｇ爜鏁版嵁锛堝寘鎷渶鍚庝竴娆★級
        if (value) {
          const decoded = decoder.decode(value, { stream: true })
          console.log('[read] decoded:', decoded)
          buffer += decoded
        }

        console.log('[read] current buffer length:', buffer.length, 'content:', buffer.substring(0, 200))

        if (done) {
          console.log('=== SSE 娴佽鍙栧畬鎴?===')
          console.log('=== 鏈€缁?buffer length:', buffer.length, 'content:', buffer)
          // 娴佺粨鏉熸椂锛屽鐞?buffer 涓殑鍓╀綑鏁版嵁
          if (buffer.trim()) {
            console.log('=== 澶勭悊 buffer 涓殑鍓╀綑鏁版嵁 ===')
            processSSEBuffer(buffer, agentTimelineStore, controller)
            buffer = ''
          } else {
            console.log('=== buffer 涓虹┖锛屾病鏈夋暟鎹彲澶勭悊 ===')
          }
          break
        }

        // 浣跨敤 \n\n 鍒嗛殧绗︼紙SSE 鏍囧噯锛夊鐞嗙矘鍖?
        let parts = buffer.split('\n\n')
        buffer = parts.pop() || ''

        for (const eventBlock of parts) {
          // SSE 浜嬩欢鍧楀彲鑳藉寘鍚琛岋紙id: xxx\ndata: {...}锛?
          // 閫愯鏌ユ壘 data: 寮€澶寸殑琛?
          const lines = eventBlock.split('\n')
          for (const line of lines) {
            const trimmedLine = line.trim()
            if (!trimmedLine.startsWith('data:')) continue

            const jsonStr = trimmedLine.replace(/^data:\s*/, '').trim()
            if (!jsonStr) continue

            try {
              const data: AgentEvent = JSON.parse(jsonStr)
              console.log(`[SSE] ${data.eventType} | ${data.nodeId || 'N/A'}`)

              // 鐩存帴浼犲叆瑙ｆ瀽濂界殑鏁版嵁锛屾棤闇€ mock EventSource
              handleSSEMessage(data, agentTimelineStore, controller, () => {
                isProcessing.value = false
              })
            } catch (error) {
              console.error('瑙ｆ瀽 SSE 鏁版嵁澶辫触:', error, jsonStr)
            }
          }
        }
      }
    } catch (error: any) {
      if (error.name === 'AbortError') {
        console.log('Fetch aborted')
      } else {
        console.error('SSE 杩炴帴閿欒:', error)
        handleSSEError()
      }
    } finally {
      isProcessing.value = false
    }
  }

  // 澶勭悊 buffer 涓殑鍓╀綑鏁版嵁
  function processSSEBuffer(
    buffer: string,
    agentTimelineStore: ReturnType<typeof useAgentTimelineStore>,
    controller: AbortController
  ) {
    // 浣跨敤 \n\n 鍒嗛殧绗﹀鐞嗕簨浠跺潡
    const eventBlocks = buffer.split('\n\n')
    for (const eventBlock of eventBlocks) {
      // 閫愯鏌ユ壘 data: 寮€澶寸殑琛?
      const lines = eventBlock.split('\n')
      for (const line of lines) {
        const trimmedLine = line.trim()
        if (!trimmedLine.startsWith('data:')) continue

        const jsonStr = trimmedLine.replace(/^data:\s*/, '').trim()
        if (!jsonStr) continue

        try {
          const data: AgentEvent = JSON.parse(jsonStr)
          console.log(`[SSE] 鏈€鍚庡鐞?${data.eventType} | ${data.nodeId || 'N/A'}`)
          handleSSEMessage(data, agentTimelineStore, controller, () => {
            isProcessing.value = false
          })
        } catch (error) {
          console.error('瑙ｆ瀽 buffer SSE 鏁版嵁澶辫触:', error, jsonStr)
        }
      }
    }
  }

  // 澶勭悊 SSE 娑堟伅浜嬩欢
  function handleSSEMessage(
    data: AgentEvent,
    agentTimelineStore: ReturnType<typeof useAgentTimelineStore>,
    abortController: AbortController,
    onComplete: () => void
  ) {
    console.log('鏀跺埌浜嬩欢:', data.eventType, data)

    // 娣诲姞鍒版椂闂寸嚎
    const syncedTodoState = todoStore.syncFromStateData(data.stateData, `${data.eventType}:${data.nodeId || 'unknown'}`)
    if (syncedTodoState) {
      todoStore.markTodoToolSeen('state_data')
    }
    agentTimelineStore.addEvent({
      eventType: data.eventType as any,
      nodeId: data.nodeId || '',
      nodeType: data.nodeType as any || 'custom',
      stateData: (data.stateData || {}) as any,
      message: data.message,
      timestamp: data.timestamp || new Date().toISOString(),
      metadata: data.metadata as any,
      title: data.title,
      startTime: data.startTime,
      endTime: data.endTime,
      nodeErrorMessage: data.nodeErrorMessage,
      logs: data.logs
    })

    if (data.nodeType === 'llm' && data.eventType === 'starting') {
      const newLlmMessage: Message = {
        id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        role: 'assistant',
        content: '',
        timestamp: new Date().toLocaleString('zh-CN'),
        status: 'thinking'
      }
      currentSession.value?.messages.push(newLlmMessage)
      currentLlmMessageId.value = newLlmMessage.id
    }

    // 妫€娴嬪苟娣诲姞宸ュ叿璋冪敤娑堟伅鍒拌亰澶╂
    handleToolCallMessage(data)

    // 妫€娴嬪苟娣诲姞宸ュ叿鎵ц娑堟伅鍒拌亰澶╂
    handleToolExecutionMessage(data)

    // 澶勭悊鏍囬鐢熸垚浜嬩欢
    if (data.eventType === 'TITLE_GENERATED' && data.metadata?.title) {
      const session = sessions.value.find(s => s.id === currentSessionId.value)
      if (session) {
        session.title = data.metadata.title as string
      }
    }

    // 澶勭悊涓柇浜嬩欢锛圚ILP - 浜哄伐鍦ㄧ幆锛?
    if (data.eventType === 'INTERRUPTION') {
      console.log('[INTERRUPTION] 鏀跺埌涓柇浜嬩欢:', data)
      // 鍋滄娴佸鐞?
      abortController.abort()
      // 灏嗘秷鎭姸鎬佽缃负绛夊緟瀹℃壒
      const msgIndex = findActiveAssistantMessageIndex()
      if (msgIndex !== undefined && msgIndex >= 0 && currentSession.value) {
        const msg = currentSession.value.messages[msgIndex]
        if (msg) {
          msg.status = 'interrupted'
          // 淇濆瓨瀹屾暣鐨勫鎵规暟鎹?
          const toolFeedbacks = data.stateData?.interruption?.metadata?.customData?.tool_feedbacks
          if (toolFeedbacks && toolFeedbacks.length > 0) {
            ;(msg as any).interruptionData = {
              message: data.stateData?.interruption?.metadata?.message || '需要人工审批',
              tool_feedbacks: toolFeedbacks
            }
          }
          // 淇濆瓨妫€鏌ョ偣 ID 鍜?threadId 浠ヤ究鍚庣画鎭㈠
          if (data.stateData?.interruption?.checkpointId) {
            ;(msg as any).checkpointId = data.stateData.interruption.checkpointId
            // 浼樺厛浣跨敤 threadId锛屽鏋滄病鏈夊垯浣跨敤褰撳墠浼氳瘽 ID
            ;(msg as any).threadId = data.stateData.interruption.threadId || currentSessionId.value
          }
        }
      }
      onComplete()
      currentLlmMessageId.value = null
      return
    }

    // 鐩存帴璋冪敤 updateAIMessage锛屾棤闇€鍐嶆瑙ｆ瀽
    updateAIMessage(data, abortController, onComplete)

    // 鏇存柊浼氳瘽鏃堕棿鎴?
    if (currentSession.value) {
      currentSession.value.updatedAt = new Date().toLocaleString('zh-CN')
    }
  }

  // 澶勭悊宸ュ叿璋冪敤娑堟伅锛團unction Call锛?
  function handleToolCallMessage(data: AgentEvent) {
    // 只在 LLM 节点完成时处理
    if (data.nodeType !== 'llm' || data.eventType !== 'completed') return

    const toolCallsFromExecutionRecord = normalizeToolCalls(data.stateData?.execution_record?.toolCalls)
    const toolCallsFromMetadata = normalizeToolCalls((data.metadata as Record<string, unknown> | undefined)?.tool_calls)
    const toolCalls = toolCallsFromExecutionRecord.length > 0 ? toolCallsFromExecutionRecord : toolCallsFromMetadata

    if (toolCalls.length === 0) {
      console.debug('[handleToolCallMessage] no tool calls in execution_record.toolCalls or metadata.tool_calls', data)
      return
    }
    if (toolCallsFromExecutionRecord.length === 0 && toolCallsFromMetadata.length > 0) {
      console.debug('[handleToolCallMessage] fallback to metadata.tool_calls', {
        count: toolCallsFromMetadata.length,
        nodeId: data.nodeId
      })
    }

    const finalThinking = extractFinalThinking(data.metadata)
    if (toolCalls.some(tc => isTodoToolName(tc.name))) {
      todoStore.markTodoToolSeen('tool_call')
    }

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
      thinkingContent: finalThinking,
      timestamp: new Date().toLocaleString('zh-CN'),
      status: 'completed',
      metadata: {
        tool_calls: toolCalls.map(tc => ({
          id: tc.id,
          name: tc.name,
          type: tc.type || 'function',
          arguments: tc.arguments
        }))
      }
    }

    // Function call 卡片已承载 think，避免 assistant 重复展示
    const activeAssistantIndex = findActiveAssistantMessageIndex()
    if (activeAssistantIndex >= 0 && currentSession.value) {
      const activeAssistant = currentSession.value.messages[activeAssistantIndex]
      if (activeAssistant) {
        const hasVisibleContent = (activeAssistant.content ?? '').trim().length > 0
        // LLM 首轮仅产出 tool call 时，移除空占位 assistant，避免出现悬挂“思考中...”气泡。
        if (!hasVisibleContent) {
          currentSession.value.messages.splice(activeAssistantIndex, 1)
          if (currentLlmMessageId.value === activeAssistant.id) {
            currentLlmMessageId.value = null
          }
        } else {
          activeAssistant.status = 'completed'
          activeAssistant.metadata = {
            ...(activeAssistant.metadata || {}),
            hide_thinking: true
          }
        }
      }
    }

    console.log('[handleToolCallMessage] 添加工具调用消息:', toolCallMessage)
    currentSession.value?.messages.push(toolCallMessage)
  }

  function findActiveAssistantMessageIndex(): number {
    const session = currentSession.value
    if (!session) return -1

    if (currentLlmMessageId.value) {
      const activeIndex = session.messages.findIndex(m =>
        m.id === currentLlmMessageId.value && m.role === 'assistant'
      )
      if (activeIndex >= 0) {
        return activeIndex
      }
    }

    for (let i = session.messages.length - 1; i >= 0; i--) {
      if (session.messages[i]?.role === 'assistant') {
        return i
      }
    }
    return -1
  }

  function ensureActiveAssistantMessage(data: AgentEvent): number {
    const session = currentSession.value
    if (!session) return -1

    if (data.nodeType === 'llm' && (data.eventType === 'running' || data.eventType === 'completed' || data.eventType === 'failed')) {
      const existingIndex = findActiveAssistantMessageIndex()
      if (existingIndex >= 0) {
        return existingIndex
      }

      const fallbackMessage: Message = {
        id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        role: 'assistant',
        content: '',
        timestamp: new Date().toLocaleString('zh-CN'),
        status: 'thinking'
      }
      session.messages.push(fallbackMessage)
      currentLlmMessageId.value = fallbackMessage.id
      return session.messages.length - 1
    }

    if (data.eventType === 'GRAPH_COMPLETED' || data.eventType === 'failed') {
      return findActiveAssistantMessageIndex()
    }

    return -1
  }

  // 澶勭悊宸ュ叿鎵ц娑堟伅锛圱ool Execution锛?
  function handleToolExecutionMessage(data: AgentEvent) {
    // 妫€鏌ユ槸鍚︽槸 tool 鑺傜偣涓斿凡瀹屾垚
    if (data.nodeType !== 'tool' || data.eventType !== 'completed') return

    const executions = data.stateData?.execution_record?.executions
    if (!executions || executions.length === 0) return
    if (executions.some((exec: any) => isTodoToolName(exec?.name))) {
      todoStore.markTodoToolSeen('tool_execution')
    }

    // 妫€鏌ユ槸鍚﹀凡缁忔坊鍔犺繃杩欎釜宸ュ叿鎵ц娑堟伅
    const existingMsg = currentSession.value?.messages.find(m =>
      m.role === 'tool_response' &&
      m.metadata?.tool_responses?.[0]?.id === executions[0]?.id
    )
    if (existingMsg) return

    // 鍒涘缓 tool_response 娑堟伅
    const toolResponseMessage: Message = {
      id: `tool-response-${Date.now()}-${executions[0]?.id}`,
      role: 'tool_response',
      content: '',
      timestamp: new Date().toLocaleString('zh-CN'),
      status: 'completed',
      metadata: {
        tool_responses: executions.map((exec: any) => {
          // 澶勭悊 result 瀛楁 - 濡傛灉鏄璞″垯杞崲涓?JSON 瀛楃涓?
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

    console.log('[handleToolExecutionMessage] 娣诲姞宸ュ叿鎵ц娑堟伅:', toolResponseMessage)
    currentSession.value?.messages.push(toolResponseMessage)
  }

  // 鏇存柊 AI 娑堟伅鐘舵€?
  function updateAIMessage(
    data: AgentEvent,
    abortController: AbortController,
    onComplete: () => void
  ) {
    // 仅触发 function call 的 LLM 完成事件不更新 assistant 气泡，避免与 Function Call 卡片重复展示 THINK。
    if (data.nodeType === 'llm' && data.eventType === 'completed' && hasToolCallPayload(data)) {
      return
    }

    const msgIndex = ensureActiveAssistantMessage(data)

    if (msgIndex === undefined || msgIndex < 0 || !currentSession.value) {
      if (data.eventType === 'GRAPH_COMPLETED') {
        onComplete()
        currentLlmMessageId.value = null
        abortController.abort()
        return
      }
      console.warn('[updateAIMessage] 未找到可更新的 assistant 回合消息')
      return
    }

    const messages = currentSession.value.messages

    // 楠岃瘉娑堟伅瀛樺湪
    const initialMsg = messages[msgIndex]
    if (!initialMsg) {
      console.warn('[updateAIMessage] 娑堟伅瀵硅薄涓虹┖')
      return
    }

    console.log(`[updateAIMessage] 澶勭悊浜嬩欢: eventType=${data.eventType}`)

    switch (data.eventType) {
      case 'running':
        // 增量追加：流式输出
        const currentMsg = messages[msgIndex]
        console.log('[updateAIMessage] running - 褰撳墠娑堟伅:', currentMsg)
        console.log('[updateAIMessage] running - 鏀跺埌鍐呭:', data.message)
        if (currentMsg) {
          const thinkDelta = extractThinkingDelta(data.metadata)
          const updatedThinking = thinkDelta
            ? `${currentMsg.thinkingContent ?? ''}${thinkDelta}`
            : currentMsg.thinkingContent

          messages[msgIndex] = {
            ...currentMsg,
            id: currentMsg.id,
            role: currentMsg.role,
            content: currentMsg.content + (data.message ?? ''),
            thinkingContent: updatedThinking,
            timestamp: currentMsg.timestamp,
            status: 'thinking'
          }
          console.log('[updateAIMessage] running - 鏇存柊鍚?', messages[msgIndex])
        }
        break

      case 'completed':
        // 全量覆盖：仅 _AGENT_MODEL_ 节点
        const completedMsg = messages[msgIndex]
        if (data.nodeId === '_AGENT_MODEL_' && completedMsg) {
          console.log('[updateAIMessage] completed - _AGENT_MODEL_')
          console.log('[updateAIMessage] metadata:', data.metadata)
          console.log('[updateAIMessage] metadata.citations:', data.metadata?.citations)

          // 鎻愬彇 citations
          const citations = data.metadata?.citations as CitationReference[] | undefined
          const finalThinking = extractFinalThinking(data.metadata) ?? completedMsg.thinkingContent

          messages[msgIndex] = {
            ...completedMsg,
            id: completedMsg.id,
            role: completedMsg.role,
            content: data.message ?? completedMsg.content,
            thinkingContent: finalThinking,
            timestamp: completedMsg.timestamp,
            status: 'thinking',
            citations: citations?.length ? citations : undefined
          }

          console.log('[updateAIMessage] 宸茶缃?citations:', citations?.length || 0)
        }
        break

      case 'GRAPH_COMPLETED':
        // 閲嶆柊鑾峰彇鏈€鏂版秷鎭紩鐢?
        const graphCompletedMsg = messages[msgIndex]
        if (graphCompletedMsg) {
          console.log('[updateAIMessage] GRAPH_COMPLETED - 瀹屾垚')
          console.log('[updateAIMessage] citations 宸插瓨鍦?', graphCompletedMsg.citations?.length || 0)
          messages[msgIndex] = {
            ...graphCompletedMsg,
            id: graphCompletedMsg.id,
            role: graphCompletedMsg.role,
            content: graphCompletedMsg.content,
            timestamp: graphCompletedMsg.timestamp,
            status: 'completed'
            // citations 宸茬粡鍦?completed 浜嬩欢涓缃簡
          }
        }
        onComplete()
        currentLlmMessageId.value = null
        abortController.abort()  // 鐪熸鍋滄娴?
        break

      case 'failed':
        // 閲嶆柊鑾峰彇鏈€鏂版秷鎭紩鐢?
        const failedMsg = messages[msgIndex]
        if (failedMsg) {
          console.log('[updateAIMessage] failed - 澶辫触:', data.nodeErrorMessage)
          messages[msgIndex] = {
            ...failedMsg,
            id: failedMsg.id,
            role: failedMsg.role,
            content: failedMsg.content + `\n(閿欒: ${data.nodeErrorMessage || '鎵ц澶辫触'})`,
            timestamp: failedMsg.timestamp,
            status: 'error'
          }
        }
        onComplete()
        currentLlmMessageId.value = null
        abortController.abort()
        break
    }
  }

  // 澶勭悊 SSE 閿欒
  function handleSSEError() {
    console.error('SSE connection error')

    // 鏇存柊娑堟伅涓洪敊璇姸鎬?
    const msgIndex = findActiveAssistantMessageIndex()
    if (msgIndex !== undefined && msgIndex >= 0 && currentSession.value) {
      const msg = currentSession.value.messages[msgIndex]
      if (msg) {
        msg.content = '杩炴帴閿欒锛岃绋嶅悗閲嶈瘯'
        msg.status = 'error'
      }
    }

    currentLlmMessageId.value = null
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

    // 鎵惧埌瀵瑰簲鐨?AI 娑堟伅
    const msgIndex = currentSession.value?.messages.findIndex(m => m.id === messageId)
    if (msgIndex === undefined || msgIndex < 0 || !currentSession.value) {
      console.error('[resumeAgent] 娑堟伅鏈壘鍒?', messageId)
      return
    }

    const aiMessage = currentSession.value.messages[msgIndex]!

    // 鏇存柊娑堟伅鐘舵€佷负鎬濊€冧腑
    aiMessage.status = 'thinking'
    aiMessage.content += '\n\n--- Approval granted, continue execution ---\n\n'
    currentLlmMessageId.value = null

    // 鍒涘缓 AbortController
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
        throw new Error('Failed to read streaming response')
      }

      while (true) {
        const { done, value } = await reader.read()
        console.log('[resumeAgent] done:', done, 'value length:', value?.length)

        if (value) {
          const decoded = decoder.decode(value, { stream: true })
          buffer += decoded
        }

        if (done) {
          console.log('[resumeAgent] SSE stream completed')
          if (buffer.trim()) {
            processSSEBuffer(buffer, agentTimelineStore, controller)
            buffer = ''
          }
          break
        }

        // 澶勭悊绮樺寘
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

              handleSSEMessage(data, agentTimelineStore, controller, () => {
                isProcessing.value = false
              })
            } catch (error) {
              console.error('[resumeAgent] 瑙ｆ瀽 SSE 鏁版嵁澶辫触:', error, jsonStr)
            }
          }
        }
      }
    } catch (error: any) {
      if (error.name === 'AbortError') {
        console.log('[resumeAgent] Fetch aborted')
      } else {
        console.error('[resumeAgent] SSE 杩炴帴閿欒:', error)
        handleSSEError()
      }
    } finally {
      isProcessing.value = false
    }
  }

  return {
    // 鐘舵€?
    sessions,
    currentSessionId,
    currentStrategy,
    isProcessing,
    isLoading,
    currentKnowledgeBaseId,
    // 璁＄畻灞炴€?
    currentSession,
    messages,
    // 鎿嶄綔
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
