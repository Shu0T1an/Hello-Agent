<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'
import { X } from 'lucide-vue-next'

interface Props {
  variant?: 'default' | 'success' | 'warning' | 'danger' | 'info' | 'purple'
  size?: 'sm' | 'md'
  closable?: boolean
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default',
  size: 'md',
  closable: false,
  disabled: false,
})

const emit = defineEmits<{
  close: []
}>()

const baseClasses = 'inline-flex items-center gap-1.5 font-medium rounded-lg transition-all duration-200'

const variantClasses = computed(() => {
  const variants = {
    default: 'bg-zinc-100 text-zinc-700',
    success: 'bg-green-100 text-green-700',
    warning: 'bg-yellow-100 text-yellow-700',
    danger: 'bg-red-100 text-red-700',
    info: 'bg-blue-100 text-blue-700',
    purple: 'bg-indigo-100 text-indigo-700',
  }
  return variants[props.variant]
})

const sizeClasses = computed(() => {
  const sizes = {
    sm: 'px-2 py-0.5 text-xs',
    md: 'px-2.5 py-1 text-sm',
  }
  return sizes[props.size]
})

const handleClose = () => {
  if (!props.disabled) {
    emit('close')
  }
}
</script>

<template>
  <span :class="cn(baseClasses, variantClasses, sizeClasses, $attrs.class)">
    <slot />
    <button
      v-if="closable"
      type="button"
      :disabled="disabled"
      :class="[
        'rounded-md hover:bg-black/10 transition-colors',
        disabled && 'opacity-50 cursor-not-allowed'
      ]"
      @click="handleClose"
    >
      <X :class="size === 'sm' ? 'w-3 h-3' : 'w-3.5 h-3.5'" />
    </button>
  </span>
</template>
