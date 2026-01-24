<template>
  <div class="session-item-content">
    <div class="session-title">{{ session.title }}</div>
    <div class="session-time">{{ formatTime(session.updatedAt) }}</div>
  </div>
</template>

<script setup lang="ts">
import type { ChatSession } from '@/types/message'

defineProps<{
  session: ChatSession
}>()

function formatTime(timeStr: string): string {
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) {
    const hours = Math.floor(diff / (1000 * 60 * 60))
    if (hours === 0) {
      const minutes = Math.floor(diff / (1000 * 60))
      return minutes === 0 ? '刚刚' : `${minutes} 分钟前`
    }
    return `${hours} 小时前`
  } else if (days === 1) {
    return '昨天'
  } else if (days < 7) {
    return `${days} 天前`
  } else {
    return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  }
}
</script>

<style scoped>
.session-item-content {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-title {
  font-size: 14px;
  color: var(--color-sidebar-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  font-size: 12px;
  color: var(--color-sidebar-text-muted);
}
</style>
