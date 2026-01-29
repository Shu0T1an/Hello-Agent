<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { X } from 'lucide-vue-next'
import { cn } from '@/lib/utils'

interface Props {
  visible: boolean
  title?: string
  width?: 'sm' | 'md' | 'lg' | 'xl' | 'full'
  closeOnOverlayClick?: boolean
  showClose?: boolean
  closeOnEsc?: boolean
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  width: 'md',
  closeOnOverlayClick: true,
  showClose: true,
  closeOnEsc: true,
})

const emit = defineEmits<{
  'update:visible': [value: boolean]
  close: []
  confirm: []
}>()

const modalRef = ref<HTMLElement | null>(null)
const previousActiveElement = ref<HTMLElement | null>(null)

const widthClasses = computed(() => {
  const widths = {
    sm: 'max-w-md',
    md: 'max-w-lg',
    lg: 'max-w-2xl',
    xl: 'max-w-4xl',
    full: 'max-w-full mx-4',
  }
  return widths[props.width]
})

const close = () => {
  emit('update:visible', false)
  emit('close')
}

const handleOverlayClick = (e: MouseEvent) => {
  if (props.closeOnOverlayClick && e.target === e.currentTarget) {
    close()
  }
}

const handleEscKey = (e: KeyboardEvent) => {
  if (props.closeOnEsc && e.key === 'Escape' && props.visible) {
    close()
  }
}

const focusTrap = (e: KeyboardEvent) => {
  if (!modalRef.value || e.key !== 'Tab') return

  const focusableElements = modalRef.value.querySelectorAll(
    'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
  )
  const firstElement = focusableElements[0] as HTMLElement
  const lastElement = focusableElements[focusableElements.length - 1] as HTMLElement

  if (e.shiftKey) {
    if (document.activeElement === firstElement) {
      lastElement?.focus()
      e.preventDefault()
    }
  } else {
    if (document.activeElement === lastElement) {
      firstElement?.focus()
      e.preventDefault()
    }
  }
}

const savePreviousActiveElement = () => {
  previousActiveElement.value = document.activeElement as HTMLElement
}

const restorePreviousActiveElement = () => {
  if (previousActiveElement.value) {
    previousActiveElement.value.focus()
  }
}

const focusModal = async () => {
  await nextTick()
  if (modalRef.value) {
    const focusable = modalRef.value.querySelector(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    ) as HTMLElement
    focusable?.focus()
  }
}

watch(() => props.visible, async (newValue) => {
  if (newValue) {
    savePreviousActiveElement()
    document.body.style.overflow = 'hidden'
    await nextTick()
    focusModal()
  } else {
    document.body.style.overflow = ''
    restorePreviousActiveElement()
  }
})

onMounted(() => {
  document.addEventListener('keydown', handleEscKey)
  if (props.visible) {
    savePreviousActiveElement()
    document.body.style.overflow = 'hidden'
    focusModal()
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleEscKey)
  document.body.style.overflow = ''
  restorePreviousActiveElement()
})
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-all duration-200"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition-all duration-200"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="visible"
        class="fixed inset-0 z-[1050] flex items-center justify-center p-4"
        @click="handleOverlayClick"
        @keydown.tab="focusTrap"
      >
        <!-- 遮罩 -->
        <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm" />

        <!-- 模态框内容 -->
        <Transition
          enter-active-class="transition-all duration-200"
          enter-from-class="opacity-0 scale-95"
          enter-to-class="opacity-100 scale-100"
          leave-active-class="transition-all duration-200"
          leave-from-class="opacity-100 scale-100"
          leave-to-class="opacity-0 scale-95"
        >
          <div
            v-if="visible"
            ref="modalRef"
            :class="cn(
              'relative bg-white rounded-2xl shadow-xl w-full',
              widthClasses,
              props.class
            )"
            role="dialog"
            aria-modal="true"
            :aria-labelledby="title ? 'modal-title' : undefined"
          >
            <!-- Header -->
            <div v-if="$slots.header || title || showClose" class="flex items-center justify-between px-6 py-4 border-b border-slate-200">
              <div v-if="$slots.header">
                <slot name="header" />
              </div>
              <h2
                v-else-if="title"
                id="modal-title"
                class="text-lg font-semibold text-slate-900"
              >
                {{ title }}
              </h2>
              <div v-else />
              <button
                v-if="showClose"
                type="button"
                class="ml-4 p-1 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
                :aria-label="'关闭对话框'"
                @click="close"
              >
                <X :size="20" />
              </button>
            </div>

            <!-- Body -->
            <div class="px-6 py-4 max-h-[70vh] overflow-y-auto custom-scrollbar">
              <slot />
            </div>

            <!-- Footer -->
            <div v-if="$slots.footer" class="flex items-center justify-end gap-3 px-6 py-4 border-t border-slate-200">
              <slot name="footer" />
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>
