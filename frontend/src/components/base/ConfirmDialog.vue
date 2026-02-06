<template>
  <BaseModal
    :visible="visible"
    :title="title"
    :show-close="true"
    width="sm"
    @update:visible="handleClose"
  >
    <div class="confirm-dialog-content">
      <!-- 图标 -->
      <div v-if="type === 'danger'" class="icon danger">
        <AlertTriangle :size="24" />
      </div>
      <div v-else class="icon info">
        <Info :size="24" />
      </div>

      <!-- 消息内容 -->
      <p class="message">{{ message }}</p>

      <!-- 额外提示 -->
      <p v-if="hint" class="hint">{{ hint }}</p>
    </div>

    <template #footer>
      <div class="dialog-actions">
        <BaseButton variant="ghost" @click="handleCancel">
          {{ cancelText }}
        </BaseButton>
        <BaseButton
          :variant="type === 'danger' ? 'danger' : 'primary'"
          @click="handleConfirm"
        >
          {{ confirmText }}
        </BaseButton>
      </div>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { AlertTriangle, Info } from 'lucide-vue-next'
import BaseModal from './BaseModal.vue'
import BaseButton from './BaseButton.vue'

interface Props {
  visible: boolean
  title?: string
  message: string
  hint?: string
  type?: 'danger' | 'info'
  confirmText?: string
  cancelText?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '确认操作',
  type: 'danger',
  confirmText: '确认',
  cancelText: '取消'
})

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'confirm': []
  'cancel': []
}>()

function handleClose() {
  emit('update:visible', false)
  emit('cancel')
}

function handleConfirm() {
  emit('update:visible', false)
  emit('confirm')
}

function handleCancel() {
  emit('update:visible', false)
  emit('cancel')
}
</script>

<style scoped>
.confirm-dialog-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 8px 0;
  text-align: center;
}

.icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
}

.icon.danger {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.icon.info {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.message {
  margin: 0;
  font-size: 15px;
  color: var(--color-text-primary);
  line-height: 1.5;
}

.hint {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-muted);
}

.dialog-actions {
  display: flex;
  gap: 12px;
  width: 100%;
  justify-content: flex-end;
}
</style>
