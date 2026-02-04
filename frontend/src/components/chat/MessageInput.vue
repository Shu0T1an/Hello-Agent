<template>
  <div class="message-input-container">
    <!-- 第一行：输入框和发送按钮 -->
    <div class="input-main-row">
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
      <BaseButton
        variant="primary"
        :disabled="!canSend"
        :loading="isSending"
        @click="handleSend"
        class="send-button"
      >
        <Send :size="18" />
      </BaseButton>
    </div>

    <!-- 第二行：额外功能 -->
    <div class="input-extras-row">
      <!-- Agent 选择器 -->
      <div class="extra-item">
        <BaseDropdown
          :items="agentDropdownItems"
          :model-value="currentAgent"
          placeholder="选择 Agent"
          size="sm"
          direction="up"
          @update:model-value="handleAgentChange"
        >
          <template #default="{ item }">
            <div class="agent-item">
              <div class="agent-name">{{ item.label }}</div>
            </div>
          </template>
        </BaseDropdown>
      </div>

      <!-- 知识库选择器 -->
      <div class="extra-item" v-if="hasKnowledgeBases">
        <BaseDropdown
          :items="knowledgeBaseDropdownItems"
          :model-value="knowledgeBaseId || ''"
          placeholder="不使用知识库"
          size="sm"
          direction="up"
          @update:model-value="handleKnowledgeBaseChange"
        />
      </div>

      <!-- 提示信息 -->
      <span class="input-hint">Enter 发送，Shift + Enter 换行</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Send } from 'lucide-vue-next'
import { useChatStore } from '@/stores/chat'
import { useAgentStore } from '@/stores/agent'
import BaseButton from '@/components/base/BaseButton.vue'
import BaseDropdown from '@/components/base/BaseDropdown.vue'

// Props
const props = defineProps<{
  knowledgeBaseId?: string
  knowledgeBases?: Array<{ kbId: string; kbName: string }>
}>()

// Emits
const emit = defineEmits<{
  'knowledge-base-change': [kbId: string]
}>()

const chatStore = useChatStore()
const agentStore = useAgentStore()
const inputContent = ref('')
const textareaRef = ref<HTMLTextAreaElement>()
const isSending = ref(false)

// Agent 列表
const agents = computed(() => agentStore.agents)

// 当前选中的 Agent
const currentAgent = computed(() => agentStore.currentAgent)

// Agent 下拉选项
const agentDropdownItems = computed(() => {
  return agents.value.map(agent => ({
    label: agent,
    value: agent
  }))
})

// 知识库下拉选项
const knowledgeBaseDropdownItems = computed(() => {
  const items = [{ label: '不使用知识库', value: '' }]
  if (Array.isArray(props.knowledgeBases)) {
    items.push(...props.knowledgeBases.map(kb => ({
      label: kb.kbName,
      value: kb.kbId
    })))
  }
  return items
})

// 是否有知识库
const hasKnowledgeBases = computed(() => {
  return props.knowledgeBases && props.knowledgeBases.length > 0
})

// 是否可以发送消息
const canSend = computed(() => {
  return inputContent.value.trim().length > 0 && !chatStore.isProcessing
})

// 处理 Agent 切换
function handleAgentChange(agentName: string): void {
  agentStore.setCurrentAgent(agentName)
}

// 处理知识库切换
function handleKnowledgeBaseChange(kbId: string): void {
  emit('knowledge-base-change', kbId)
}

// 发送消息
async function handleSend(): Promise<void> {
  if (!canSend.value) return

  isSending.value = true
  chatStore.sendMessage(inputContent.value)
  inputContent.value = ''

  // 重置 textarea 高度
  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
  }

  // 发送后延迟重置 loading 状态
  setTimeout(() => {
    isSending.value = false
  }, 300)
}

// 自动调整 textarea 高度
function autoResize(event: Event): void {
  const target = event.target as HTMLTextAreaElement
  target.style.height = 'auto'
  target.style.height = `${Math.min(target.scrollHeight, 200)}px`
}

// 设置输入框内容（供父组件调用）
function setContent(content: string) {
  inputContent.value = content
  // 自动调整高度
  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
    textareaRef.value.style.height = `${Math.min(Math.max(textareaRef.value.scrollHeight, 48), 200)}px`
    // 聚焦输入框
    textareaRef.value.focus()
  }
}

// 暴露方法给父组件
defineExpose({
  setContent
})
</script>

<style scoped>
.message-input-container {
  padding: 16px 24px 24px;
  background: transparent;
}

.input-main-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  margin-bottom: 12px;
}

.message-textarea {
  flex: 1;
  padding: 14px 18px;
  background: #ffffff;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 20px;
  color: var(--color-text-primary);
  font-size: 15px;
  font-family: inherit;
  line-height: 1.5;
  resize: none;
  min-height: 48px;
  max-height: 200px;
  overflow-y: auto;
  transition: height 0.15s ease-out, box-shadow 0.15s ease, border-color 0.15s ease;
  box-shadow:
    0 1px 3px 0 rgba(0, 0, 0, 0.06),
    0 4px 12px 0 rgba(0, 0, 0, 0.05);
}

[data-theme="dark"] .message-textarea {
  background: #2d2d2d;
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow:
    0 1px 3px 0 rgba(0, 0, 0, 0.3),
    0 4px 12px 0 rgba(0, 0, 0, 0.2);
}

.message-textarea:focus {
  outline: none;
  border-color: #4f46e5;
  box-shadow:
    0 0 0 3px rgba(79, 70, 229, 0.1),
    0 1px 3px 0 rgba(0, 0, 0, 0.06),
    0 4px 12px 0 rgba(79, 70, 229, 0.08);
}

[data-theme="dark"] .message-textarea:focus {
  box-shadow:
    0 0 0 3px rgba(79, 70, 229, 0.15),
    0 1px 3px 0 rgba(0, 0, 0, 0.3),
    0 4px 12px 0 rgba(79, 70, 229, 0.1);
}

.message-textarea::placeholder {
  color: #94a3b8;
}

[data-theme="dark"] .message-textarea::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.send-button {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  padding: 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.input-extras-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 4px;
}

.extra-item {
  display: flex;
  align-items: center;
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

.input-hint {
  margin-left: auto;
  font-size: 13px;
  color: #94a3b8;
}

[data-theme="dark"] .input-hint {
  color: rgba(255, 255, 255, 0.4);
}
</style>
