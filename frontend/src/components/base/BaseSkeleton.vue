<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

interface Props {
  type?: 'text' | 'avatar' | 'card' | 'table' | 'custom'
  rows?: number
  width?: string
  height?: string
  avatarSize?: number
  class?: string
}

const props = withDefaults(defineProps<Props>(), {
  type: 'text',
  rows: 3,
  width: '100%',
  height: 'auto',
  avatarSize: 40,
})

const containerClasses = computed(() => {
  const types = {
    text: 'flex flex-col gap-2',
    avatar: 'flex items-center gap-3',
    card: 'flex flex-col gap-4 p-4',
    table: 'flex flex-col gap-2',
    custom: '',
  }
  return types[props.type]
})

const skeletonClasses = computed(() => {
  return 'shimmer rounded-lg bg-slate-100'
})

const textSkeletonWidths = computed(() => {
  const widths = ['100%', '85%', '95%', '70%', '90%', '80%']
  return Array.from({ length: props.rows }, (_, i) => widths[i % widths.length])
})
</script>

<template>
  <div :class="cn(containerClasses, props.class)">
    <!-- Text 类型 -->
    <template v-if="type === 'text'">
      <div
        v-for="(_, index) in rows"
        :key="index"
        :class="cn(skeletonClasses, 'h-4')"
        :style="{ width: textSkeletonWidths[index] }"
      />
    </template>

    <!-- Avatar 类型 -->
    <template v-else-if="type === 'avatar'">
      <div
        :class="cn(skeletonClasses, 'rounded-full flex-shrink-0')"
        :style="{ width: `${avatarSize}px`, height: `${avatarSize}px` }"
      />
      <div :class="cn(skeletonClasses, 'h-4 flex-1')" style="width: 60%" />
    </template>

    <!-- Card 类型 -->
    <template v-else-if="type === 'card'">
      <div :class="cn(skeletonClasses, 'h-40 w-full rounded-xl')" />
      <div :class="cn(skeletonClasses, 'h-5 w-3/4')" />
      <div :class="cn(skeletonClasses, 'h-4 w-full')" />
      <div :class="cn(skeletonClasses, 'h-4 w-5/6')" />
    </template>

    <!-- Table 类型 -->
    <template v-else-if="type === 'table'">
      <!-- Header -->
      <div class="flex gap-2">
        <div :class="cn(skeletonClasses, 'h-8 flex-1')" />
        <div :class="cn(skeletonClasses, 'h-8 flex-1')" />
        <div :class="cn(skeletonClasses, 'h-8 flex-1')" />
      </div>
      <!-- Rows -->
      <div
        v-for="(_, index) in rows"
        :key="index"
        class="flex gap-2"
      >
        <div :class="cn(skeletonClasses, 'h-12 flex-1')" />
        <div :class="cn(skeletonClasses, 'h-12 flex-1')" />
        <div :class="cn(skeletonClasses, 'h-12 flex-1')" />
      </div>
    </template>

    <!-- Custom 类型 -->
    <template v-else-if="type === 'custom'">
      <div
        :class="cn(skeletonClasses)"
        :style="{ width, height }"
      />
      <slot />
    </template>
  </div>
</template>
