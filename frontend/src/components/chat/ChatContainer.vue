<template>
  <div class="chat-shell">
    <header class="chat-toolbar">
      <div class="segment-control" role="tablist" aria-label="会话视图">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          :class="['segment-item', { active: activeTab === tab.id }]"
          @click="activeTab = tab.id"
        >
          {{ tab.label }}
        </button>
      </div>

      <div class="toolbar-actions">
        <BaseButton
          v-if="todoStore.hasTodoPanel"
          variant="editorial"
          size="sm"
          class="md:hidden"
          @click="todoVisible = true"
        >
          <ListTodo :size="15" />
          <span>Todo</span>
        </BaseButton>

        <BaseButton
          variant="editorial"
          size="sm"
          :class="{ 'toolbar-active': timelineVisible }"
          @click="toggleTimeline"
        >
          <Activity :size="15" />
          <span class="hidden sm:inline">时间线</span>
        </BaseButton>
      </div>
    </header>

    <div class="chat-stage">
      <div class="chat-main">
        <div
          v-if="activeTab === 'conversation'"
          ref="messagesRef"
          class="conversation-scroll custom-scrollbar"
        >
          <div class="conversation-column">
            <WelcomeScreen
              v-if="chatStore.messages.length === 0"
              @select-prompt="handleSelectPrompt"
            />

            <div class="conversation-stream">
              <ChatMessage
                v-for="message in chatStore.messages"
                :key="message.id"
                :message="message"
                :agent-name="agentStore.currentAgent"
                :session-id="chatStore.currentSessionId"
              />

              <div v-if="chatStore.isProcessing" class="typing-card">
                <span class="typing-dot" style="animation-delay: 0ms" />
                <span class="typing-dot" style="animation-delay: 140ms" />
                <span class="typing-dot" style="animation-delay: 280ms" />
                <span class="typing-label">Agent 正在思考</span>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="activeTab === 'summary'" class="summary-scroll custom-scrollbar">
          <SessionSummary
            :session-id="chatStore.currentSessionId"
            :loading="summaryLoading"
            :summary="summaryData"
            :error="summaryError"
            @retry="handleRetrySummary"
          />
        </div>

        <div v-else-if="activeTab === 'audit'" class="summary-scroll custom-scrollbar">
          <SessionAudit
            :session-id="chatStore.currentSessionId"
            :loading="auditLoading"
            :audits="auditData"
            :error="auditError"
            @retry="handleRetryAudit"
          />
        </div>

        <div v-else class="checkpoint-wrapper">
          <CheckpointViewer :session-id="chatStore.currentSessionId" />
        </div>
      </div>

      <aside v-if="todoStore.hasTodoPanel && activeTab === 'conversation'" class="hidden md:block todo-column">
        <TodoSidebar />
      </aside>

      <div
        v-if="timelineVisible"
        class="resize-handle"
        @mousedown="startResize"
      >
        <div class="resize-grip" />
      </div>

      <Transition
        enter-active-class="transition-all duration-300"
        enter-from-class="w-0 opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition-all duration-300"
        leave-from-class="opacity-100"
        leave-to-class="w-0 opacity-0"
      >
        <aside
          v-if="timelineVisible"
          class="timeline-panel custom-scrollbar hidden md:block"
          :style="{ width: timelineWidth + 'px' }"
        >
          <div class="timeline-head">执行时间线</div>
          <AgentTimeline :events="agentTimeline.events" :loading="chatStore.isProcessing" />
        </aside>
      </Transition>
    </div>

    <Teleport to="body">
      <Transition
        enter-active-class="transition-all duration-300"
        enter-from-class="translate-y-full"
        enter-to-class="translate-y-0"
        leave-active-class="transition-all duration-300"
        leave-from-class="translate-y-0"
        leave-to-class="translate-y-full"
      >
        <div
          v-if="timelineVisible && isMobile"
          class="mobile-panel"
        >
          <div class="mobile-panel-head">
            <h3>执行时间线</h3>
            <BaseButton variant="editorial" size="sm" @click="timelineVisible = false">
              <X :size="18" />
            </BaseButton>
          </div>
          <div class="mobile-panel-body custom-scrollbar">
            <AgentTimeline :events="agentTimeline.events" :loading="chatStore.isProcessing" />
          </div>
        </div>
      </Transition>
    </Teleport>

    <Teleport to="body">
      <Transition
        enter-active-class="transition-all duration-300"
        enter-from-class="translate-x-full"
        enter-to-class="translate-x-0"
        leave-active-class="transition-all duration-300"
        leave-from-class="translate-x-0"
        leave-to-class="translate-x-full"
      >
        <div
          v-if="todoVisible && isMobile && todoStore.hasTodoPanel"
          class="mobile-panel side"
        >
          <div class="mobile-panel-head">
            <h3>Todo</h3>
            <BaseButton variant="editorial" size="sm" @click="todoVisible = false">
              <X :size="18" />
            </BaseButton>
          </div>
          <div class="mobile-panel-body">
            <TodoSidebar />
          </div>
        </div>
      </Transition>
    </Teleport>

    <Teleport to="body">
      <div
        v-if="(timelineVisible || todoVisible) && isMobile"
        class="fixed inset-0 bg-slate-900/35 backdrop-blur-sm z-40 md:hidden"
        @click="closeMobilePanels"
      />
    </Teleport>

    <footer class="input-strip">
      <div class="input-column">
        <MessageInput
          ref="messageInputRef"
          :knowledge-base-id="knowledgeBaseId"
          :knowledge-bases="knowledgeBases"
          @knowledge-base-change="handleKnowledgeBaseChange"
        />
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch, onUnmounted } from 'vue'
import { Activity, ListTodo, X } from 'lucide-vue-next'
import { useChatStore } from '@/stores/chat'
import { useAgentStore } from '@/stores/agent'
import { useAgentTimelineStore } from '@/stores/agentTimeline'
import { useTodoStore } from '@/stores/todo'
import { useBreakpoints } from '@/composables/useBreakpoints'
import BaseButton from '@/components/base/BaseButton.vue'
import ChatMessage from './ChatMessage.vue'
import MessageInput from './MessageInput.vue'
import WelcomeScreen from './WelcomeScreen.vue'
import AgentTimeline from '@/components/agent/AgentTimeline.vue'
import TodoSidebar from '@/components/todo/TodoSidebar.vue'
import SessionSummary from './SessionSummary.vue'
import SessionAudit from './SessionAudit.vue'
import CheckpointViewer from '@/components/checkpoint/CheckpointViewer.vue'
import { fetchSessionSummary, fetchSessionAudits } from '@/api/session'
import type { SessionSummary as SessionSummaryType } from '@/types/summary'
import type { SessionAuditData } from '@/types/session-audit'

