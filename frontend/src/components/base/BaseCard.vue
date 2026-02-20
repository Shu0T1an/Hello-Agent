<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

interface Props {
  variant?: 'default' | 'bordered' | 'elevated' | 'flat' | 'glass' | 'editorial'
  hoverable?: boolean
  padding?: 'none' | 'sm' | 'md' | 'lg' | 'xl'
  clickable?: boolean
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default',
  hoverable: false,
  padding: 'md',
  clickable: false,
})

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()

const baseClasses = 'rounded-xl transition-all duration-200'

const variantClasses = computed(() => {
  const variants = {
    default: 'bg-[var(--surface-1)] border border-[var(--line-subtle)] shadow-sm',
    bordered: 'bg-[var(--surface-1)] border-2 border-[var(--line-strong)]',
    elevated: 'bg-[var(--surface-1)] shadow-md border border-[var(--line-subtle)]',
    flat: 'bg-[var(--surface-2)] border-0 shadow-none',
    glass: 'glass-card',
    editorial: 'bg-[var(--surface-1)] border border-[var(--line-subtle)] shadow-sm',
  }
  return variants[props.variant]
})

const hoverClasses = computed(() => {
  if (!props.hoverable && !props.clickable) return ''
  if (props.variant === 'glass') {
    return 'hover:-translate-y-[1px] hover:shadow-lg cursor-pointer'
  }
  return 'hover:-translate-y-[1px] hover:shadow-md cursor-pointer'
})

const paddingClasses = computed(() => {
  const paddings = {
    none: '',
    sm: 'p-4',
    md: 'p-6',
    lg: 'p-8',
    xl: 'p-10',
  }
  return paddings[props.padding]
})

const handleClick = (e: MouseEvent) => {
  if (props.clickable) {
    emit('click', e)
  }
}
</script>

<template>
  <div
    :class="cn(
      baseClasses,
      variantClasses,
      hoverClasses,
      paddingClasses,
      props.class
    )"
    :role="clickable ? 'button' : 'region'"
    :tabindex="clickable ? 0 : undefined"
    :aria-label="typeof $attrs['aria-label'] === 'string' ? $attrs['aria-label'] : undefined"
    @click="handleClick"
    @keydown.enter="clickable ? handleClick($event as any) : null"
  >
    <div v-if="$slots.header" class="mb-4">
      <slot name="header" />
    </div>
    <slot />
    <div v-if="$slots.footer" class="mt-4">
      <slot name="footer" />
    </div>
  </div>
</template>
