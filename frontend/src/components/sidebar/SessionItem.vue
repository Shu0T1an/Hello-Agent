<template>
  <div class="session-item-content" :class="{ collapsed }">
    <div class="session-info" :class="{ collapsed }">
      <MessageSquare v-if="collapsed" :size="16" class="session-icon" />
      <template v-else>
        <div class="session-title">{{ session.title }}</div>
        <div class="session-time">{{ formatTime(session.updatedAt) }}</div>
      </template>
    </div>
    <button
      v-if="!collapsed"
      class="delete-btn"
      @click.stop="showDeleteConfirm = true"
      title="删除会话"
    >
      <X :size="14" />
    </button>

    <!-- 删除确认对话框 -->
    <ConfirmDialog
      v-model:visible="showDeleteConfirm"
      type="danger"
      title="删除会话"
      :message="`确定要删除会话「${session.title}」吗？`"
      hint="删除后无法恢复"
      confirm-text="删除"
      @confirm="handleDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { MessageSquare, X } from 'lucide-vue-next'
import ConfirmDialog from '@/components/base/ConfirmDialog.vue'
import type { ChatSession } from '@/types/message'
import { useChatStore } from '@/stores/chat'

interface Props {
  session: ChatSession
  collapsed?: boolean
}

const props = defineProps<Props>()
const chatStore = useChatStore()

const showDeleteConfirm = ref(false)

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

function handleDelete() {
  chatStore.deleteSession(props.session.id)
}
</script>

<style scoped>
.session-item-content {
  padding: 12px;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.session-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.session-info.collapsed {
  align-items: center;
}

.session-icon {
  color: var(--color-sidebar-text-muted);
  flex-shrink: 0;
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

.delete-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--color-sidebar-text-muted);
  cursor: pointer;
  border-radius: 4px;
  transition: all 150ms ease;
  flex-shrink: 0;
}

.delete-btn:hover {
  background: var(--color-error-light);
  color: var(--color-error);
}
</style>