const props = defineProps<{
  knowledgeBaseId?: string
  knowledgeBases?: Array<{ kbId: string; kbName: string }>
}>()

const tabs = [
  { id: 'conversation' as const, label: 'Conversation' },
  { id: 'summary' as const, label: 'Summary' },
  { id: 'audit' as const, label: 'Audit' },
  { id: 'checkpoints' as const, label: 'Checkpoints' },
]

const chatStore = useChatStore()
const agentStore = useAgentStore()
const agentTimeline = useAgentTimelineStore()
const todoStore = useTodoStore()
const { isMobile } = useBreakpoints()

const activeTab = ref<'conversation' | 'summary' | 'audit' | 'checkpoints'>('conversation')
const summaryData = ref<SessionSummaryType | null>(null)
const summaryLoading = ref(false)
const summaryError = ref<string | null>(null)
const auditData = ref<SessionAuditData | null>(null)
const auditLoading = ref(false)
const auditError = ref<string | null>(null)

function handleKnowledgeBaseChange(kbId: string) {
  chatStore.setKnowledgeBaseId(kbId)
}

function handleSelectPrompt(prompt: string) {
  messageInputRef.value?.setContent(prompt)
}

const messagesRef = ref<HTMLElement>()
const messageInputRef = ref<InstanceType<typeof MessageInput>>()

