<template>
  <div class="chat-container">
    <!-- 顶部导航 -->
    <TopNav />

    <!-- 标签和工具栏 -->
    <div class="chat-toolbar">
      <div class="tabs">
        <div
          class="tab-item"
          :class="{ active: activeTab === 'conversation' }"
          @click="activeTab = 'conversation'"
        >
          Conversation
        </div>
        <div
          class="tab-item"
          :class="{ active: activeTab === 'summary' }"
          @click="activeTab = 'summary'"
        >
          Summary
        </div>
      </div>
      <div class="toolbar-actions">
        <BaseButton
          variant="ghost"
          size="sm"
          @click="toggleTimeline"
          :class="{ active: timelineVisible }"
        >
          <Activity :size="16" />
        </BaseButton>
      </div>
    </div>

    <!-- 主内容区域：消息列表 + 时间线 -->
    <div class="content-wrapper">
      <!-- 上半部分：消息 + 时间线（左右并列） -->
      <div class="content-area">
        <div class="messages-container" ref="messagesRef">
          <div v-if="chatStore.messages.length === 0" class="empty-state">
            <MessageSquare class="empty-icon" :size="48" />
            <p class="empty-text">开始一个新对话</p>
          </div>
          <ChatMessage
            v-for="message in chatStore.messages"
            :key="message.id"
            :message="message"
          />
          <div v-if="chatStore.isProcessing" class="typing-indicator">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>

        <!-- 时间线面板（右侧并列） -->
        <div v-if="timelineVisible" class="timeline-panel">
          <AgentTimeline :events="agentTimeline.events" :loading="chatStore.isProcessing" />
        </div>
      </div>

      <!-- 输入框（最下面） -->
      <MessageInput />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { MessageSquare, Activity } from 'lucide-vue-next'
import { useChatStore } from '@/stores/chat'
import { useAgentTimelineStore } from '@/stores/agentTimeline'
import BaseButton from '@/components/base/BaseButton.vue'
import TopNav from '@/components/common/TopNav.vue'
import ChatMessage from './ChatMessage.vue'
import MessageInput from './MessageInput.vue'
import AgentTimeline from '@/components/agent/AgentTimeline.vue'

const chatStore = useChatStore()
const agentTimeline = useAgentTimelineStore()

// 当前激活的标签页
const activeTab = ref<'conversation' | 'summary'>('conversation')

// 消息容器引用
const messagesRef = ref<HTMLElement>()

// 时间线显示状态
const timelineVisible = ref(false)

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
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: var(--color-content-bg);
}

.chat-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-content-border);
  background-color: white;
  flex-shrink: 0;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.toolbar-actions :deep(.base-button.active) {
  background-color: var(--color-primary-bg);
  color: var(--color-primary);
}

.tabs {
  display: flex;
  gap: 16px;
}

.tab-item {
  padding: 8px 0;
  font-size: 14px;
  color: var(--color-text-secondary);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 150ms ease;
}

.tab-item:hover {
  color: var(--color-text-primary);
}

.tab-item.active {
  color: var(--color-text-primary);
  border-bottom-color: var(--color-primary);
}

/* 主内容区域：垂直布局 */
.content-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 上半部分：消息和时间线左右并列 */
.content-area {
  flex: 1;
  display: flex;
  overflow: hidden;
  min-height: 0;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.messages-container::-webkit-scrollbar {
  width: 6px;
}

.messages-container::-webkit-scrollbar-track {
  background: transparent;
}

.messages-container::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

.messages-container::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-secondary);
}

.empty-icon {
  margin-bottom: 12px;
  opacity: 0.3;
  color: var(--color-primary);
}

.empty-text {
  font-size: 16px;
  margin: 0;
}

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 12px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--color-text-secondary);
  animation: typing 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(1) { animation-delay: 0s; }
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.7;
  }
  30% {
    transform: translateY(-10px);
    opacity: 1;
  }
}

/* 右侧时间线面板 */
.timeline-panel {
  width: 400px;
  min-width: 400px;
  background-color: white;
  border-left: 1px solid var(--color-content-border);
  overflow-y: auto;
}
</style>
