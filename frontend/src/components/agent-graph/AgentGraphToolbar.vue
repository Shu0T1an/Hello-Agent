<template>
  <div class="flex items-center justify-between gap-3 border-b border-zinc-200 px-4 py-3 bg-zinc-50">
    <div class="text-xs text-zinc-600 flex items-center gap-2">
      <span class="font-medium text-zinc-700">布局:</span>
      <button
        class="px-2 py-1 rounded border text-xs"
        :class="layoutMode === 'LR' ? 'bg-zinc-900 text-white border-zinc-900' : 'bg-white text-zinc-700 border-zinc-300'"
        @click="$emit('layoutChange', 'LR')"
      >
        左右
      </button>
      <button
        class="px-2 py-1 rounded border text-xs"
        :class="layoutMode === 'TB' ? 'bg-zinc-900 text-white border-zinc-900' : 'bg-white text-zinc-700 border-zinc-300'"
        @click="$emit('layoutChange', 'TB')"
      >
        上下
      </button>
    </div>

    <button
      class="px-2 py-1 rounded border text-xs bg-white text-zinc-700 border-zinc-300 hover:bg-zinc-100"
      @click="$emit('fitView')"
    >
      适配视图
    </button>
  </div>
</template>

<script setup lang="ts">
import type { GraphLayoutMode } from '@/types/agent-graph'

defineProps<{
  layoutMode: GraphLayoutMode
}>()

defineEmits<{
  layoutChange: [mode: GraphLayoutMode]
  fitView: []
}>()
</script>
