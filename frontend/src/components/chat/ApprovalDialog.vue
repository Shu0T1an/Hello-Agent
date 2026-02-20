<template>
  <BaseModal
    :visible="isOpen"
    :title="title"
    @update:visible="handleClose"
  >
    <div class="approval-dialog">
      <!-- 审批消息 -->
      <div class="approval-message">
        <AlertCircle class="icon-warning" :size="24" />
        <p>{{ message }}</p>
      </div>

      <!-- 工具调用列表 -->
      <div class="tool-list">
        <div
          v-for="tool in toolCalls"
          :key="tool.id"
          class="tool-item"
          :class="`tool-status-${getToolStatus(tool)}`"
        >
          <div class="tool-header">
            <Wrench class="tool-icon" :size="16" />
            <span class="tool-name">{{ tool.name }}</span>
            <span class="tool-description">{{ tool.description || '无描述' }}</span>
          </div>

          <!-- 工具参数 -->
          <div class="tool-arguments">
            <code>{{ formatArguments(tool.arguments) }}</code>
          </div>

          <!-- 审批按钮 -->
          <div class="tool-actions">
            <BaseButton
              variant="primary"
              size="sm"
              :disabled="isSubmitting"
              @click="handleApprove(tool)"
            >
              <Check class="btn-icon" :size="14" />
              批准
            </BaseButton>
            <BaseButton
              variant="danger"
              size="sm"
              :disabled="isSubmitting"
              @click="handleReject(tool)"
            >
              <X class="btn-icon" :size="14" />
              拒绝
            </BaseButton>
          </div>
        </div>
      </div>

      <!-- 提交按钮 -->
      <div class="dialog-footer">
        <BaseButton
          variant="ghost"
          size="sm"
          @click="handleClose"
          :disabled="isSubmitting"
        >
          取消
        </BaseButton>
      </div>
    </div>
  </BaseModal>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { AlertCircle, Check, X, Wrench } from 'lucide-vue-next'
import BaseModal from '@/components/base/BaseModal.vue'
import BaseButton from '@/components/base/BaseButton.vue'

interface ToolCall {
  id: string
  name: string
  arguments: Record<string, unknown>
  description?: string
}

interface ToolFeedback {
  id: string
  name: string
  result: 'PENDING' | 'APPROVED' | 'REJECTED'
}

interface Props {
  isOpen: boolean
  agentName: string
  checkpointId: string
  sessionId?: string
  message: string
  toolCalls: ToolCall[]
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'close': []
  'submit': [feedbacks: ToolFeedback[]]
}>()

const isSubmitting = ref(false)
const toolStatuses = ref<Record<string, 'PENDING' | 'APPROVED' | 'REJECTED'>>({})

const title = computed(() => '需要人工审批')

// 获取工具状态
function getToolStatus(tool: ToolCall): 'PENDING' | 'APPROVED' | 'REJECTED' {
  return toolStatuses.value[tool.id] || 'PENDING'
}

// 格式化参数显示
function formatArguments(args: Record<string, unknown>): string {
  try {
    return JSON.stringify(args, null, 2)
  } catch {
    return String(args)
  }
}

// 批准工具调用
async function handleApprove(tool: ToolCall) {
  toolStatuses.value[tool.id] = 'APPROVED'
  await submitFeedback()
}

// 拒绝工具调用
async function handleReject(tool: ToolCall) {
  toolStatuses.value[tool.id] = 'REJECTED'
  await submitFeedback()
}

// 提交所有反馈
async function submitFeedback() {
  // 验证 checkpointId
  if (!props.checkpointId || props.checkpointId.trim() === '') {
    console.error('checkpointId 为空，无法提交审批')
    alert('检查点 ID 无效，请刷新页面重试')
    return
  }

  isSubmitting.value = true

  try {
    const feedbacks: ToolFeedback[] = props.toolCalls.map(tool => ({
      id: tool.id,
      name: tool.name,
      result: toolStatuses.value[tool.id] || 'PENDING'
    }))

    // 只 emit 数据，由父组件的 chatStore.resumeAgent 处理 SSE 流
    emit('submit', feedbacks)
    emit('close')
  } catch (error) {
    console.error('提交审批失败:', error)
    alert('提交审批失败，请重试')
    // 重置状态
    Object.keys(toolStatuses.value).forEach(id => {
      toolStatuses.value[id] = 'PENDING'
    })
  } finally {
    isSubmitting.value = false
  }
}

function handleClose() {
  emit('close')
}
</script>

<style scoped>
.approval-dialog {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.approval-message {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: rgba(251, 191, 36, 0.1);
  border: 1px solid rgba(251, 191, 36, 0.3);
  border-radius: 8px;
  color: var(--color-text-primary);
}

.icon-warning {
  color: #fbbf24;
  flex-shrink: 0;
}

.approval-message p {
  margin: 0;
  font-size: 14px;
}

.tool-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tool-item {
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-secondary);
  transition: all 0.2s;
}

.tool-item:hover {
  border-color: var(--color-border-hover);
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.tool-icon {
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.tool-name {
  font-weight: 600;
  color: var(--color-text-primary);
}

.tool-description {
  font-size: 12px;
  color: var(--color-text-muted);
}

.tool-arguments {
  margin: 8px 0;
  padding: 8px;
  background: var(--color-bg-primary);
  border-radius: 4px;
}

.tool-arguments code {
  display: block;
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
}

.tool-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.tool-actions .btn-icon {
  margin-right: 4px;
}

/* 状态样式 */
.tool-status-PENDING {
  border-left: 3px solid #fbbf24;
}

.tool-status-APPROVED {
  border-left: 3px solid #22c55e;
  opacity: 0.6;
}

.tool-status-REJECTED {
  border-left: 3px solid #ef4444;
  opacity: 0.6;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}
</style>
