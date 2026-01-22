<template>
  <div class="session-list">
    <div
      v-for="session in chatStore.sessions"
      :key="session.id"
      class="session-item"
      :class="{ active: session.id === chatStore.currentSessionId }"
      @click="handleSessionClick(session.id)"
    >
      <SessionItem :session="session" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useChatStore } from '@/stores/chat'
import SessionItem from './SessionItem.vue'

const chatStore = useChatStore()

function handleSessionClick(sessionId: string) {
  chatStore.switchSession(sessionId)
}
</script>

<style scoped>
.session-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  height: 100%;
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
