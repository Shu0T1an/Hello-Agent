<template>
  <div
    class="chat-message"
    :class="`message-${message.role}`"
  >
    <!-- AI 消息头像 -->
    <div v-if="message.role === 'assistant'" class="message-avatar">
      <Bot :size="20" />
    </div>

    <div class="message-content-wrapper">
      <!-- 消息内容 -->
      <div class="message-content">
        <div class="message-text" v-html="formatContent(message.content)"></div>
      </div>

      <!-- 消息底部信息 -->
      <div class="message-footer">
        <span class="message-time">{{ formatTime(message.timestamp) }}</span>
        <BaseTag
          v-if="message.status && message.status !== 'idle'"
          :variant="getStatusVariant(message.status)"
          size="sm"
          class="message-status"
        >
          {{ formatStatus(message.status) }}
        </BaseTag>
      </div>
    </div>

    <!-- 用户消息头像 -->
    <div v-if="message.role === 'user'" class="message-avatar">
      <User :size="20" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { Bot, User } from 'lucide-vue-next'
import BaseTag from '@/components/base/BaseTag.vue'
import type { Message } from '@/types/message'

defineProps<{
  message: Message
}>()

function formatContent(content: string): string {
  // 简单的换行和代码块处理
  return content
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
}

function formatTime(timestamp: string): string {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatStatus(status: string): string {
  const statusMap: Record<string, string> = {
    'thinking': '思考中...',
    'taking-action': '执行中',
    'completed': '完成',
    'error': '错误'
  }
  return statusMap[status] || status
}

function getStatusVariant(status: string): 'default' | 'warning' | 'danger' | 'success' | 'info' | 'purple' {
  const variantMap: Record<string, 'default' | 'warning' | 'danger' | 'success' | 'info' | 'purple'> = {
    'thinking': 'warning',
    'taking-action': 'purple',
    'completed': 'success',
    'error': 'danger'
  }
  return variantMap[status] || 'info'
}
</script>

<style scoped>
.chat-message {
  display: flex;
  gap: 12px;
  max-width: 80%;
}

.chat-message.message-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.chat-message.message-user .message-content-wrapper {
  align-items: flex-end;
}

.chat-message.message-user .message-content {
  background-color: var(--color-primary);
  color: white;
}

.chat-message.message-user .message-avatar {
  background-color: var(--color-primary);
}

.chat-message.message-assistant {
  align-self: flex-start;
}

.chat-message.message-assistant .message-content-wrapper {
  align-items: flex-start;
}

.chat-message.message-assistant .message-content {
  background-color: white;
  color: var(--color-text-primary);
  border: 1px solid var(--color-content-border);
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

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: white;
}

.message-content-wrapper {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-content {
  padding: 12px;
  border-radius: 12px;
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

.message-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>
