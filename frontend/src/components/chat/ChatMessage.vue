<template>
  <div
    class="chat-message"
    :class="`message-${message.role}`"
  >
    <!-- AI 消息头像 -->
    <div v-if="message.role === 'assistant'" class="message-avatar">
      <Bot :size="20" />
    </div>

    <!-- Tool Call 消息头像 -->
    <div v-if="message.role === 'tool_call'" class="message-avatar message-avatar-tool-call">
      <Sparkles :size="18" />
    </div>

    <!-- Tool Response 消息头像 -->
    <div v-if="message.role === 'tool_response'" class="message-avatar message-avatar-tool-response">
      <Terminal :size="18" />
    </div>

    <div class="message-content-wrapper">
      <!-- 消息内容 -->
      <div class="message-content">
        <!-- Tool Call 结构化展示 -->
        <div v-if="message.role === 'tool_call' && message.metadata?.tool_calls" class="tool-call-content">
          <div class="flex items-center gap-2 text-indigo-700 mb-3">
            <Sparkles :size="16" />
            <span class="text-sm font-medium">Function Call</span>
          </div>
          <div v-for="(toolCall, idx) in message.metadata.tool_calls" :key="toolCall.id || idx" class="tool-call-item">
            <div class="flex items-center gap-2 mb-2">
              <code class="text-xs font-mono px-2 py-1 bg-indigo-100 text-indigo-700 rounded">{{ toolCall.name }}</code>
            </div>
            <div class="text-xs text-zinc-600 mb-1">Arguments:</div>
            <pre class="font-mono text-xs bg-white border border-zinc-200 rounded p-3 overflow-x-auto text-zinc-700">{{ formatToolArguments(toolCall.arguments) }}</pre>
          </div>
        </div>

        <!-- Tool Response 结构化展示 -->
        <div v-else-if="message.role === 'tool_response' && message.metadata?.tool_responses" class="tool-response-content">
          <div class="flex items-center gap-2 text-emerald-700 mb-3">
            <Terminal :size="16" />
            <span class="text-sm font-medium">Tool Execution</span>
          </div>
          <div v-for="(response, idx) in message.metadata.tool_responses" :key="response.id || idx" class="tool-response-item">
            <div class="flex items-center gap-2 mb-2">
              <code class="text-xs font-mono px-2 py-1 bg-emerald-100 text-emerald-700 rounded">{{ response.name }}</code>
              <span class="text-xs px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700">Success</span>
            </div>
            <div class="text-xs text-zinc-600 mb-1">Result:</div>
            <pre class="font-mono text-xs bg-white border border-zinc-200 rounded p-3 overflow-x-auto text-zinc-700">{{ response.response }}</pre>
          </div>
        </div>

        <!-- 普通 Markdown 内容 -->
        <div v-else class="message-text prose prose-sm max-w-none dark:prose-invert" v-html="renderedContent"></div>
      </div>

      <!-- 消息底部信息 -->
      <div class="message-footer">
        <span class="message-time">{{ formatTime(message.timestamp) }}</span>
        <BaseTag
          v-if="message.status && message.status !== 'idle'"
          :variant="getMessageStatusVariant(message.status)"
          size="sm"
          class="message-status"
        >
          {{ getMessageStatusLabel(message.status) }}
        </BaseTag>

        <!-- 审批按钮（当消息状态为 interrupted 时显示） -->
        <BaseButton
          v-if="message.status === 'interrupted' && hasApprovalData"
          variant="primary"
          size="sm"
          @click="handleOpenApproval"
        >
          <CheckCircle :size="14" />
          查看并审批
        </BaseButton>
      </div>
    </div>

    <!-- 用户消息头像 -->
    <div v-if="message.role === 'user'" class="message-avatar">
      <User :size="20" />
    </div>

    <!-- 审批对话框 -->
    <ApprovalDialog
      :is-open="approvalDialogOpen"
      :agent-name="agentName || 'default'"
      :checkpoint-id="message.checkpointId || ''"
      :session-id="(message as any).threadId || sessionId"
      :message="approvalMessage"
      :tool-calls="toolCalls"
      @close="approvalDialogOpen = false"
      @submit="handleApprovalSubmit"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Bot, User, CheckCircle, Sparkles, Terminal } from 'lucide-vue-next'
import BaseTag from '@/components/base/BaseTag.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import ApprovalDialog from '@/components/chat/ApprovalDialog.vue'
import type { Message } from '@/types/message'
import { renderMarkdown } from '@/utils/markdown'
import { formatTime, getMessageStatusLabel, getMessageStatusVariant } from '@/utils/helpers'
import { useChatStore } from '@/stores/chat'

const props = defineProps<{
  message: Message
  agentName?: string
  sessionId?: string
}>()

const emit = defineEmits<{
  'approval-submit': []
}>()

const renderedContent = ref('')
const approvalDialogOpen = ref(false)

// 格式化工具参数
function formatToolArguments(args: string): string {
  try {
    const parsed = JSON.parse(args)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return args
  }
}

// 检查是否有审批数据
const hasApprovalData = computed(() => {
  const result = !!(props.message.checkpointId) &&
         props.message.interruptionData?.tool_feedbacks &&
         props.message.interruptionData?.tool_feedbacks.length > 0

  // 调试输出
  console.log('[ChatMessage] hasApprovalData:', result, {
    status: props.message.status,
    checkpointId: props.message.checkpointId,
    hasInterruptionData: !!props.message.interruptionData,
    toolFeedbacksCount: props.message.interruptionData?.tool_feedbacks?.length || 0
  })

  return result
})

// 获取审批消息
const approvalMessage = computed(() => {
  return props.message.interruptionData?.message || '需要人工审批'
})

