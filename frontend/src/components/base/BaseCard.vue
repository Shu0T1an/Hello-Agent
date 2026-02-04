<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

interface Props {
  variant?: 'default' | 'bordered' | 'elevated' | 'flat' | 'glass'
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

const baseClasses = 'rounded-xl transition-all duration-500'

const variantClasses = computed(() => {
  const variants = {
    default: 'bg-white border border-zinc-200 shadow-soft',
    bordered: 'bg-white border-2 border-zinc-200',
    elevated: 'bg-white shadow-medium border-0',
    flat: 'bg-zinc-50 border-0 shadow-none',
    glass: 'glass-card',
  }
  return variants[props.variant]
})

const hoverClasses = computed(() => {
  if (!props.hoverable && !props.clickable) return ''
  if (props.variant === 'glass') {
    return 'hover:shadow-glass-deep hover:scale-[1.02] cursor-pointer'
  }
  return 'hover:shadow-hover hover:scale-[1.02] cursor-pointer'
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
    :aria-label="$attrs['aria-label']"
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
