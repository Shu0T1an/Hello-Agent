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
          <div
            class="tool-thumbnail"
            :class="{ 'tool-thumbnail--collapsed': isToolCollapsed }"
            @click="toggleCollapse"
          >
            <div class="flex items-center justify-between w-full">
              <div class="flex items-center gap-2">
                <Sparkles :size="16" />
                <span class="text-sm font-medium">Function Call</span>
              </div>
              <component :is="isToolCollapsed ? ChevronDown : ChevronUp" :size="16" class="collapse-icon" />
            </div>
            <div v-if="isToolCollapsed" class="tool-names">
              <template v-for="(toolCall, idx) in message.metadata.tool_calls" :key="toolCall.id || idx">
                <code class="text-xs font-mono px-2 py-1 bg-indigo-100 text-indigo-700 rounded">{{ toolCall.name }}</code>
              </template>
            </div>
          </div>
          <Transition name="collapse">
            <div v-if="!isToolCollapsed" class="tool-details">
              <div v-for="(toolCall, idx) in message.metadata.tool_calls" :key="toolCall.id || idx" class="tool-call-item">
                <div class="flex items-center gap-2 mb-2">
                  <code class="text-xs font-mono px-2 py-1 bg-indigo-100 text-indigo-700 rounded">{{ toolCall.name }}</code>
                </div>
                <div class="text-xs text-zinc-600 mb-1">Arguments:</div>
                <pre class="font-mono text-xs bg-white border border-zinc-200 rounded p-3 overflow-x-auto text-zinc-700">{{ formatToolArguments(toolCall.arguments) }}</pre>
              </div>
            </div>
          </Transition>
        </div>

        <!-- Tool Response 结构化展示 -->
        <div v-else-if="message.role === 'tool_response' && message.metadata?.tool_responses" class="tool-response-content">
          <div
            class="tool-thumbnail tool-thumbnail--response"
            :class="{ 'tool-thumbnail--collapsed': isToolCollapsed }"
            @click="toggleCollapse"
          >
            <div class="flex items-center justify-between w-full">
              <div class="flex items-center gap-2">
                <Terminal :size="16" />
                <span class="text-sm font-medium">Tool Execution</span>
              </div>
              <component :is="isToolCollapsed ? ChevronDown : ChevronUp" :size="16" class="collapse-icon" />
            </div>
            <div v-if="isToolCollapsed" class="tool-names">
              <template v-for="(response, idx) in message.metadata.tool_responses" :key="response.id || idx">
                <code class="text-xs font-mono px-2 py-1 bg-emerald-100 text-emerald-700 rounded">{{ response.name }}</code>
                <span class="text-xs px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700">Success</span>
              </template>
            </div>
          </div>
          <Transition name="collapse">
            <div v-if="!isToolCollapsed" class="tool-details">
              <div v-for="(response, idx) in message.metadata.tool_responses" :key="response.id || idx" class="tool-response-item">
                <div class="flex items-center gap-2 mb-2">
                  <code class="text-xs font-mono px-2 py-1 bg-emerald-100 text-emerald-700 rounded">{{ response.name }}</code>
                  <span class="text-xs px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700">Success</span>
                </div>
                <div class="text-xs text-zinc-600 mb-1">Result:</div>
                <pre class="font-mono text-xs bg-white border border-zinc-200 rounded p-3 overflow-x-auto text-zinc-700">{{ response.response }}</pre>
              </div>
            </div>
          </Transition>
        </div>

        <!-- 普通 Markdown 内容 -->
        <div
          v-else
          class="message-text prose prose-sm max-w-none dark:prose-invert"
          v-html="renderedContent"
          @click="handleMarkdownClick"
        ></div>

        <!-- 用户消息附件列表 -->
        <div v-if="message.attachments?.length && message.role === 'user'" class="message-attachments">
          <div v-for="attach in message.attachments" :key="attach.id" class="attachment-item">
            <FileText :size="16" />
            <span>{{ attach.fileName }}</span>
            <span class="attachment-size">({{ formatFileSize(attach.fileSize) }})</span>
          </div>
        </div>

        <!-- AI 消息引用列表 -->
        <div v-if="message.citations?.length && message.role === 'assistant'" class="message-citations">
          <div class="citations-header">
            <FileText :size="14" />
            <span>引用来源</span>
          </div>
          <div class="citations-list">
            <div
              v-for="(citation, index) in message.citations"
              :key="citation.chunkId"
              class="citation-item"
              @click="toggleCitation(index)"
            >
              <div class="citation-header">
                <span class="citation-file">{{ citation.fileName }}</span>
                <span class="citation-index">段落 {{ citation.chunkIndex + 1 }}</span>
                <ChevronDown
                  :size="14"
                  class="chevron-icon"
                  :class="{ 'rotated': expandedCitations.has(index) }"
                />
              </div>
              <div v-show="expandedCitations.has(index)" class="citation-content">
                {{ citation.content }}
              </div>
            </div>
          </div>
        </div>
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
import { ref, computed, watch, nextTick } from 'vue'
import { Bot, User, CheckCircle, Sparkles, Terminal, ChevronDown, ChevronUp, FileText } from 'lucide-vue-next'
import BaseTag from '@/components/base/BaseTag.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import ApprovalDialog from '@/components/chat/ApprovalDialog.vue'
import type { Message } from '@/types/message'
import { renderMarkdown, testCitationRendering, clearCache } from '@/utils/markdown'
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
const isToolCollapsed = ref(true)
const expandedCitations = ref<Set<number>>(new Set())

