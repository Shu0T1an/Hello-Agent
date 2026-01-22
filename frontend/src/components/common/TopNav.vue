<template>
  <div class="top-nav">
    <div class="nav-left">
      <ArrowLeft class="back-btn" :size="20" />
      <span class="session-id">{{ currentId }}</span>
    </div>

    <div class="nav-right">
      <BaseDropdown
        :items="modeDropdownItems"
        :model-value="currentMode"
        placeholder="选择模式"
        size="sm"
        @update:model-value="handleModeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowLeft } from 'lucide-vue-next'
import type { Strategy } from '@/types/message'
import { useChatStore } from '@/stores/chat'
import BaseDropdown from '@/components/base/BaseDropdown.vue'

const chatStore = useChatStore()

const currentId = computed(() => {
  return chatStore.currentSessionId || 'No active session'
})

const currentMode = computed(() => {
  const modeMap: Record<Strategy, string> = {
    'deep-research': 'Deep Research',
    'quick': 'Quick',
    'detailed': 'Detailed'
  }
  return modeMap[chatStore.currentStrategy]
})

const modeDropdownItems = computed(() => {
  const modes: { label: string; value: Strategy }[] = [
    { label: 'Deep Research', value: 'deep-research' },
    { label: 'Quick', value: 'quick' },
    { label: 'Detailed', value: 'detailed' }
  ]
  return modes.map(mode => ({
    label: mode.label,
    value: mode.value,
  }))
})

function handleModeChange(mode: string) {
  chatStore.setStrategy(mode as Strategy)
}
</script>

<style scoped>
.top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid var(--color-content-border);
  background-color: white;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.back-btn {
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: color 150ms ease;
}

.back-btn:hover {
  color: var(--color-text-primary);
}

.session-id {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.nav-right {
  display: flex;
  align-items: center;
}
</style>
