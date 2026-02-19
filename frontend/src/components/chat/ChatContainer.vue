<template>
  <div class="flex flex-col h-full">
    <!-- 标签和工具栏 -->
    <div class="flex items-center justify-between px-4 py-3 glass-panel border-b border-b-glass-border flex-shrink-0">
      <div class="flex gap-6 items-center">
        <button
          :class="[
            'text-sm font-medium pb-1 border-b-2 transition-all',
            activeTab === 'conversation'
              ? 'text-indigo-600 border-indigo-600'
              : 'text-zinc-500 border-transparent hover:text-zinc-700'
          ]"
          @click="activeTab = 'conversation'"
        >
          Conversation
        </button>
        <button
          :class="[
            'text-sm font-medium pb-1 border-b-2 transition-all',
            activeTab === 'summary'
              ? 'text-indigo-600 border-indigo-600'
              : 'text-zinc-500 border-transparent hover:text-zinc-700'
          ]"
          @click="activeTab = 'summary'"
        >
          Summary
        </button>
      </div>
      <div class="flex items-center gap-2">
        <BaseButton
          variant="glass"
          size="sm"
          @click="toggleTimeline"
          :class="{ 'bg-indigo-100 text-indigo-700': timelineVisible }"
        >
          <Activity :size="16" />
          <span class="hidden sm:inline ml-1">时间线</span>
        </BaseButton>
      </div>
    </div>

    <!-- 主内容区域：消息列表 + 时间线 -->
    <div class="flex-1 flex flex-col overflow-hidden">
      <!-- 内容区域：消息和时间线 -->
      <div class="flex-1 flex overflow-hidden min-h-0">
        <!-- Conversation 标签页内容 -->
        <div
          v-if="activeTab === 'conversation'"
          ref="messagesRef"
          :class="[
            'flex-1 overflow-y-auto custom-scrollbar-glass',
            'transition-all duration-300',
            timelineVisible ? 'flex-1' : 'w-full'
          ]"
        >
          <!-- 消息列表居中容器 -->
          <div class="flex justify-center">
            <div class="w-full max-w-[850px] p-4 sm:p-6">
              <!-- 欢迎界面 -->
              <WelcomeScreen
                v-if="chatStore.messages.length === 0"
                @select-prompt="handleSelectPrompt"
              />
              <div class="space-y-4">
                <ChatMessage
                  v-for="message in chatStore.messages"
                  :key="message.id"
                  :message="message"
                  :agent-name="agentStore.currentAgent"
                  :session-id="chatStore.currentSessionId"
                />
                <div v-if="chatStore.isProcessing" class="flex items-center gap-2 p-4 glass-card rounded-xl max-w-md">
                  <span class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 0ms" />
                  <span class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 150ms" />
                  <span class="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" style="animation-delay: 300ms" />
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Summary 标签页内容 -->
        <div
          v-else-if="activeTab === 'summary'"
          class="flex-1 overflow-y-auto custom-scrollbar-glass"
        >
          <SessionSummary
            :session-id="chatStore.currentSessionId"
            :loading="summaryLoading"
            :summary="summaryData"
            :error="summaryError"
            @retry="handleRetrySummary"
          />
        </div>

        <!-- 拖拽手柄 -->
        <div
          v-if="timelineVisible"
          class="w-1 bg-glass-200 hover:bg-indigo-400 cursor-col-resize flex-shrink-0 transition-colors relative z-10"
          @mousedown="startResize"
        >
          <div class="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-1 h-8 bg-zinc-300 rounded-full" />
        </div>

        <!-- 时间线面板 -->
        <Transition
          enter-active-class="transition-all duration-300"
          enter-from-class="w-0 opacity-0"
          enter-to-class="opacity-100"
          leave-active-class="transition-all duration-300"
          leave-from-class="opacity-100"
          leave-to-class="w-0 opacity-0"
        >
          <div
            v-if="timelineVisible"
            :class="[
              'glass-panel border-l border-l-glass-border overflow-y-auto custom-scrollbar-glass flex-shrink-0',
              'hidden md:block'
            ]"
            :style="{ width: timelineWidth + 'px' }"
          >
            <AgentTimeline :events="agentTimeline.events" :loading="chatStore.isProcessing" />
          </div>
        </Transition>
      </div>

      <!-- 移动端时间线抽屉 -->
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
            class="fixed inset-x-0 bottom-0 h-[70vh] glass-panel border-t border-t-glass-border rounded-t-2xl shadow-xl z-50 md:hidden"
          >
            <div class="flex items-center justify-between p-4 border-b border-b-glass-border">
              <h3 class="text-lg font-semibold">时间线</h3>
              <BaseButton variant="glass" size="sm" @click="timelineVisible = false">
                <X :size="20" />
              </BaseButton>
            </div>
            <div class="overflow-y-auto h-[calc(70vh-60px)] custom-scrollbar-glass">
              <AgentTimeline :events="agentTimeline.events" :loading="chatStore.isProcessing" />
            </div>
          </div>
        </Transition>
      </Teleport>

      <!-- 遮罩（移动端时间线打开时） -->
      <Teleport to="body">
        <div
          v-if="timelineVisible && isMobile"
          class="fixed inset-0 bg-black/30 backdrop-blur-sm z-40 md:hidden"
          @click="timelineVisible = false"
        />
      </Teleport>
    </div>

    <!-- 输入框 - 移到 overflow-hidden 外面 -->
    <div class="flex justify-center relative z-20">
      <div class="w-full max-w-[850px]">
        <MessageInput
          ref="messageInputRef"
          :knowledge-base-id="knowledgeBaseId"
          :knowledge-bases="knowledgeBases"
          @knowledge-base-change="handleKnowledgeBaseChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch, onUnmounted } from 'vue'
