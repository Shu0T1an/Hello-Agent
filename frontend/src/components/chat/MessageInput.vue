<template>
  <div class="message-input-container">
    <div class="input-wrapper">
      <!-- Agent 选择器 -->
      <BaseDropdown
        :items="agentDropdownItems"
        :model-value="currentAgent"
        placeholder="选择 Agent"
        size="md"
        @update:model-value="handleAgentChange"
      >
        <template #default="{ item }">
          <div class="agent-item">
            <div class="agent-name">{{ item.label }}</div>
          </div>
        </template>
      </BaseDropdown>

      <!-- 输入框 -->
      <textarea
        ref="textareaRef"
        v-model="inputContent"
        class="message-textarea"
        placeholder="输入消息..."
        rows="1"
        @keydown.enter.exact="handleSend"
        @keydown.enter.shift.prevent
        @input="autoResize"
      />
    </div>

    <div class="input-footer">
      <span class="input-hint">Enter 发送，Shift + Enter 换行</span>
      <BaseButton
        variant="primary"
        :disabled="!canSend"
        @click="handleSend"
      >
        发送
      </BaseButton>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Settings } from 'lucide-vue-next'
import { useChatStore } from '@/stores/chat'
import { useAgentStore } from '@/stores/agent'
import BaseButton from '@/components/base/BaseButton.vue'
import BaseDropdown from '@/components/base/BaseDropdown.vue'

const chatStore = useChatStore()
const agentStore = useAgentStore()
const inputContent = ref('')
const textareaRef = ref<HTMLTextAreaElement>()

// Agent 列表
const agents = computed(() => agentStore.agents)

// 当前选中的 Agent
const currentAgent = computed(() => agentStore.currentAgent)

// Agent 下拉选项
const agentDropdownItems = computed(() => {
  return agents.value.map(agent => ({
    label: agent,
    value: agent,
    icon: Settings
  }))
})

// 是否可以发送消息
const canSend = computed(() => {
  return inputContent.value.trim().length > 0 && !chatStore.isProcessing
})

// 处理 Agent 切换
function handleAgentChange(agentName: string): void {
  agentStore.setCurrentAgent(agentName)
}

// 发送消息
function handleSend(): void {
  if (!canSend.value) return

  chatStore.sendMessage(inputContent.value)
  inputContent.value = ''

  // 重置 textarea 高度
  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
  }
}

// 自动调整 textarea 高度
function autoResize(event: Event): void {
  const target = event.target as HTMLTextAreaElement
  target.style.height = 'auto'
  target.style.height = `${Math.min(target.scrollHeight, 200)}px`
}
</script>

<style scoped>
.message-input-container {
  padding: 16px;
  border-top: 1px solid var(--color-content-border);
  background-color: white;
}

.input-wrapper {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin-bottom: 8px;
}

.agent-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-name {
  font-size: 14px;
  color: var(--color-text-primary);
}

.message-textarea {
  flex: 1;
  padding: 12px;
  background-color: #f8fafc;
  border: 1px solid var(--color-content-border);
  border-radius: 12px;
  color: var(--color-text-primary);
  font-size: 14px;
  font-family: inherit;
  line-height: 1.5;
  resize: none;
  min-height: 44px;
  max-height: 200px;
  overflow-y: auto;
  transition: border-color 150ms ease, box-shadow 150ms ease;
}

.message-textarea:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.message-textarea::placeholder {
  color: #94a3b8;
}

.input-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.input-hint {
  font-size: 12px;
  color: #94a3b8;
}
</style>
