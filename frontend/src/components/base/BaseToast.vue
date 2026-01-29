<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  CheckCircle,
  AlertTriangle,
  XCircle,
  Info,
  X,
} from 'lucide-vue-next'
import { cn } from '@/lib/utils'

export interface ToastProps {
  id: string
  type: 'success' | 'warning' | 'error' | 'info'
  message: string
  duration: number
  closable: boolean
  showIcon: boolean
  onClose?: () => void
  onClick?: () => void
}

const props = withDefaults(defineProps<ToastProps>(), {
  duration: 3000,
  closable: true,
  showIcon: true,
})

const emit = defineEmits<{
  close: []
  click: []
}>()

const visible = ref(true)
const progressBar = ref(100)

const typeClasses = computed(() => {
  const types = {
    success: 'bg-success-50 border-success-200 text-success-800',
    warning: 'bg-warning-50 border-warning-200 text-warning-800',
    error: 'bg-error-50 border-error-200 text-error-800',
    info: 'bg-info-50 border-info-200 text-info-800',
  }
  return types[props.type]
})

const iconComponent = computed(() => {
  const icons = {
    success: CheckCircle,
    warning: AlertTriangle,
    error: XCircle,
    info: Info,
  }
  return icons[props.type]
})

const iconColor = computed(() => {
  const colors = {
    success: 'text-success-500',
    warning: 'text-warning-500',
    error: 'text-error-500',
    info: 'text-info-500',
  }
  return colors[props.type]
})

const close = () => {
  visible.value = false
  setTimeout(() => {
    emit('close')
    props.onClose?.()
  }, 200)
}

const handleClick = () => {
  emit('click')
  props.onClick?.()
}

let timer: ReturnType<typeof setInterval> | null = null
let progressTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  if (props.duration > 0) {
    timer = setTimeout(close, props.duration)

    const interval = 50
    const steps = props.duration / interval
    const decrement = 100 / steps
    progressTimer = setInterval(() => {
      progressBar.value -= decrement
      if (progressBar.value <= 0) {
        progressTimer && clearInterval(progressTimer)
      }
    }, interval)
  }
})

defineExpose({
  close,
})
</script>

<template>
  <Transition
    enter-active-class="transition-all duration-200"
    enter-from-class="opacity-0 translate-x-full"
    enter-to-class="opacity-100 translate-x-0"
    leave-active-class="transition-all duration-200"
    leave-from-class="opacity-100 translate-x-0"
    leave-to-class="opacity-0 translate-x-full"
  >
    <div
      v-if="visible"
      :class="cn(
        'relative flex items-start gap-3 p-4 rounded-xl border shadow-lg min-w-[320px] max-w-md cursor-pointer',
        typeClasses
      )"
      role="alert"
      @click="handleClick"
    >
      <!-- 图标 -->
      <component
        :is="iconComponent"
        v-if="showIcon"
        :class="cn('flex-shrink-0 mt-0.5', iconColor)"
        :size="20"
      />

      <!-- 内容 -->
      <div class="flex-1 min-w-0">
        <p class="text-sm font-medium break-words">
          {{ message }}
        </p>
      </div>

      <!-- 关闭按钮 -->
      <button
        v-if="closable"
        type="button"
        :class="cn('flex-shrink-0 opacity-70 hover:opacity-100 transition-opacity', iconColor)"
        :aria-label="'关闭通知'"
        @click.stop="close"
      >
        <X :size="18" />
      </button>

      <!-- 进度条 -->
      <div
        v-if="duration > 0"
        :class="cn('absolute bottom-0 left-0 h-1 rounded-b-xl transition-all duration-50 ease-linear', {
          'bg-success-500': type === 'success',
          'bg-warning-500': type === 'warning',
          'bg-error-500': type === 'error',
          'bg-info-500': type === 'info',
        })"
        :style="{ width: `${progressBar}%` }"
      />
    </div>
  </Transition>
</template>