import { Activity, X } from 'lucide-vue-next'
import { useChatStore } from '@/stores/chat'
import { useAgentStore } from '@/stores/agent'
import { useAgentTimelineStore } from '@/stores/agentTimeline'
import { useBreakpoints } from '@/composables/useBreakpoints'
import BaseButton from '@/components/base/BaseButton.vue'
import ChatMessage from './ChatMessage.vue'
import MessageInput from './MessageInput.vue'
import WelcomeScreen from './WelcomeScreen.vue'
import AgentTimeline from '@/components/agent/AgentTimeline.vue'
import SessionSummary from './SessionSummary.vue'
import { fetchSessionSummary } from '@/api/session'
import type { SessionSummary as SessionSummaryType } from '@/types/summary'

// 接收知识库ID和列表
const props = defineProps<{
  knowledgeBaseId?: string
  knowledgeBases?: Array<{ kbId: string; kbName: string }>
}>()

const chatStore = useChatStore()
const agentStore = useAgentStore()
const agentTimeline = useAgentTimelineStore()
const { isMobile } = useBreakpoints()

// 当前激活的标签页
const activeTab = ref<'conversation' | 'summary'>('conversation')

// Summary 相关状态
const summaryData = ref<SessionSummaryType | null>(null)
const summaryLoading = ref(false)
const summaryError = ref<string | null>(null)

// 处理知识库切换
function handleKnowledgeBaseChange(kbId: string) {
  chatStore.setKnowledgeBaseId(kbId)
}

// 处理快捷提示词选择
function handleSelectPrompt(prompt: string) {
  // 设置输入框内容并聚焦
  messageInputRef.value?.setContent(prompt)
}

// 消息容器引用
const messagesRef = ref<HTMLElement>()

// MessageInput 组件引用
const messageInputRef = ref<InstanceType<typeof MessageInput>>()

// 时间线显示状态
const timelineVisible = ref(false)
const timelineWidth = ref(400)
const minWidth = 300
const maxWidth = 600
const isResizing = ref(false)

// 自动滚动到底部
watch(() => chatStore.messages.length, async () => {
  await nextTick()
  smoothScrollToBottom()
})

// 监听会话变化，自动开始收集事件
watch(() => chatStore.currentSessionId, (newSessionId) => {
  if (newSessionId) {
    agentTimeline.startCollecting(newSessionId)
  }
}, { immediate: true })

// 监听 activeTab 变化，切换到 summary 时加载数据
watch(activeTab, async (newTab) => {
  if (newTab === 'summary' && chatStore.currentSessionId) {
    await loadSummaryData()
  }
})

// 监听 sessionId 变化，切换会话时清空 summary 数据
watch(() => chatStore.currentSessionId, () => {
  if (activeTab.value === 'summary') {
    summaryData.value = null
    summaryError.value = null
  }
})

// 加载会话摘要数据
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

// 重试加载摘要数据
function handleRetrySummary() {
  loadSummaryData()
}

// 平滑滚动到底部
function smoothScrollToBottom() {
  if (messagesRef.value) {
    messagesRef.value.scrollTo({
      top: messagesRef.value.scrollHeight,
      behavior: 'smooth'
    })
  }
}

// 切换时间线显示
function toggleTimeline() {
  timelineVisible.value = !timelineVisible.value

  if (timelineVisible.value) {
    agentTimeline.startCollecting(chatStore.currentSessionId)
  } else {
    agentTimeline.stopCollecting()
  }
}

// 开始调整大小
function startResize(e: MouseEvent) {
  isResizing.value = true
  document.addEventListener('mousemove', onResize)
  document.addEventListener('mouseup', stopResize)
  e.preventDefault()
}

// 调整大小中
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

// 停止调整大小
function stopResize() {
  isResizing.value = false
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
}

// 清理事件监听器
onUnmounted(() => {
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
})
</script>

<style scoped>
@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.animate-bounce {
  animation: bounce 1.4s infinite ease-in-out;
}
</style>
