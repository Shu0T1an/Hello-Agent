<template>
  <div class="flex flex-col h-full bg-slate-50">
    <!-- 顶部导航 -->
    <TopNav />

    <!-- 标签和工具栏 -->
    <div class="flex items-center justify-between px-4 py-3 bg-white border-b border-slate-200 flex-shrink-0">
      <div class="flex gap-6">
        <button
          :class="[
            'text-sm font-medium pb-1 border-b-2 transition-all',
            activeTab === 'conversation'
              ? 'text-indigo-600 border-indigo-600'
              : 'text-slate-500 border-transparent hover:text-slate-700'
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
              : 'text-slate-500 border-transparent hover:text-slate-700'
          ]"
          @click="activeTab = 'summary'"
        >
          Summary
        </button>
      </div>
      <div class="flex items-center gap-2">
        <BaseButton
          variant="ghost"
          size="sm"
          @click="toggleTimeline"
          :class="{ 'bg-indigo-50 text-indigo-600': timelineVisible }"
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
        <!-- 消息列表 -->
        <div
          ref="messagesRef"
          :class="[
            'flex-1 overflow-y-auto custom-scrollbar',
            'transition-all duration-300',
            timelineVisible ? 'flex-1' : 'w-full'
          ]"
        >
          <div class="p-4 sm:p-6">
            <div v-if="chatStore.messages.length === 0" class="flex flex-col items-center justify-center h-full min-h-[300px] text-slate-400">
              <MessageSquare :size="64" class="opacity-30 mb-4" />
              <p class="text-lg">开始一个新对话</p>
            </div>
            <div class="space-y-6">
              <ChatMessage
                v-for="message in chatStore.messages"
                :key="message.id"
                :message="message"
              />
              <div v-if="chatStore.isProcessing" class="flex items-center gap-2 p-4 bg-white rounded-xl shadow-sm max-w-md">
                <span class="w-2 h-2 bg-slate-400 rounded-full animate-bounce" style="animation-delay: 0ms" />
                <span class="w-2 h-2 bg-slate-400 rounded-full animate-bounce" style="animation-delay: 150ms" />
                <span class="w-2 h-2 bg-slate-400 rounded-full animate-bounce" style="animation-delay: 300ms" />
              </div>
            </div>
          </div>
        </div>

        <!-- 拖拽手柄 -->
        <div
          v-if="timelineVisible"
          ref="resizeHandleRef"
          class="w-1 bg-slate-200 hover:bg-indigo-400 cursor-col-resize flex-shrink-0 transition-colors relative z-10"
          @mousedown="startResize"
        >
          <div class="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-1 h-8 bg-slate-300 rounded-full" />
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
              'bg-white border-l border-slate-200 overflow-y-auto custom-scrollbar flex-shrink-0',
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
            class="fixed inset-x-0 bottom-0 h-[70vh] bg-white border-t border-slate-200 rounded-t-2xl shadow-xl z-50 md:hidden"
          >
            <div class="flex items-center justify-between p-4 border-b border-slate-200">
              <h3 class="text-lg font-semibold">时间线</h3>
              <BaseButton variant="ghost" size="sm" @click="timelineVisible = false">
                <X :size="20" />
              </BaseButton>
            </div>
            <div class="overflow-y-auto h-[calc(70vh-60px)] custom-scrollbar">
              <AgentTimeline :events="agentTimeline.events" :loading="chatStore.isProcessing" />
            </div>
          </div>
        </Transition>
      </Teleport>

      <!-- 遮罩（移动端时间线打开时） -->
      <Teleport to="body">
        <div
          v-if="timelineVisible && isMobile"
          class="fixed inset-0 bg-black/50 z-40 md:hidden"
          @click="timelineVisible = false"
        />
      </Teleport>

      <!-- 输入框 -->
      <MessageInput />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch, onMounted, onUnmounted } from 'vue'
import { MessageSquare, Activity, X } from 'lucide-vue-next'
import { useChatStore } from '@/stores/chat'
import { useAgentTimelineStore } from '@/stores/agentTimeline'
import { useBreakpoints } from '@/composables/useBreakpoints'
import BaseButton from '@/components/base/BaseButton.vue'
import TopNav from '@/components/common/TopNav.vue'
import ChatMessage from './ChatMessage.vue'
import MessageInput from './MessageInput.vue'
import AgentTimeline from '@/components/agent/AgentTimeline.vue'

// 接收知识库ID
const props = defineProps<{
  knowledgeBaseId?: string
}>()

const chatStore = useChatStore()
const agentTimeline = useAgentTimelineStore()
const { isMobile } = useBreakpoints()

// 监听knowledgeBaseId变化，更新chatStore
watch(() => props.knowledgeBaseId, (newId) => {
  chatStore.setKnowledgeBaseId(newId || '')
}, { immediate: true })

// 当前激活的标签页
const activeTab = ref<'conversation' | 'summary'>('conversation')

// 消息容器引用
const messagesRef = ref<HTMLElement>()

// 时间线显示状态
const timelineVisible = ref(false)
const timelineWidth = ref(400)
const minWidth = 300
const maxWidth = 600
const isResizing = ref(false)
const resizeHandleRef = ref<HTMLElement>()

// 自动滚动到底部
watch(() => chatStore.messages.length, async () => {
  await nextTick()
  scrollToBottom()
})

// 监听会话变化，自动开始收集事件
watch(() => chatStore.currentSessionId, (newSessionId) => {
  if (newSessionId) {
    agentTimeline.startCollecting(newSessionId)
  }
}, { immediate: true })

// 滚动到底部
function scrollToBottom() {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
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
