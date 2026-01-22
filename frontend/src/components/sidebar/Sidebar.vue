<template>
  <div class="sidebar-container">
    <!-- Logo/标题 -->
    <div class="sidebar-header">
      <h1 class="app-title">
        <MessageSquare class="title-icon" :size="24" />
        Hello-Agent
      </h1>
    </div>

    <!-- 新建会话按钮 -->
    <div class="sidebar-actions">
      <BaseButton
        variant="primary"
        class="new-chat-btn"
        @click="handleNewChat"
      >
        <Plus :size="16" />
        新建对话
      </BaseButton>
    </div>

    <!-- 会话列表 -->
    <div class="sidebar-sessions">
      <SessionList />
    </div>
  </div>
</template>

<script setup lang="ts">
import { MessageSquare, Plus } from 'lucide-vue-next'
import { useChatStore } from '@/stores/chat'
import BaseButton from '@/components/base/BaseButton.vue'
import SessionList from './SessionList.vue'

const chatStore = useChatStore()

function handleNewChat() {
  chatStore.createNewSession()
}
</script>

<style scoped>
.sidebar-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: var(--spacing-lg);
  background-color: var(--color-sidebar-bg);
}

.sidebar-header {
  margin-bottom: var(--spacing-lg);
}

.app-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: 18px;
  font-weight: 600;
  color: var(--color-sidebar-text);
  margin: 0;
}

.title-icon {
  color: var(--color-primary);
}

.sidebar-actions {
  margin-bottom: var(--spacing-md);
}

.new-chat-btn {
  width: 100%;
  justify-content: flex-start;
}

.sidebar-sessions {
  flex: 1;
  overflow: hidden;
}
</style>
