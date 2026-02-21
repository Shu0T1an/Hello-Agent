<template>
  <div class="sidebar-container" :class="{ collapsed }">
    <!-- 折叠按钮 -->
    <button
      v-if="!collapsed"
      @click="handleToggle"
      class="collapse-btn"
      title="折叠侧边栏"
    >
      <ChevronsLeft :size="16" />
    </button>
    <button
      v-else
      @click="handleToggle"
      class="collapse-btn collapsed"
      title="展开侧边栏"
    >
      <ChevronsRight :size="16" />
    </button>

    <!-- Logo/标题 -->
    <div class="sidebar-header" v-if="!collapsed">
      <h1 class="app-title">
        <MessageSquare class="title-icon" :size="24" />
        Hello-Agent
      </h1>
    </div>

    <!-- 新建会话按钮 -->
    <div class="sidebar-actions" v-if="!collapsed">
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
      <SessionList :collapsed="collapsed" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { MessageSquare, Plus, ChevronsLeft, ChevronsRight } from 'lucide-vue-next'
import { useChatStore } from '@/stores/chat'
import BaseButton from '@/components/base/BaseButton.vue'
import SessionList from './SessionList.vue'

interface Props {
  collapsed?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  collapsed: false
})

const emit = defineEmits<{
  toggle: []
}>()

const chatStore = useChatStore()

function handleNewChat() {
  chatStore.createNewSession()
}

function handleToggle() {
  emit('toggle')
}
</script>

<style scoped>
.sidebar-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: var(--spacing-lg);
  position: relative;
}

.sidebar-container.collapsed {
  padding: 12px 8px;
}

[data-theme="dark"] .sidebar-container {
  background: transparent;
}

[data-theme="light"] .sidebar-container {
  background: transparent;
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

.sidebar-container.collapsed .sidebar-sessions {
  margin-top: 40px;
}

.collapse-btn {
  position: absolute;
  top: var(--spacing-md);
  right: var(--spacing-md);
  padding: 6px;
  border-radius: 8px;
  border: 1px solid var(--glass-border);
  background: var(--glass-bg);
  color: var(--color-sidebar-text-muted);
  cursor: pointer;
  transition: all 0.2s ease;
  z-index: 10;
}

.collapse-btn:hover {
  background: var(--glass-bg-hover);
  color: var(--color-sidebar-text);
}

.collapse-btn.collapsed {
  right: auto;
  left: 50%;
  transform: translateX(-50%);
}
</style>