// 获取工具调用列表
const toolCalls = computed(() => {
  const feedbacks = props.message.interruptionData?.tool_feedbacks || []
  return feedbacks.map((f) => ({
    id: f.id,
    name: f.name,
    arguments: f.arguments,
    description: f.description
  }))
})

// 打开审批对话框
function handleOpenApproval() {
  approvalDialogOpen.value = true
}

// 处理审批提交
async function handleApprovalSubmit(feedbacks: Array<{ id: string; name: string; result: string }>) {
  const chatStore = useChatStore()
  approvalDialogOpen.value = false

  // 调用 resumeAgent 函数处理 SSE 流
  await chatStore.resumeAgent(
    props.agentName || 'default',
    props.message.checkpointId || '',
    (props.message as any).threadId || props.sessionId || '',
    feedbacks,
    props.message.id
  )
}

// 异步渲染 markdown
async function updateRenderedContent() {
  renderedContent.value = await renderMarkdown(props.message.content)
}

// 监听消息内容变化，自动重新渲染
watch(() => props.message.content, updateRenderedContent, { immediate: true })
</script>

<style scoped>
.chat-message {
  display: flex;
  gap: 12px;
  max-width: 80%;
  animation: message-in 0.4s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

@keyframes message-in {
  0% {
    opacity: 0;
    transform: scale(0.95) translateY(16px);
  }
  100% {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.chat-message.message-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.chat-message.message-user .message-content-wrapper {
  align-items: flex-end;
}

.chat-message.message-user .message-content {
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 50%, #06b6d4 100%);
  color: white;
  border-radius: 18px 18px 4px 18px;
  box-shadow: 0 4px 16px rgba(79, 70, 229, 0.25);
}

[data-theme="dark"] .chat-message.message-user .message-content {
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 50%, #06b6d4 100%);
  box-shadow: 0 4px 20px rgba(79, 70, 229, 0.4), 0 0 30px rgba(59, 130, 246, 0.2);
}

.chat-message.message-user .message-avatar {
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 50%, #06b6d4 100%);
}

.chat-message.message-assistant {
  align-self: flex-start;
}

.chat-message.message-assistant .message-content-wrapper {
  align-items: flex-start;
}

.chat-message.message-assistant .message-content {
  background: #ffffff;
  color: var(--color-text-primary);
  border-radius: 18px 18px 18px 4px;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px -1px rgba(0, 0, 0, 0.1);
}

[data-theme="dark"] .chat-message.message-assistant .message-content {
  background: #2d2d2d;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.3), 0 1px 2px -1px rgba(0, 0, 0, 0.2);
}

.chat-message.message-assistant .message-avatar {
  background-color: #10b981;
}

.chat-message.message-system {
  align-self: center;
  max-width: 60%;
}

.chat-message.message-system .message-content {
  background-color: transparent;
  color: var(--color-text-secondary);
  text-align: center;
  font-size: 12px;
}

/* Tool Call 消息样式 */
.chat-message.message-tool_call {
  align-self: flex-start;
}

.chat-message.message-tool_call .message-content-wrapper {
  align-items: flex-start;
}

.chat-message.message-tool_call .message-content {
  background: #ffffff;
  color: var(--color-text-primary);
  border-radius: 18px 18px 18px 4px;
  border-left: 3px solid #6366f1;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px -1px rgba(0, 0, 0, 0.1);
}

[data-theme="dark"] .chat-message.message-tool_call .message-content {
  background: #2d2d2d;
  border-left-color: #818cf8;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.3), 0 1px 2px -1px rgba(0, 0, 0, 0.2);
}

.chat-message.message-tool_call .message-avatar-tool-call {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
}

/* Tool Response 消息样式 */
.chat-message.message-tool_response {
  align-self: flex-start;
}

.chat-message.message-tool_response .message-content-wrapper {
  align-items: flex-start;
}

.chat-message.message-tool_response .message-content {
  background: #ffffff;
  color: var(--color-text-primary);
  border-radius: 18px 18px 18px 4px;
  border-left: 3px solid #10b981;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px -1px rgba(0, 0, 0, 0.1);
}

[data-theme="dark"] .chat-message.message-tool_response .message-content {
  background: #2d2d2d;
  border-left-color: #34d399;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.3), 0 1px 2px -1px rgba(0, 0, 0, 0.2);
}

.chat-message.message-tool_response .message-avatar-tool-response {
  background: linear-gradient(135deg, #10b981 0%, #34d399 100%);
}

/* Tool Call 内容样式 */
.tool-call-content,
.tool-response-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tool-call-item,
.tool-response-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

[data-theme="dark"] .tool-call-item code,
[data-theme="dark"] .tool-response-item code {
  background-color: rgba(99, 102, 241, 0.2);
  color: #a5b4fc;
}

[data-theme="dark"] .tool-call-item .text-xs,
[data-theme="dark"] .tool-response-item .text-xs {
  color: #a1a1aa;
}

[data-theme="dark"] .tool-call-item pre,
[data-theme="dark"] .tool-response-item pre {
  background-color: #1a1a1a;
  border-color: #3f3f46;
  color: #d4d4d8;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.message-content-wrapper {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-content {
  padding: 12px 16px;
  word-wrap: break-word;
  overflow-wrap: break-word;
}

.message-text {
  font-size: 14px;
  line-height: 1.6;
}

.message-text :deep(code) {
  background-color: rgba(0, 0, 0, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

[data-theme="dark"] .message-text :deep(code) {
  background-color: rgba(255, 255, 255, 0.15);
}

.message-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-muted);
  padding: 0 4px;
}
</style>
