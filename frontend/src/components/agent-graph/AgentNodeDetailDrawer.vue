<template>
  <aside class="w-[300px] border-l border-zinc-200 bg-white p-4 overflow-y-auto">
    <div v-if="node">
      <h3 class="text-sm font-semibold text-zinc-900 mb-2">{{ node.label }}</h3>
      <p class="text-xs text-zinc-500 mb-1"><span class="font-medium text-zinc-700">ID:</span> {{ node.id }}</p>
      <p class="text-xs text-zinc-500 mb-1"><span class="font-medium text-zinc-700">类型:</span> {{ node.nodeType }}</p>
      <p v-if="node.className" class="text-xs text-zinc-500 mb-3 break-all">
        <span class="font-medium text-zinc-700">实现类:</span> {{ node.className }}
      </p>

      <div v-if="metadataEntries.length > 0" class="space-y-2">
        <h4 class="text-xs font-semibold text-zinc-700 uppercase tracking-wide">元数据</h4>
        <div v-for="item in metadataEntries" :key="item.key" class="rounded border border-zinc-200 bg-zinc-50 p-2">
          <div class="text-[11px] font-medium text-zinc-700">{{ item.key }}</div>
          <pre class="text-[11px] text-zinc-600 whitespace-pre-wrap break-all">{{ item.value }}</pre>
        </div>
      </div>
    </div>
    <div v-else class="text-xs text-zinc-500">
      点击节点查看详情。
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AgentGraphNode } from '@/types/agent-graph'

const props = defineProps<{
  node: AgentGraphNode | null
}>()

const metadataEntries = computed(() => {
  const metadata = props.node?.metadata
  if (!metadata) {
    return []
  }
  return Object.entries(metadata).map(([key, value]) => ({
    key,
    value: typeof value === 'string' ? value : JSON.stringify(value, null, 2)
  }))
})
</script>
