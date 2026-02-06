<template>
  <div class="session-list-container">
    <!-- 删除所有按钮 -->
    <div v-if="!collapsed && chatStore.sessions.length > 0" class="session-actions">
      <button class="delete-all-btn" @click="showDeleteAllConfirm = true">
        <Trash2 :size="14" />
        <span>删除所有会话</span>
      </button>
    </div>

    <!-- 会话列表 -->
    <div class="session-list">
      <div
        v-for="session in chatStore.sessions"
        :key="session.id"
        class="session-item"
        :class="{ active: session.id === chatStore.currentSessionId }"
        @click="handleSessionClick(session.id)"
      >
        <SessionItem :session="session" :collapsed="collapsed" />
      </div>
    </div>

    <!-- 删除所有确认对话框 -->
    <ConfirmDialog
      v-model:visible="showDeleteAllConfirm"
      type="danger"
      title="删除所有会话"
      message="确定要删除所有会话吗？"
      hint="此操作不可恢复，删除后将自动创建新的默认会话"
      confirm-text="删除全部"
      @confirm="handleDeleteAll"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Trash2 } from 'lucide-vue-next'
import { useChatStore } from '@/stores/chat'
import SessionItem from './SessionItem.vue'
import ConfirmDialog from '@/components/base/ConfirmDialog.vue'

interface Props {
  collapsed?: boolean
}

const props = defineProps<Props>()
const chatStore = useChatStore()

const showDeleteAllConfirm = ref(false)

function handleSessionClick(sessionId: string) {
  chatStore.switchSession(sessionId)
}

async function handleDeleteAll() {
  try {
    await chatStore.deleteAllSessions()
  } catch (error) {
    console.error('删除所有会话失败:', error)
  }
}
</script>

<style scoped>
.session-list-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.session-actions {
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-content-border);
}

.delete-all-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 8px 12px;
  border: none;
  background: var(--color-error-light);
  color: var(--color-error);
  font-size: 13px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 150ms ease;
}

.delete-all-btn:hover {
  background: #fecaca;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.session-list::-webkit-scrollbar {
  width: 4px;
}

.session-list::-webkit-scrollbar-track {
  background: transparent;
}

.session-list::-webkit-scrollbar-thumb {
  background: var(--color-sidebar-active);
  border-radius: 2px;
}

.session-item {
  cursor: pointer;
  border-radius: 8px;
  transition: background-color 150ms ease;
}

.session-item:hover {
  background-color: var(--color-sidebar-hover);
}

.session-item.active {
  background-color: var(--color-sidebar-active);
}
</style>
