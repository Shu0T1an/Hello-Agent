<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

interface Props {
  variant?: 'default' | 'bordered' | 'elevated' | 'flat'
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

const baseClasses = 'rounded-xl bg-white transition-all duration-200'

const variantClasses = computed(() => {
  const variants = {
    default: 'border border-slate-200 shadow-soft',
    bordered: 'border-2 border-slate-200',
    elevated: 'shadow-medium border-0',
    flat: 'border-0 shadow-none bg-slate-50',
  }
  return variants[props.variant]
})

const hoverClasses = computed(() => {
  if (!props.hoverable && !props.clickable) return ''
  return 'hover:shadow-hover hover:-translate-y-0.5 cursor-pointer'
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
