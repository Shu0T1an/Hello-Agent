<script setup lang="ts">
import { computed, useSlots } from 'vue'
import { cn } from '@/lib/utils'

interface Props {
  modelValue: string
  type?: string
  placeholder?: string
  disabled?: boolean
  error?: string
  size?: 'sm' | 'md' | 'lg'
}

const props = withDefaults(defineProps<Props>(), {
  type: 'text',
  disabled: false,
  error: '',
  size: 'md',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  focus: [event: FocusEvent]
  blur: [event: FocusEvent]
  keydown: [event: KeyboardEvent]
}>()

const slots = useSlots()

const hasPrefix = computed(() => !!slots.prefix)
const hasSuffix = computed(() => !!slots.suffix || !!props.error)

const baseClasses = 'w-full rounded-xl border transition-all duration-200 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-offset-0 disabled:bg-slate-100 disabled:cursor-not-allowed'

const sizeClasses = computed(() => {
  const hasAddon = hasPrefix.value || hasSuffix.value
  const sizes = {
    sm: hasAddon ? 'pl-10 pr-3 py-1.5' : 'px-3 py-1.5',
    md: hasAddon ? 'pl-11 pr-4 py-2' : 'px-4 py-2',
    lg: hasAddon ? 'pl-12 pr-4 py-3' : 'px-4 py-3',
  }
  return sizes[props.size]
})

const stateClasses = computed(() => {
  const errorPadding = props.error ? 'pr-10' : ''
  if (props.error) {
    return `border-error-300 focus:border-error-500 focus:ring-error-500 ${errorPadding}`
  }
  return 'border-slate-200 focus:border-indigo-500 focus:ring-indigo-500'
})

const handleInput = (e: Event) => {
  emit('update:modelValue', (e.target as HTMLInputElement).value)
}
</script>

<template>
  <div class="relative">
    <!-- Prefix Slot -->
    <div
      v-if="slots.prefix"
      class="absolute inset-y-0 left-0 flex items-center pl-3.5 pointer-events-none text-slate-400"
    >
      <slot name="prefix" />
    </div>

    <!-- Input -->
    <input
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :class="cn(
        baseClasses,
        sizeClasses,
        stateClasses,
        'text-sm',
        $attrs.class
      )"
      @input="handleInput"
      @focus="emit('focus', $event)"
      @blur="emit('blur', $event)"
      @keydown="emit('keydown', $event)"
    />

    <!-- Suffix Slot (Error Icon) -->
    <div
      v-if="error || slots.suffix"
      class="absolute inset-y-0 right-0 flex items-center pr-3 pointer-events-none"
    >
      <slot v-if="slots.suffix" name="suffix" />
      <svg
        v-else-if="error"
        class="h-5 w-5 text-error-500"
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 20 20"
        fill="currentColor"
      >
        <path
          fill-rule="evenodd"
          d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z"
          clip-rule="evenodd"
        />
      </svg>
    </div>

    <!-- Error Message -->
    <p
      v-if="error"
      class="mt-1 text-sm text-error-600"
    >
      {{ error }}
    </p>
  </div>
</template>