// 将测试函数暴露到全局（用于调试）
if (typeof window !== 'undefined') {
  (window as any).testCitationRendering = testCitationRendering;
  (window as any).clearMarkdownCache = clearCache;
  console.log('[ChatMessage] 测试函数已注册到全局:')
  console.log('  - window.testCitationRendering() - 测试引用标记渲染')
  console.log('  - window.clearMarkdownCache() - 清除缓存')
}

function toggleCollapse() {
  isToolCollapsed.value = !isToolCollapsed.value
}

function toggleCitation(index: number) {
  if (expandedCitations.value.has(index)) {
    expandedCitations.value.delete(index)
  } else {
    expandedCitations.value.add(index)
  }
  // 触发响应式更新
  expandedCitations.value = new Set(expandedCitations.value)
}

// 格式化文件大小
function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

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
  console.log('[ChatMessage] updateRenderedContent 开始')
  console.log('[ChatMessage] citations 数据:', props.message.citations)
  console.log('[ChatMessage] content:', props.message.content)

  const result = await renderMarkdown(props.message.content, {
    theme: 'github-dark',
    citations: props.message.citations || []
  })

  console.log('[ChatMessage] 渲染结果是否包含 citation-marker:', result.includes('citation-marker'))
  console.log('[ChatMessage] 渲染结果片段:', result.substring(0, 500))

  renderedContent.value = result
}

// 监听消息内容变化，自动重新渲染
watch(() => props.message.content, updateRenderedContent, { immediate: true })
watch(() => props.message.citations, updateRenderedContent, { immediate: true })

// 处理 Markdown 内容中的点击事件
function handleMarkdownClick(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (target.classList.contains('citation-marker')) {
    const citationIndex = target.getAttribute('data-citation-index')
    if (citationIndex !== null) {
      const index = parseInt(citationIndex, 10)
      // 展开对应的引用
      if (!expandedCitations.value.has(index)) {
        expandedCitations.value.add(index)
        expandedCitations.value = new Set(expandedCitations.value)
      }

      // 滚动到引用位置
      nextTick(() => {
        const citationElement = document.querySelector(`[data-citation-index="${index}"]`) as HTMLElement
        citationElement?.scrollIntoView({ behavior: 'smooth', block: 'center' })

        // 添加高亮动画效果
        if (citationElement) {
          citationElement.classList.add('citation-highlight')
          setTimeout(() => {
            citationElement.classList.remove('citation-highlight')
          }, 2000)
        }
      })
    }
  }
}
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

/* 工具消息折叠样式 */
.tool-thumbnail {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.05) 0%, rgba(139, 92, 246, 0.05) 100%);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}

.tool-thumbnail:hover {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(139, 92, 246, 0.1) 100%);
}

.tool-thumbnail--response {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.05) 0%, rgba(52, 211, 153, 0.05) 100%);
}