const timelineVisible = ref(false)
const todoVisible = ref(false)
const timelineWidth = ref(400)
const minWidth = 300
const maxWidth = 600
const isResizing = ref(false)

watch(() => chatStore.messages.length, async () => {
  await nextTick()
  smoothScrollToBottom()
})

watch(() => chatStore.currentSessionId, (newSessionId) => {
  if (newSessionId) {
    agentTimeline.startCollecting(newSessionId)
  }
}, { immediate: true })

watch(
  () => agentStore.currentAgent,
  (agentName) => {
    const isDeepSearch = typeof agentName === 'string' && agentName.includes('deep-search')
    if (isDeepSearch) {
      timelineVisible.value = true
      agentTimeline.startCollecting(chatStore.currentSessionId)
    }
  },
  { immediate: true }
)

watch(activeTab, async (newTab) => {
  if (!chatStore.currentSessionId) return
  if (newTab === 'summary') {
    await loadSummaryData()
  } else if (newTab === 'audit') {
    await loadAuditData()
  }
})

watch(() => chatStore.currentSessionId, async () => {
  if (activeTab.value === 'summary') {
    summaryData.value = null
    summaryError.value = null
    await loadSummaryData()
  } else if (activeTab.value === 'audit') {
    auditData.value = null
    auditError.value = null
    await loadAuditData()
  }
})

watch(
  () => todoStore.hasTodoPanel,
  (visible) => {
    if (!visible) {
      todoVisible.value = false
    }
  }
)

async function loadSummaryData() {
  if (!chatStore.currentSessionId) return

  summaryLoading.value = true
  summaryError.value = null

  try {
    summaryData.value = await fetchSessionSummary(chatStore.currentSessionId)
  } catch (err) {
    console.error('加载会话摘要失败:', err)
    summaryError.value = err instanceof Error ? err.message : '加载失败'
  } finally {
    summaryLoading.value = false
  }
}

function handleRetrySummary() {
  loadSummaryData()
}

async function loadAuditData() {
  if (!chatStore.currentSessionId) return

  auditLoading.value = true
  auditError.value = null

  try {
    auditData.value = await fetchSessionAudits(chatStore.currentSessionId)
  } catch (err) {
    console.error('加载会话审计失败:', err)
    auditError.value = err instanceof Error ? err.message : '加载失败'
  } finally {
    auditLoading.value = false
  }
}

function handleRetryAudit() {
  loadAuditData()
}

function smoothScrollToBottom() {
  if (messagesRef.value) {
    messagesRef.value.scrollTo({
      top: messagesRef.value.scrollHeight,
      behavior: 'smooth'
    })
  }
}

function toggleTimeline() {
  timelineVisible.value = !timelineVisible.value

  if (timelineVisible.value) {
    agentTimeline.startCollecting(chatStore.currentSessionId)
  } else {
    agentTimeline.stopCollecting()
  }
}

function closeMobilePanels() {
  timelineVisible.value = false
  todoVisible.value = false
}

function startResize(e: MouseEvent) {
  isResizing.value = true
  document.addEventListener('mousemove', onResize)
  document.addEventListener('mouseup', stopResize)
  e.preventDefault()
}

function onResize(e: MouseEvent) {
  if (!isResizing.value) return

  const container = messagesRef.value?.parentElement
  if (!container) return

  const rect = container.getBoundingClientRect()
  const newWidth = rect.right - e.clientX

  if (newWidth >= minWidth && newWidth <= maxWidth) {
    timelineWidth.value = newWidth
  }
}

function stopResize() {
  isResizing.value = false
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
}

onUnmounted(() => {
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
})
</script>

<style scoped>
.chat-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.chat-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--line-subtle);
  background: color-mix(in srgb, var(--surface-1) 88%, transparent);
}

