<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

interface Props {
  modelValue: string
  placeholder?: string
  disabled?: boolean
  error?: string
  rows?: number
  resize?: 'none' | 'vertical' | 'horizontal' | 'both'
  size?: 'sm' | 'md' | 'lg'
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  error: '',
  rows: 3,
  resize: 'vertical',
  size: 'md',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  focus: [event: FocusEvent]
  blur: [event: FocusEvent]
  keydown: [event: KeyboardEvent]
}>()

const baseClasses = 'w-full rounded-xl border transition-all duration-200 placeholder:text-zinc-400 focus:outline-none focus:ring-2 focus:ring-offset-0 disabled:bg-zinc-100 disabled:cursor-not-allowed'

const sizeClasses = computed(() => {
  const sizes = {
    sm: 'px-3 py-2 text-sm',
    md: 'px-4 py-2 text-sm',
    lg: 'px-4 py-3 text-base',
  }
  return sizes[props.size]
})

const resizeClasses = computed(() => {
  const resizes = {
    none: 'resize-none',
    vertical: 'resize-y',
    horizontal: 'resize-x',
    both: 'resize',
  }
  return resizes[props.resize]
})

const stateClasses = computed(() => {
  if (props.error) {
    return 'border-red-300 focus:border-red-500 focus:ring-red-500'
  }
  return 'border-zinc-200 focus:border-indigo-500 focus:ring-indigo-500'
})

const handleInput = (e: Event) => {
  emit('update:modelValue', (e.target as HTMLTextAreaElement).value)
}
</script>

<template>
  <div class="relative">
    <textarea
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :rows="rows"
      :class="cn(baseClasses, sizeClasses, resizeClasses, stateClasses, $attrs.class)"
      @input="handleInput"
      @focus="emit('focus', $event)"
      @blur="emit('blur', $event)"
      @keydown="emit('keydown', $event)"
    />
    <p
      v-if="error"
      class="mt-1 text-sm text-red-600"
    >
      {{ error }}
    </p>
  </div>
</template>