.tool-thumbnail--response:hover {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, rgba(52, 211, 153, 0.1) 100%);
}

[data-theme="dark"] .tool-thumbnail {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.15) 0%, rgba(139, 92, 246, 0.15) 100%);
}

[data-theme="dark"] .tool-thumbnail:hover {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.25) 0%, rgba(139, 92, 246, 0.25) 100%);
}

[data-theme="dark"] .tool-thumbnail--response {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.15) 0%, rgba(52, 211, 153, 0.15) 100%);
}

[data-theme="dark"] .tool-thumbnail--response:hover {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.25) 0%, rgba(52, 211, 153, 0.25) 100%);
}

.tool-thumbnail--collapsed {
  padding: 8px 12px;
}

.tool-names {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.collapse-icon {
  flex-shrink: 0;
  transition: transform 0.2s ease;
}

.tool-details {
  overflow: hidden;
}

/* 折叠动画 */
.collapse-enter-active,
.collapse-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
}

.collapse-enter-from,
.collapse-leave-to {
  opacity: 0;
  max-height: 0;
  margin-top: 0;
}

.collapse-enter-to,
.collapse-leave-from {
  opacity: 1;
  max-height: 500px;
  margin-top: 12px;
}

/* 附件列表样式 */
.message-attachments {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

[data-theme="dark"] .message-attachments {
  border-top-color: rgba(255, 255, 255, 0.1);
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(0, 0, 0, 0.03);
  border-radius: 8px;
  font-size: 13px;
  color: var(--color-text-primary);
}

[data-theme="dark"] .attachment-item {
  background: rgba(255, 255, 255, 0.05);
}

.attachment-item svg {
  color: #4f46e5;
  flex-shrink: 0;
}

.attachment-size {
  margin-left: auto;
  font-size: 11px;
  color: var(--color-text-secondary);
}

/* 引用列表样式 */
.message-citations {
  margin-top: 12px;
  padding: 12px;
  background: rgba(79, 70, 229, 0.05);
  border-radius: 8px;
  border-left: 3px solid #4f46e5;
}

[data-theme="dark"] .message-citations {
  background: rgba(79, 70, 229, 0.1);
}

.citations-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}

.citations-header svg {
  color: #4f46e5;
}

.citations-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.citation-item {
  padding: 8px;
  background: white;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

[data-theme="dark"] .citation-item {
  background: #2d2d2d;
}

.citation-item:hover {
  background: rgba(79, 70, 229, 0.05);
}

[data-theme="dark"] .citation-item:hover {
  background: rgba(79, 70, 229, 0.1);
}

.citation-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.citation-file {
  font-size: 13px;
  font-weight: 500;
  color: #4f46e5;
}

.citation-index {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.chevron-icon {
  color: var(--color-text-secondary);
  transition: transform 0.2s ease;
}

.chevron-icon.rotated {
  transform: rotate(180deg);
}

.citation-content {
  margin-top: 8px;
  padding: 8px;
  background: rgba(0, 0, 0, 0.02);
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
}

[data-theme="dark"] .citation-content {
  background: rgba(255, 255, 255, 0.05);
}

/* 引用标记样式 */
.message-text :deep(.citation-marker) {
  display: inline-block;
  padding: 2px 6px;
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%);
  color: white;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  transition: all 0.2s ease;
  margin: 0 2px;
  font-family: 'Courier New', monospace;
}

/* 有完整引用数据的标记 - 可点击 */
.message-text :deep(.citation-marker:not(.citation-simple)) {
  cursor: pointer;
}

.message-text :deep(.citation-marker:not(.citation-simple):hover) {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.3);
}

/* 简单引用标记 - 无点击功能 */
.message-text :deep(.citation-marker.citation-simple) {
  cursor: default;
  opacity: 0.9;
}

.message-text :deep(.citation-marker.citation-simple:hover) {
  opacity: 1;
}

/* 引用高亮动画 */
.message-text :deep(.citation-marker.citation-highlight) {
  animation: citation-pulse 2s ease-in-out;
}

@keyframes citation-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(79, 70, 229, 0.7);
  }
  70% {
    box-shadow: 0 0 0 10px rgba(79, 70, 229, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(79, 70, 229, 0);
  }
}
</style>