.segment-control {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.2rem;
  border-radius: 999px;
  border: 1px solid var(--line-subtle);
  background: color-mix(in srgb, var(--surface-2) 70%, transparent);
}

.segment-item {
  border: none;
  border-radius: 999px;
  padding: 0.38rem 0.78rem;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  background: transparent;
  transition: all var(--transition-fast);
}

.segment-item:hover {
  color: var(--color-text-primary);
}

.segment-item.active {
  background: var(--surface-1);
  color: var(--color-primary);
  box-shadow: var(--shadow-xs);
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.toolbar-active {
  border-color: var(--color-primary) !important;
  color: var(--color-primary) !important;
}

.chat-stage {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.chat-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.conversation-scroll,
.summary-scroll {
  height: 100%;
  overflow-y: auto;
}

.conversation-column {
  width: 100%;
  max-width: 920px;
  margin: 0 auto;
  padding: 1rem 1.25rem 1.4rem;
}

.conversation-stream {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

.typing-card {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.66rem 0.85rem;
  border-radius: 999px;
  border: 1px solid var(--line-subtle);
  background: var(--surface-1);
  width: fit-content;
}

.typing-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--color-primary);
  animation: typing-bounce 1.25s infinite ease-in-out;
}

.typing-label {
  margin-left: 0.18rem;
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.checkpoint-wrapper {
  height: 100%;
  overflow: hidden;
}

.todo-column {
  width: 320px;
  flex-shrink: 0;
  border-left: 1px solid var(--line-subtle);
  background: color-mix(in srgb, var(--surface-1) 88%, transparent);
}

.resize-handle {
  width: 6px;
  flex-shrink: 0;
  cursor: col-resize;
  border-left: 1px solid var(--line-subtle);
  border-right: 1px solid var(--line-subtle);
  background: color-mix(in srgb, var(--surface-1) 65%, transparent);
  display: none;
  align-items: center;
  justify-content: center;
}

.resize-grip {
  width: 2px;
  height: 40px;
  border-radius: 999px;
  background: var(--line-strong);
}

.timeline-panel {
  flex-shrink: 0;
  border-left: 1px solid var(--line-subtle);
  background: color-mix(in srgb, var(--surface-1) 92%, transparent);
  overflow-y: auto;
}

.timeline-head {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 0.7rem 1rem;
  font-size: 0.78rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-weight: 700;
  color: var(--color-text-muted);
  border-bottom: 1px solid var(--line-subtle);
  background: color-mix(in srgb, var(--surface-1) 92%, transparent);
}

.input-strip {
  border-top: 1px solid var(--line-subtle);
  padding: 0.8rem 1rem 1rem;
  background: color-mix(in srgb, var(--surface-1) 88%, transparent);
}

.input-column {
  width: 100%;
  max-width: 920px;
  margin: 0 auto;
}

.mobile-panel {
  position: fixed;
  inset-inline: 0;
  bottom: 0;
  height: 72vh;
  border-top-left-radius: 1rem;
  border-top-right-radius: 1rem;
  border: 1px solid var(--line-subtle);
  background: var(--surface-1);
  box-shadow: var(--shadow-xl);
  z-index: 50;
}

.mobile-panel.side {
  inset-inline: auto 0;
  inset-block: 0;
  width: min(88vw, 360px);
  height: 100vh;
  border-top-left-radius: 1rem;
  border-top-right-radius: 0;
  border-bottom-left-radius: 1rem;
}

.mobile-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--line-subtle);
}

.mobile-panel-head h3 {
  margin: 0;
  font-size: 0.95rem;
}

.mobile-panel-body {
  height: calc(100% - 56px);
  overflow-y: auto;
}

@media (min-width: 768px) {
  .resize-handle {
    display: flex;
  }
}

@keyframes typing-bounce {
  0%,
  80%,
  100% {
    transform: scale(0.5);
    opacity: 0.45;
  }

  40% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
