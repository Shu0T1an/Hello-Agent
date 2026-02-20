<script setup lang="ts">
import { computed } from 'vue'
import type { ClassValue } from 'clsx'
import { cn } from '@/lib/utils'

interface Props {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'gradient' | 'glass' | 'editorial'
  size?: 'sm' | 'md' | 'lg'
  disabled?: boolean
  loading?: boolean
  type?: 'button' | 'submit' | 'reset'
  class?: ClassValue
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'primary',
  size: 'md',
  disabled: false,
  loading: false,
  type: 'button',
})

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()

const baseClasses = 'inline-flex items-center justify-center gap-2 font-medium rounded-xl border transition-all duration-200 active:translate-y-[1px] focus:outline-none focus:ring-2 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed disabled:active:translate-y-0'

const variantClasses = computed(() => {
  const variants = {
    primary: 'bg-[var(--color-primary)] text-white border-transparent hover:bg-[var(--color-primary-hover)] focus:ring-[var(--color-primary)] shadow-sm',
    secondary: 'bg-[var(--surface-1)] text-[var(--color-text-primary)] border-[var(--line-subtle)] hover:bg-[var(--surface-2)] hover:border-[var(--line-strong)] focus:ring-[var(--line-strong)] shadow-sm',
    ghost: 'bg-transparent text-[var(--color-text-secondary)] border-transparent hover:bg-[var(--surface-2)] hover:text-[var(--color-text-primary)] focus:ring-[var(--line-strong)]',
    danger: 'bg-red-600 text-white border-transparent hover:bg-red-700 focus:ring-red-500 shadow-sm',
    gradient: 'btn-gradient-primary focus:ring-[var(--color-primary)]',
    glass: 'btn-glass focus:ring-[var(--color-primary)]',
    editorial: 'bg-[var(--surface-1)] text-[var(--color-text-primary)] border-[var(--line-subtle)] hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] focus:ring-[var(--color-primary)] shadow-xs',
  }
  return variants[props.variant]
})

const sizeClasses = computed(() => {
  const sizes = {
    sm: 'px-3 py-1.5 text-sm',
    md: 'px-4 py-2 text-sm',
    lg: 'px-6 py-3 text-base',
  }
  return sizes[props.size]
})

const handleClick = (e: MouseEvent) => {
  if (!props.disabled && !props.loading) {
    emit('click', e)
  }
}
</script>

<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :class="cn(baseClasses, variantClasses, sizeClasses, props.class)"
    @click="handleClick"
  >
    <slot v-if="!loading" />
    <svg
      v-else
      class="animate-spin h-4 w-4"
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
    >
      <circle
        class="opacity-25"
        cx="12"
        cy="12"
        r="10"
        stroke="currentColor"
        stroke-width="4"
      />
      <path
        class="opacity-75"
        fill="currentColor"
        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
      />
    </svg>
  </button>
</template>
